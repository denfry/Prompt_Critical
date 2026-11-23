package dev.denfry.promptcritical.mod.manufacturing;

import dev.denfry.promptcritical.mod.foundry.FoundryTags;
import dev.denfry.promptcritical.mod.foundry.MachineInventory;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.sim.manufacturing.Drawing;
import dev.denfry.promptcritical.sim.manufacturing.ForgeHeating;
import dev.denfry.promptcritical.sim.manufacturing.Tempering;
import dev.denfry.promptcritical.sim.manufacturing.Wire;
import dev.denfry.promptcritical.sim.manufacturing.Workpiece;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Кузнечный горн {@code forge_hearth} (18 §18.6.2, §18.7): уголь в горн, мехи кнопкой щита. Заготовка в огне греется к
 * температуре огня ({@link ForgeHeating}, окалина копится между секундами), заготовка на краю горна — к трети этого
 * нагрева: там её отпускают по цветам побежалости ({@link Tempering}). Стальной прокат, положенный в горн, становится
 * поковкой.
 */
public final class ForgeHearthBlockEntity extends SmithyDevice {
    /** Топливо в горне, кг; масса на предмет, кг [BALANCE]. */
    public static final double FUEL_CAPACITY_KG = 10, FUEL_PER_ITEM_KG = 1;
    /** Расход: на холостом ходу и сверх — на полном дутье, кг/с [BALANCE: ведро угля на несколько часов работы]. */
    public static final double FUEL_IDLE_KG_S = 0.0003, FUEL_BLAST_KG_S = 0.0015;
    /** Огонь подходит к уставке мехов с постоянной времени, с [BALANCE]. */
    public static final double FIRE_TAU_S = 30;
    /** Край горна получает эту долю нагрева огня над воздухом [моё: 900 °C огня — около 310 °C на краю]. */
    public static final double EDGE_SHARE = 0.35;
    public static final double BELLOWS_PUMP = 0.25, BLAST_DECAY_S = 0.08;
    private static final MachineInventory.Role[] ROLES = {MachineInventory.Role.INPUT, MachineInventory.Role.INPUT,
            MachineInventory.Role.INPUT};

    private double fuelKg;
    private double fireK = WorkpieceData.AMBIENT_K;
    private double blast;

    public ForgeHearthBlockEntity(BlockPos pos, BlockState state) {
        super(SmithyBlockEntities.FORGE_HEARTH.get(), pos, state, ROLES, (slot, s) -> slot == 0 ? isFuel(s) : slot == 1 ? isFireable(s) : isForgeable(s));
    }

    public static boolean isFuel(ItemStack s) {
        return s.is(FoundryTags.FURNACE_COAL);
    }

    public static boolean isForgeable(ItemStack s) {
        return Smithy.forgeable(s);
    }

    /** В огонь кладут и поковку, и проволоку на отжиг (18 §18.6.7). */
    public static boolean isFireable(ItemStack s) {
        return Smithy.forgeable(s) || s.has(ManufacturingComponents.WIRE.get());
    }

    public double fireK() {
        return fireK;
    }

