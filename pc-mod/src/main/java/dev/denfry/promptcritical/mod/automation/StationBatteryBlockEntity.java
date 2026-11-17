package dev.denfry.promptcritical.mod.automation;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.ironworks.IronworksBlockEntity;
import dev.denfry.promptcritical.sim.automation.LeadAcidBattery;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Аккумуляторная батарея оперативного тока ({@link LeadAcidBattery}, 24 В, 100 А·ч) рядом с выпрямителем
 * {@code relay_power_supply}: пока шина 0,4 кВ под напряжением, выпрямитель держит её на подзаряде; пропала шина —
 * батарея питает реле и приводы выключателей, пока не разрядится. Блок показывает степень заряда банками.
 */
public final class StationBatteryBlockEntity extends IronworksBlockEntity {
    public static final double CAPACITY_AH = 100;

    private final LeadAcidBattery battery = new LeadAcidBattery(CAPACITY_AH);
    private double lastChargeA;
    private int ticks;

    public StationBatteryBlockEntity(BlockPos pos, BlockState state) {
        super(AutomationBlockEntities.STATION_BATTERY.get(), pos, state);
    }

    public LeadAcidBattery battery() {
        return battery;
    }

    /** Батарея, которая касается {@code pos} гранью. */
    @Nullable
    public static StationBatteryBlockEntity adjacent(Level level, BlockPos pos) {
        for (Direction d : Direction.values())
            if (level.getBlockEntity(pos.relative(d)) instanceof StationBatteryBlockEntity b) return b;
        return null;
    }

    /** Выпрямитель под напряжением: подзаряд на тик, А. */
    void floatCharge(double dtS) {
        lastChargeA = battery.charge(dtS);
    }

    /** Шины нет: батарея отдаёт ток {@code currentA}; вход выпрямителя, о.е. (0 — отключена). */
    double supply(double currentA, double dtS) {
        lastChargeA = -currentA;
        battery.discharge(currentA, dtS);
        return battery.terminalV(currentA) / 24.0;
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
        if (++ticks % 20 != 0) return;
        int level = battery.cutOff() ? 0 : Math.min(4, (int) Math.floor(battery.soc() * 4 + 0.5));
        BlockState s = getBlockState();
        if (s.getValue(PcProperties.CHARGE) != level) this.level.setBlock(worldPosition, s.setValue(PcProperties.CHARGE, level), 3);
        setChanged();
    }

    @Override
    public InteractionResult use(Player player) {
        player.displayClientMessage(Component.translatable("message.promptcritical.station_battery",
                String.format(Locale.ROOT, "%.0f", battery.soc() * 100), String.format(Locale.ROOT, "%.1f", battery.terminalV(0)),
                String.format(Locale.ROOT, "%.1f", lastChargeA)).withStyle(battery.cutOff() ? ChatFormatting.RED : ChatFormatting.WHITE), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("charge_ah", battery.chargeAh());
        tag.putBoolean("cut_off", battery.cutOff());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("charge_ah")) battery.restore(tag.getDouble("charge_ah"), tag.getBoolean("cut_off"));
    }
}
