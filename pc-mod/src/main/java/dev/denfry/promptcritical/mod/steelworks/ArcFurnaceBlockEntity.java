package dev.denfry.promptcritical.mod.steelworks;

import dev.denfry.promptcritical.mod.grid.GridBlockEntities;
import dev.denfry.promptcritical.mod.grid.GridDeviceBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.ironworks.LadleBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.grid.ArcFurnaceUnit;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.metallurgy.AlloyElement;
import dev.denfry.promptcritical.sim.metallurgy.ArcFurnace;
import dev.denfry.promptcritical.sim.metallurgy.SteelMelt;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Дуговая печь ДСП-6 ({@link ArcFurnace}) на шине 6 кВ: корпус 3×3×3 и свод сверху. Шихта — стальной лом (рукой или
 * воронкой), дуга — правый клик, плавка готова — выпуск в ковш перед носком (Shift и правый клик или сам при 1620 °C,
 * если ковш стоит). Кабель — к любой наружной грани корпуса.
 */
public class ArcFurnaceBlockEntity extends GridDeviceBlockEntity<ArcFurnaceUnit> {
    private static final double DT = 0.05;
    /** Химия переплавленного лома углеродистой стали [допущение]. */
    private static final Map<AlloyElement, Double> SCRAP_CHEM = new EnumMap<>(Map.of(AlloyElement.C, 0.002,
            AlloyElement.MN, 0.005, AlloyElement.SI, 0.002));

    private final ArcFurnace furnace = new ArcFurnace();
    private boolean arc;

    public ArcFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(GridBlockEntities.ARC_FURNACE.get(), pos, state);
    }

    public ArcFurnace furnace() {
        return furnace;
    }

    public boolean arc() {
        return arc;
    }

    @Override
    protected ArcFurnaceUnit unitOf(GridSubsystem grid) {
        return grid.arcFurnace(simId());
    }

    @Override
    public List<List<Face>> terminals() {
        List<Face> out = new ArrayList<>();
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            for (Direction d : Direction.values()) {
                int nx = x + d.getStepX(), ny = y + d.getStepY(), nz = z + d.getStepZ();
                if (Math.abs(nx) > 1 || Math.abs(ny) > 1 || Math.abs(nz) > 1) out.add(new Face(worldPosition.offset(x, y, z), d));
            }
        }
        return List.of(out);
    }

    @Override
    protected void serverTick(ArcFurnaceUnit u) {
        double v = u.voltagePu();
        furnace.step(DT, v, level.getGameTime(), arc);
        u.setDemand(furnace.powerW(), furnace.reactiveVar());
        if (furnace.readyToTap() && ladle() != null) tap(null);
        if (ticks % 10 == 0) setFlag(BlockStateProperties.LIT, furnace.powerW() > 0);
        if (ticks % 100 == 0) setChanged();
    }

    /** Ковш перед носком печи (у фасада, ниже корпуса). */
    @Nullable
    private LadleBlockEntity ladle() {
        Direction f = facing();
        BlockPos spout = worldPosition.relative(f, 2);
        AABB box = new AABB(spout).inflate(1, 2, 1).move(0, -1, 0);
        List<LadleBlockEntity> found = Ironworks.blockEntities(level, box, LadleBlockEntity.class);
        for (LadleBlockEntity l : found) if (!l.frozen() && (l.metalKg() <= 0 || l.steel())) return l;
        return null;
    }

    /** Выпустить плавку в ковш; что не поместилось — остаётся в печи. */
    private void tap(@Nullable Player player) {
        LadleBlockEntity l = ladle();
        if (l == null) {
            if (player != null) player.displayClientMessage(Component.translatable("message.promptcritical.eaf.no_ladle").withStyle(ChatFormatting.RED), true);
            return;
        }
        double t = furnace.tempK(), m = furnace.massKg(), h = furnace.enthalpy();
        double room = Math.min(m, l.roomKg());
        if (room <= 0) return;
        double taken = l.receive(new SteelMelt(room, t, SCRAP_CHEM), true);
        double left = m - taken;
        furnace.tap();
        if (left > 1e-6) furnace.restore(left, h, furnace.electrodeKg());
        arc = false;
        level.playSound(null, worldPosition, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 1.0f, 0.7f);
        if (player != null) player.displayClientMessage(Component.translatable("message.promptcritical.eaf.tapped",
                String.format(Locale.ROOT, "%.0f", taken), String.format(Locale.ROOT, "%.0f", t - 273.15)), true);
    }

    // --- игрок ---

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.is(PcItems.STEEL_SCRAP.get());
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (!accepts(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        double perItem = Ironworks.bulkKg();
        int n = (int) Math.min(stack.getCount(), Math.floor((ArcFurnace.CAPACITY_KG - furnace.massKg()) / perItem));
        if (n <= 0) {
            player.displayClientMessage(Component.translatable("message.promptcritical.eaf.full"), true);
            return ItemInteractionResult.SUCCESS;
        }
        furnace.charge(n * perItem);
        if (!player.getAbilities().instabuild) stack.shrink(n);
        level.playSound(null, worldPosition, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0f, 0.6f);
        return ItemInteractionResult.SUCCESS;
    }

    /** Дуга вкл/выкл; с Shift — выпуск. Сообщение — масса, расплав, температура, мощность. */
    @Override
    public InteractionResult use(Player player) {
        if (player.isShiftKeyDown()) {
            if (furnace.moltenFraction() < 1) {
                player.displayClientMessage(Component.translatable("message.promptcritical.eaf.not_molten"), true);
            } else {
                tap(player);
            }
            return InteractionResult.SUCCESS;
        }
        if (!arc && furnace.massKg() <= 0) {
            player.displayClientMessage(Component.translatable("message.promptcritical.eaf.empty"), true);
            return InteractionResult.SUCCESS;
        }
        arc = !arc;
        setChanged();
        player.displayClientMessage(Component.translatable(arc ? "message.promptcritical.eaf.arc_on" : "message.promptcritical.eaf.arc_off",
                String.format(Locale.ROOT, "%.0f", furnace.massKg()), String.format(Locale.ROOT, "%.0f", 100 * furnace.moltenFraction()),
                String.format(Locale.ROOT, "%.0f", furnace.tempK() - 273.15), String.format(Locale.ROOT, "%.2f", furnace.powerW() / 1e6)), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("mass", furnace.massKg());
        tag.putDouble("enthalpy", furnace.enthalpy());
        tag.putDouble("electrodes", furnace.electrodeKg());
        tag.putBoolean("arc", arc);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        furnace.restore(tag.getDouble("mass"), tag.getDouble("enthalpy"), tag.getDouble("electrodes"));
        arc = tag.getBoolean("arc");
    }
}