    public double blast() {
        return blast;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel)) {
            clientTick();
            return;
        }
        if (level.getGameTime() % 20 == 0) step(1.0);
    }

    /** Секунда горна. */
    public void step(double seconds) {
        long now = level.getGameTime();
        ItemStack fuel = inv.getStackInSlot(0);
        while (!fuel.isEmpty() && fuelKg + FUEL_PER_ITEM_KG <= FUEL_CAPACITY_KG) {
            fuelKg += FUEL_PER_ITEM_KG;
            fuel.shrink(1);
        }
        double target = fuelKg > 0 ? ForgeHeating.fireTempK(blast) : WorkpieceData.AMBIENT_K;
        fireK = target + (fireK - target) * Math.exp(-seconds / FIRE_TAU_S);
        if (fuelKg > 0) fuelKg = Math.max(0, fuelKg - (FUEL_IDLE_KG_S + FUEL_BLAST_KG_S * blast) * seconds);
        blast = Math.max(0, blast - BLAST_DECAY_S * seconds);
        heatSlot(1, fireK, false, seconds, now);
        heatSlot(2, WorkpieceData.AMBIENT_K + EDGE_SHARE * (fireK - WorkpieceData.AMBIENT_K), true, seconds, now);
        setChanged();
        boolean lit = fuelKg > 0 && fireK > 773.15;
        BlockState st = getBlockState();
        if (st.hasProperty(BlockStateProperties.LIT) && st.getValue(BlockStateProperties.LIT) != lit)
            level.setBlock(worldPosition, st.setValue(BlockStateProperties.LIT, lit), Block.UPDATE_ALL);
    }

    private void heatSlot(int slot, double toK, boolean temper, double seconds, long now) {
        ItemStack s = inv.getStackInSlot(slot);
        if (s.getCount() > 1) return; // стопку проката в огонь не берут: кузнец греет по одному куску
        WireData wire = s.get(ManufacturingComponents.WIRE.get());
        if (wire != null) {
            // моток проволоки тонкий: прогревается сразу, отжиг идёт при температуре огня
            Wire annealed = Drawing.anneal(wire.wire(), toK, seconds);
            if (annealed.strain() != wire.wire().strain()) inv.setStackInSlot(slot, Smithy.wireStack(annealed));
            return;
        }
        Workpiece w = Smithy.read(s, now);
        if (w == null) return;
        w = ForgeHeating.heat(w, toK, seconds);
        if (temper) w = Tempering.temper(w, w.tempK(), seconds);
        inv.setStackInSlot(slot, Smithy.stack(w, now));
    }

    public void pump() {
        blast = Math.min(1, blast + BELLOWS_PUMP);
        if (level != null) level.playSound(null, worldPosition, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.25f, 0.7f);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == SmithyPanels.BUTTON_BELLOWS) pump();
    }

    private void clientTick() {
        MachineFx fx = MachineFx.of(level, worldPosition, getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING));
        if (fx == null || !getBlockState().getValue(BlockStateProperties.LIT)) return;
        if (fx.chance(5)) fx.flame(fx.at(8, 12.5, 8), 0.1);
        if (fx.chance(3)) fx.smoke(fx.at(8, 16, 12), false, 0.08);
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return isFuel(stack) || isFireable(stack);
    }

    @Override
    public InteractionResult use(Player player) {
        player.displayClientMessage(ManufacturingText.msg("forge.status", Math.round(fireK - 273.15), Math.round(fuelKg * 10) / 10.0),
                true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public dev.denfry.promptcritical.mod.panel.PanelLayout panelLayout() {
        return SmithyPanels.FORGE;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        long now = level == null ? 0 : level.getGameTime();
        out[SmithyPanels.H_FIRE_C] = (int) Math.round(fireK - 273.15);
        out[SmithyPanels.H_BLAST_PCT] = (int) Math.round(blast * 100);
        out[SmithyPanels.H_FUEL_KG10] = (int) Math.round(fuelKg * 10);
        int f = fuelKg > 0 && fireK > 773.15 ? 1 << SmithyPanels.HF_LIT : 0;
        Workpiece a = Smithy.read(inv.getStackInSlot(1), now), b = Smithy.read(inv.getStackInSlot(2), now);
        if (a != null) {
            f |= 1 << SmithyPanels.HF_PIECE;
            out[SmithyPanels.H_PIECE_C] = (int) Math.round(a.tempK() - 273.15);
        }
        if (b != null) {
            f |= 1 << SmithyPanels.HF_EDGE;
            out[SmithyPanels.H_EDGE_C] = (int) Math.round(b.tempK() - 273.15);
        }
        out[SmithyPanels.FLAGS] = f;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("fuel", fuelKg);
        tag.putDouble("fire", fireK);
        tag.putDouble("blast", blast);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fuelKg = Math.max(0, tag.getDouble("fuel"));
        fireK = tag.contains("fire") ? Math.max(1, tag.getDouble("fire")) : WorkpieceData.AMBIENT_K;
        blast = Math.max(0, Math.min(1, tag.getDouble("blast")));
    }
}
