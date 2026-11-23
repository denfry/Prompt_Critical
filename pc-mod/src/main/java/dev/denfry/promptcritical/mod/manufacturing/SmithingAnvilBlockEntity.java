package dev.denfry.promptcritical.mod.manufacturing;

import dev.denfry.promptcritical.mod.foundry.MachineInventory;
import dev.denfry.promptcritical.sim.manufacturing.ForgeEvent;
import dev.denfry.promptcritical.sim.manufacturing.Forging;
import dev.denfry.promptcritical.sim.manufacturing.ForgingSession;
import dev.denfry.promptcritical.sim.manufacturing.PartDefect;
import dev.denfry.promptcritical.sim.manufacturing.PartSpec;
import dev.denfry.promptcritical.sim.manufacturing.Quenching;
import dev.denfry.promptcritical.sim.manufacturing.Workpiece;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Наковальня {@code smithing_anvil} (18 §18.7, руками; решение «решения вместо реакции»): кузнец выбирает операцию и
 * бьёт серию ударов кнопкой щита — точность клика не важна, удар идёт на тике сервера. Навык — в решениях: когда
 * вернуть заготовку в горн по цвету каления, когда остановиться, где закалить. Серия — {@link Forging#series} с
 * ручными константами, закалка — {@link Quenching} в воде рядом, готовая деталь — {@link ForgingSession#toPart}.
 */
public final class SmithingAnvilBlockEntity extends SmithyDevice {
    /** Вал: длина не меньше четырёх сторон сечения (18 §18.5) [моё]. */
    public static final double SHAFT_MIN_SLENDERNESS = 4;
    /** Деталь сдают остывшей: выше 60 °C её не взять голой рукой [моё]. */
    public static final double FINISH_MAX_K = 333.15;
    private static final MachineInventory.Role[] ROLES = {MachineInventory.Role.INPUT};

    private Forging.Op op = Forging.Op.DRAW;
    private int lastEvent = -1;

    public SmithingAnvilBlockEntity(BlockPos pos, BlockState state) {
        super(SmithyBlockEntities.SMITHING_ANVIL.get(), pos, state, ROLES, (slot, s) -> Smithy.forgeable(s));
    }

    @Nullable
    private Workpiece piece() {
        return level == null ? null : Smithy.read(inv.getStackInSlot(0), level.getGameTime());
    }

    private void store(Workpiece w) {
        inv.setStackInSlot(0, Smithy.stack(w, level.getGameTime()));
        setChanged();
    }

    public Forging.Op op() {
        return op;
    }

    /**
     * Серия ударов операцией {@link #op}. Нужен кузнечный молот в инвентаре; {@code null} у игрока — для GameTest и
     * механизации, тогда молот не проверяется.
     */
    public List<ForgeEvent> strike(@Nullable Player player) {
        Workpiece w = piece();
        if (w == null || inv.getStackInSlot(0).getCount() != 1) return List.of();
        ItemStack hammer = player == null ? ItemStack.EMPTY : hammer(player);
        if (player != null && hammer.isEmpty()) {
            player.displayClientMessage(ManufacturingText.msg("anvil.no_hammer"), true);
            return List.of();
        }
        Forging.Result r = Forging.series(w, op, Forging.HAND_BLOW_J, Forging.HAND_BLOWS, Forging.HAND_STRAIN_RATE,
                Forging.HAND_SERIES_S, WorkpieceData.AMBIENT_K);
        store(r.piece());
        lastEvent = r.events().isEmpty() ? -1 : r.events().get(r.events().size() - 1).ordinal();
        if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) hammer.hurtAndBreak(1, sl, sp, item -> { });
        if (level instanceof ServerLevel sl) {
            sl.playSound(null, worldPosition, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.35f, 1.4f);
            if (w.tempK() > 873.15)
                sl.sendParticles(ParticleTypes.LAVA, worldPosition.getX() + 0.5, worldPosition.getY() + 1.05,
                        worldPosition.getZ() + 0.5, 4, 0.15, 0.02, 0.15, 0.01);
        }
        return r.events();
    }

    /** Закалка в воде рядом с наковальней. */
    public List<ForgeEvent> quench(@Nullable Player player) {
        Workpiece w = piece();
        if (w == null) return List.of();
        if (!Smithy.waterNear(level, worldPosition)) {
            if (player != null) player.displayClientMessage(ManufacturingText.msg("anvil.no_water"), true);
            return List.of();
        }
        Forging.Result r = Quenching.quench(w, Quenching.Medium.WATER);
        store(r.piece());
        lastEvent = r.events().get(r.events().size() - 1).ordinal();
        if (level instanceof ServerLevel sl) {
            sl.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 1.0f);
            if (w.tempK() > 473.15)
                sl.sendParticles(ParticleTypes.CLOUD, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1,
                        worldPosition.getZ() + 0.5, 8, 0.2, 0.1, 0.2, 0.02);
        }
        return r.events();
    }

    /** Сдать поковку валом: холодная, круглая или квадратная, длиной от четырёх сторон сечения. */
    public boolean finishShaft(@Nullable Player player) {
        Workpiece w = piece();
        String refuse = w == null ? "anvil.no_piece"
                : w.tempK() > FINISH_MAX_K ? "anvil.too_hot"
                : w.section() == Workpiece.Section.FLAT || w.lengthM() < SHAFT_MIN_SLENDERNESS * w.aM() ? "anvil.not_a_shaft"
                : w.severity(PartDefect.SLAG_INCLUSION) > 0 ? "anvil.slag_left" : null;
        if (refuse != null) {
            if (player != null) player.displayClientMessage(ManufacturingText.msg(refuse), true);
            return false;
        }
        PartSpec spec = ForgingSession.start(w).toPart("shaft");
        ItemStack part = new ItemStack(ManufacturingItems.SHAFT.get());
        part.set(ManufacturingComponents.PART_SPEC.get(), spec);
        inv.setStackInSlot(0, part);
        setChanged();
        return true;
    }

    private static ItemStack hammer(Player player) {
        for (ItemStack s : player.getInventory().items) if (s.is(ManufacturingItems.SMITH_HAMMER.get())) return s;
        ItemStack off = player.getOffhandItem();
        return off.is(ManufacturingItems.SMITH_HAMMER.get()) ? off : ItemStack.EMPTY;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case SmithyPanels.BUTTON_DRAW -> op = Forging.Op.DRAW;
            case SmithyPanels.BUTTON_UPSET -> op = Forging.Op.UPSET;
            case SmithyPanels.BUTTON_BEND -> op = Forging.Op.BEND;
            case SmithyPanels.BUTTON_STRIKE -> strike(player);
            case SmithyPanels.BUTTON_QUENCH -> quench(player);
            case SmithyPanels.BUTTON_SHAFT -> finishShaft(player);
            default -> { }
        }
        setChanged();
    }

    public void setOp(Forging.Op o) {
        op = o;
    }

    @Override
    public void tickDevice() {
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return Smithy.forgeable(stack);
    }

    @Override
    public InteractionResult use(Player player) {
        return InteractionResult.PASS;
    }

    @Override
    public dev.denfry.promptcritical.mod.panel.PanelLayout panelLayout() {
        return SmithyPanels.ANVIL;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[SmithyPanels.A_OP] = op.ordinal();
        out[SmithyPanels.A_EVENT] = lastEvent;
        int f = level != null && Smithy.waterNear(level, worldPosition) ? 1 << SmithyPanels.AF_WATER : 0;
        Workpiece w = piece();
        if (w != null) {
            f |= 1 << SmithyPanels.AF_PIECE;
            Forging.Range range = Forging.range(w.grade().family());
            out[SmithyPanels.A_TEMP_C] = (int) Math.round(w.tempK() - 273.15);
            out[SmithyPanels.A_A_MM10] = (int) Math.round(w.aM() * 10_000);
            out[SmithyPanels.A_B_MM10] = (int) Math.round(w.bM() * 10_000);
            out[SmithyPanels.A_LEN_MM] = (int) Math.round(w.lengthM() * 1000);
            out[SmithyPanels.A_SLAG_PCT] = (int) Math.round(w.severity(PartDefect.SLAG_INCLUSION) * 50);
            out[SmithyPanels.A_CRACK] = PartSpec.defectLevel(w.severity(PartDefect.CRACK));
            out[SmithyPanels.A_LOWER_C] = (int) Math.round(range.lowerK() - 273.15);
            out[SmithyPanels.A_UPPER_C] = (int) Math.round(range.upperK() - 273.15);
            out[SmithyPanels.A_ROUND] = w.section() == Workpiece.Section.ROUND ? 1 : 0;
            if (w.tempK() >= range.lowerK() && w.tempK() <= range.upperK()) f |= 1 << SmithyPanels.AF_IN_RANGE;
            else if (w.tempK() < range.lowerK()) f |= 1 << SmithyPanels.AF_COLD;
        }
        out[SmithyPanels.FLAGS] = f;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("op", op.name());
        tag.putInt("last", lastEvent);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        try {
            op = Forging.Op.valueOf(tag.getString("op"));
        } catch (IllegalArgumentException e) {
            op = Forging.Op.DRAW;
        }
        lastEvent = tag.contains("last") ? tag.getInt("last") : -1;
    }
}
