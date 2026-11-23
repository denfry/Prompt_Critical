package dev.denfry.promptcritical.mod.manufacturing;

import dev.denfry.promptcritical.mod.foundry.MachineInventory;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.manufacturing.Drawing;
import dev.denfry.promptcritical.sim.manufacturing.Wire;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Волочильная доска {@code draw_plate} (18 §18.6.7, ручная ступень): стальная доска с рядом волок на верстаке и ворот.
 * Кузнец выбирает волоку и протягивает пруток клещами: проход — {@link Drawing#pass} с тягой
 * {@link Drawing#HAND_FORCE_N}, со смазкой (пчелиный воск, сота на {@link #LUBE_PASSES} проходов) трение меньше.
 * Наклёп копится от прохода к проходу; оборвавшуюся проволоку отжигают в огне кузнечного горна.
 */
public final class DrawPlateBlockEntity extends SmithyDevice {
    /** Ряд волок, мм: шаг около 20 % площади, как у калибров проволоки [сверить: ГОСТ 3282 ряд диаметров]. */
    public static final double[] DIES_MM = {10, 9, 8, 7, 6, 5.3, 4.7, 4.2, 3.7, 3.3, 2.9, 2.6, 2.3, 2.0, 1.8, 1.6, 1.4,
            1.25, 1.1, 1.0, 0.9, 0.8, 0.7, 0.63, 0.56, 0.5};
    /** Проходов на одну соту воска [BALANCE]. */
    public static final int LUBE_PASSES = 8;
    private static final MachineInventory.Role[] ROLES = {MachineInventory.Role.INPUT, MachineInventory.Role.INPUT};

    private int die = 4;
    private int lubeLeft;
    private int lastOutcome = -1;
    private int lastForceN;

    public DrawPlateBlockEntity(BlockPos pos, BlockState state) {
        super(SmithyBlockEntities.DRAW_PLATE.get(), pos, state, ROLES, (slot, s) -> slot == 0 ? Smithy.drawable(s) : isLube(s));
    }

    public static boolean isLube(ItemStack s) {
        return s.is(Items.HONEYCOMB);
    }

    public double dieM() {
        return DIES_MM[die] / 1000;
    }

    public void setDie(int index) {
        die = Math.max(0, Math.min(DIES_MM.length - 1, index));
        setChanged();
    }

    /** Самая крупная волока меньше диаметра — следующий проход кузнеца. */
    public void nextDieFor(Wire w) {
        for (int i = 0; i < DIES_MM.length; i++)
            if (DIES_MM[i] / 1000 < w.diameterM() - 1e-9) {
                setDie(i);
                return;
            }
    }

    /**
     * Протянуть пруток или проволоку из слота через выбранную волоку. Нужны клещи; {@code null} у игрока — для
     * GameTest. Возвращает исход прохода, {@code null} — нечего тянуть.
     */
    @Nullable
    public Drawing.Outcome pull(@Nullable Player player) {
        ItemStack s = inv.getStackInSlot(0);
        if (s.isEmpty() || s.getCount() != 1 || level == null) return null;
        Wire w = Smithy.readWire(s, level.getGameTime());
        if (w == null) {
            if (player != null) player.displayClientMessage(ManufacturingText.msg("draw.not_drawable"), true);
            return null;
        }
        if (player != null && !HotHandling.hasTongs(player.getInventory())) {
            player.displayClientMessage(ManufacturingText.msg("draw.no_tongs"), true);
            return null;
        }
        boolean lubricated = useLube();
        Drawing.Pass p = Drawing.pass(w, dieM(), lubricated ? Drawing.MU_LUBRICATED : Drawing.MU_DRY, Drawing.HAND_FORCE_N);
        lastOutcome = p.outcome().ordinal();
        lastForceN = (int) Math.round(p.forceN());
        if (p.outcome() != Drawing.Outcome.TOO_STRONG && p.outcome() != Drawing.Outcome.NO_REDUCTION)
            inv.setStackInSlot(0, Smithy.wireStack(p.wire()));
        if (player != null) {
            switch (p.outcome()) {
                case TOO_STRONG -> player.displayClientMessage(ManufacturingText.msg("draw.too_strong", lastForceN,
                        (int) Drawing.HAND_FORCE_N), true);
                case BROKE -> player.displayClientMessage(ManufacturingText.msg("draw.broke"), true);
                case NO_REDUCTION -> player.displayClientMessage(ManufacturingText.msg("draw.no_reduction"), true);
                case OK -> { }
            }
        }
        if (level instanceof ServerLevel sl) {
            boolean broke = p.outcome() == Drawing.Outcome.BROKE;
            sl.playSound(null, worldPosition, broke ? SoundEvents.ITEM_BREAK : SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS,
                    0.5f, broke ? 1.2f : 0.8f);
        }
        setChanged();
        return p.outcome();
    }

    private boolean useLube() {
        if (lubeLeft == 0) {
            ItemStack l = inv.getStackInSlot(1);
            if (!isLube(l)) return false;
            l.shrink(1);
            lubeLeft = LUBE_PASSES;
        }
        lubeLeft--;
        return true;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case SmithyPanels.BUTTON_DIE_SMALLER -> setDie(die + 1);
            case SmithyPanels.BUTTON_DIE_LARGER -> setDie(die - 1);
            case SmithyPanels.BUTTON_PULL -> pull(player);
            default -> { }
        }
    }

    @Override
    public void tickDevice() {
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return Smithy.drawable(stack) || isLube(stack);
    }

    @Override
    public InteractionResult use(Player player) {
        return InteractionResult.PASS;
    }

    @Override
    public PanelLayout panelLayout() {
        return SmithyPanels.DRAW_PLATE;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[SmithyPanels.D_DIE_UM] = (int) Math.round(DIES_MM[die] * 1000);
        out[SmithyPanels.D_OUTCOME] = lastOutcome;
        out[SmithyPanels.D_FORCE_N] = lastForceN;
        out[SmithyPanels.D_LUBE] = lubeLeft + (isLube(inv.getStackInSlot(1)) ? inv.getStackInSlot(1).getCount() * LUBE_PASSES : 0);
        Wire w = level == null ? null : Smithy.readWire(inv.getStackInSlot(0), level.getGameTime());
        int f = 0;
        if (w != null) {
            f |= 1 << SmithyPanels.DF_WIRE;
            out[SmithyPanels.D_WIRE_UM] = (int) Math.round(w.diameterM() * 1e6);
            out[SmithyPanels.D_LENGTH_CM] = (int) Math.round(w.lengthM() * 100);
            out[SmithyPanels.D_STRAIN_PCT] = (int) Math.round(100 * w.strain() / w.metal().maxStrain);
            out[SmithyPanels.D_METAL] = w.metal().ordinal();
        }
        out[SmithyPanels.FLAGS] = f;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("die", die);
        tag.putInt("lube", lubeLeft);
        tag.putInt("last", lastOutcome);
        tag.putInt("force", lastForceN);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        die = Math.max(0, Math.min(DIES_MM.length - 1, tag.getInt("die")));
        lubeLeft = Math.max(0, Math.min(LUBE_PASSES, tag.getInt("lube")));
        lastOutcome = tag.contains("last") ? tag.getInt("last") : -1;
        lastForceN = Math.max(0, tag.getInt("force"));
    }
}
