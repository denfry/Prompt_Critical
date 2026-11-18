package dev.denfry.promptcritical.mod.mine;

import dev.denfry.promptcritical.mod.grid.GridDeviceBlockEntity;
import dev.denfry.promptcritical.mod.item.CapLampData;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcDataComponents;
import dev.denfry.promptcritical.sim.battery.CapLamp;
import dev.denfry.promptcritical.sim.battery.LampCharger;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.grid.LampRackUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Зарядный стеллаж ламповой ({@link LampRackUnit}) на {@value #SLOTS} головных светильников: каждое место — свой
 * трансформатор с выпрямителем и заряд CC/CV ({@link LampCharger}); сумма их мощности — нагрузка-выпрямитель на шине
 * 0,4 кВ, кабель — к любой грани, кроме фасада. Лампа с разбитым стеклом, поставленная на заряд, получает новое стекло
 * и лампу — это работа ламповщицы. Время — игровое, как у самих ламп ({@link CapLampService#SECONDS_PER_TICK}).
 */
public class LampRackBlockEntity extends GridDeviceBlockEntity<LampRackUnit> implements PanelHost {
    public static final int SLOTS = 12;

    private final ItemStackHandler lamps = new ItemStackHandler(SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof CapLampItem;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private double acW, maxTempC;
    private int charging, full, repaired;

    public LampRackBlockEntity(BlockPos pos, BlockState state) {
        super(MineBlockEntities.LAMP_RACK.get(), pos, state);
    }

    @Override
    protected LampRackUnit unitOf(GridSubsystem grid) {
        return grid.lampRack(simId());
    }

    @Override
    public List<List<Face>> terminals() {
        List<Face> out = new ArrayList<>();
        for (Direction d : Direction.values()) if (d != facing()) out.add(new Face(worldPosition, d));
        return List.of(out);
    }

    public IItemHandlerModifiable lamps() {
        return lamps;
    }

    // --- заряд ---

    @Override
    protected void serverTick(LampRackUnit u) {
        if (ticks % CapLampService.UPDATE_TICKS != 0) return;
        ServerLevel level = (ServerLevel) this.level;
        long now = level.getGameTime();
        double supply = u.supplyPu();
        double ac = 0, tmax = 0;
        int nCharging = 0, nFull = 0;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack s = lamps.getStackInSlot(i);
            if (!(s.getItem() instanceof CapLampItem)) continue;
            CapLampData d = CapLampItem.data(s);
            CapLamp lamp = d.toLamp();
            if (lamp.glassBroken() || lamp.bulbBurnt()) {
                lamp.repair();
                repaired++;
            }
            long last = d.lastTick() < 0 ? now : d.lastTick();
            double left = Math.max(0, now - last) * CapLampService.SECONDS_PER_TICK;
            LampCharger.Step step = LampCharger.Step.IDLE;
            while (left > 0) {
                double dt = Math.min(CapLampService.MAX_STEP_S, left);
                step = LampCharger.charge(lamp.battery(), dt, supply, CapLampService.AMBIENT_C);
                left -= dt;
            }
            ac += step.acPowerW();
            if (step.full()) nFull++;
            else if (step.currentA() > 0) nCharging++;
            tmax = Math.max(tmax, lamp.battery().temperatureC());
            lamps.setStackInSlot(i, withData(s, CapLampData.of(lamp, now)));
        }
        acW = ac;
        maxTempC = tmax;
        charging = nCharging;
        full = nFull;
        u.setDemandW(ac);
        setFlag(BlockStateProperties.LIT, supply > 0.5);
    }

    private static ItemStack withData(ItemStack s, CapLampData d) {
        ItemStack copy = s.copy();
        copy.set(PcDataComponents.CAP_LAMP.get(), d);
        return copy;
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return LampRackPanels.RACK;
    }

    @Override
    public Component panelTitle() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public BlockPos panelPos() {
        return worldPosition;
    }

    @Override
    public boolean panelValid(Player player) {
        return PanelHost.near(this, player);
    }

    @Override
    @Nullable
    public IItemHandlerModifiable panelItems() {
        return lamps;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        LampRackUnit u = unit();
        if (u == null) return;
        int present = 0;
        for (int i = 0; i < SLOTS; i++) if (!lamps.getStackInSlot(i).isEmpty()) present++;
        out[LampRackPanels.SUPPLY_V] = (int) Math.round(u.supplyPu() * 400);
        out[LampRackPanels.POWER_W10] = (int) Math.round(acW * 10);
        out[LampRackPanels.LAMPS] = present;
        out[LampRackPanels.CHARGING] = charging;
        out[LampRackPanels.FULL] = full;
        out[LampRackPanels.TEMP_C10] = (int) Math.round(maxTempC * 10);
        out[LampRackPanels.REPAIRED] = repaired;
        out[LampRackPanels.FLAGS] = (u.supplyPu() > 0.5 ? 1 << LampRackPanels.F_SUPPLY : 0)
                | (u.switchedOn() ? 1 << LampRackPanels.F_BREAKER : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        LampRackUnit u = unit();
        if (u != null && id == LampRackPanels.BUTTON_BREAKER) u.setSwitchedOn(!u.switchedOn());
    }

    // --- сохранение ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("lamps", lamps.serializeNBT(registries));
        tag.putInt("repaired", repaired);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("lamps")) lamps.deserializeNBT(registries, tag.getCompound("lamps"));
        repaired = tag.getInt("repaired");
    }
}
