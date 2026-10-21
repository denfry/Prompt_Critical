package dev.denfry.promptcritical.mod.steelworks;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.MachineInventory;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.ironworks.GasBuffer;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.ironworks.IronworksBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.LadleBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.metallurgy.AlloyElement;
import dev.denfry.promptcritical.sim.metallurgy.Ferroalloy;
import dev.denfry.promptcritical.sim.metallurgy.HeatTreatState;
import dev.denfry.promptcritical.sim.metallurgy.LadleStation;
import dev.denfry.promptcritical.sim.metallurgy.SteelGrade;
import dev.denfry.promptcritical.sim.metallurgy.SteelMelt;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/**
 * Установка доводки {@code ladle_station} 3×2×3 (08 §8.6.2): сталеразливочный ковш подвозят к стенду (в пределах
 * {@value #REACH} блоков), из бункеров отдаются ферросплавы — по предмету (100 кг) на кнопку, раскисление Si и Al
 * и легирование по {@link LadleStation}; аргон через пробку (газ {@code argon} из труб,
 * {@value #ARGON_NM3_MIN} нм³/мин физ.) перемешивает ванну и остужает её. Щит показывает химию и марку, которая
 * получится при разливке.
 */
public final class LadleStationBlockEntity extends IronworksBlockEntity implements PanelHost {
    public static final int REACH = 2, BINS = 3;
    public static final double ARGON_NM3_MIN = 0.1;
    private static final MachineInventory.Role[] ROLES = {MachineInventory.Role.INPUT, MachineInventory.Role.INPUT,
            MachineInventory.Role.INPUT};

    private final MachineInventory inv = new MachineInventory(ROLES, (slot, s) -> isAlloy(s), this::setChanged);
    private final GasBuffer argon = new GasBuffer(4_000, s -> s.getFluid().isSame(PcFluids.ARGON.source.get()), this::setChanged);
    private boolean stirring;
    private double stirredS;

    public LadleStationBlockEntity(BlockPos pos, BlockState state) {
        super(SteelworksBlockEntities.LADLE_STATION.get(), pos, state);
    }

    static boolean isAlloy(ItemStack s) {
        return s.is(PcItems.FERROALLOY.get()) && SteelworksComponents.alloy(s.getOrDefault(SteelworksComponents.FERROALLOY.get(), "")) != null;
    }

    /** Ковш со сталью у стенда. */
    @Nullable
    public LadleBlockEntity ladle() {
        AABB box = new AABB(worldPosition).inflate(1 + REACH, 2, 1 + REACH);
        List<LadleBlockEntity> found = Ironworks.blockEntities(level, box, LadleBlockEntity.class);
        for (LadleBlockEntity l : found) if (l.steel() && !l.frozen()) return l;
        return null;
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
        double dtp = Ironworks.tickPhysS(b);
        LadleBlockEntity l = stirring ? ladle() : null;
        boolean blowing = false;
        if (l != null) {
            double need = ARGON_NM3_MIN / 60 * dtp;
            if (argon.draw(need) >= need * 0.99) {
                l.replaceMelt(LadleStation.argonStir(l.melt(), dtp));
                stirredS += dtp;
                blowing = true;
            }
        }
        setStateFlag(PcProperties.RUNNING, blowing);
    }

    /** Отдать в ковш один предмет из бункера {@code bin}. */
    public boolean addFromBin(int bin) {
        LadleBlockEntity l = ladle();
        if (l == null || bin < 0 || bin >= BINS) return false;
        ItemStack s = inv.getStackInSlot(bin);
        Ferroalloy a = isAlloy(s) ? SteelworksComponents.alloy(s.get(SteelworksComponents.FERROALLOY.get())) : null;
        if (a == null) return false;
        SteelMelt m = l.melt();
        if (m == null) return false;
        l.replaceMelt(LadleStation.addAlloy(m, a, Ironworks.bulkKg()));
        s.shrink(1);
        stirredS = 0;
        level.playSound(null, worldPosition, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.8f, 1.2f);
        setChanged();
        return true;
    }

    @Override
    protected void clientTick() {
        if (!stateFlag(PcProperties.RUNNING)) return;
        MachineFx fx = fx();
        if (fx.chance(2)) fx.smoke(fx.at(8, 4, -8), false, 0.6);
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return inv.external();
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return argon.input();
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return isAlloy(stack);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        player.setItemInHand(hand, inv.insertFromPlayer(stack));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        LadleBlockEntity l = ladle();
        SteelMelt m = l == null ? null : l.melt();
        if (m == null) player.displayClientMessage(SteelworksText.msg("station_no_ladle"), true);
        else player.displayClientMessage(SteelworksText.msg("station", SteelworksText.gradeName(grade(m)),
                SteelworksText.num(m.tempK() - 273.15), SteelworksText.num(m.frac(AlloyElement.O) * 1e6)), true);
        return InteractionResult.SUCCESS;
    }

    /** Марка, которую даст расплав при разливке. */
    public static SteelGrade grade(SteelMelt m) {
        return SteelGrade.classify(m.chem(), HeatTreatState.AS_ROLLED);
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return SteelworksPanels.LADLE_STATION;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return inv;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        LadleBlockEntity l = ladle();
        SteelMelt m = l == null ? null : l.melt();
        out[SteelworksPanels.L_ARGON_MB] = argon.amount();
        out[SteelworksPanels.L_STIR_MIN10] = (int) Math.round(stirredS / 6);
        int flags = 0;
        if (stirring) flags |= 1 << SteelworksPanels.LF_STIR;
        if (m != null) {
            flags |= 1 << SteelworksPanels.LF_LADLE;
            out[SteelworksPanels.L_TEMP_C] = (int) Math.round(m.tempK() - 273.15);
            out[SteelworksPanels.L_MASS_KG] = (int) Math.round(m.massKg());
            out[SteelworksPanels.L_C_PCT1000] = (int) Math.round(m.frac(AlloyElement.C) * 100_000);
            out[SteelworksPanels.L_MN_PCT100] = (int) Math.round(m.frac(AlloyElement.MN) * 10_000);
            out[SteelworksPanels.L_SI_PCT100] = (int) Math.round(m.frac(AlloyElement.SI) * 10_000);
            out[SteelworksPanels.L_CR_PCT100] = (int) Math.round(m.frac(AlloyElement.CR) * 10_000);
            out[SteelworksPanels.L_O_PPM] = (int) Math.round(m.frac(AlloyElement.O) * 1e6);
            SteelGrade g = grade(m);
            out[SteelworksPanels.L_FAMILY] = g.family().ordinal();
            if (m.killed()) flags |= 1 << SteelworksPanels.LF_KILLED;
            if (g.meetsSpec()) flags |= 1 << SteelworksPanels.LF_SPEC;
        } else {
            out[SteelworksPanels.L_FAMILY] = -1;
        }
        out[SteelworksPanels.L_FLAGS] = flags;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case SteelworksPanels.BTN_ADD_1, SteelworksPanels.BTN_ADD_2, SteelworksPanels.BTN_ADD_3 -> {
                if (!addFromBin(id - SteelworksPanels.BTN_ADD_1)) player.displayClientMessage(SteelworksText.msg("station_cannot_add"), true);
            }
            case SteelworksPanels.BTN_STIR -> {
                stirring = !stirring;
                setChanged();
            }
            default -> {
            }
        }
    }

    public void setStirring(boolean on) {
        stirring = on;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inv", inv.save(registries));
        tag.put("argon", argon.save(registries));
        tag.putBoolean("stirring", stirring);
        tag.putDouble("stirred_s", stirredS);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("inv")) inv.load(registries, tag.getCompound("inv"));
        if (tag.contains("argon")) argon.load(registries, tag.getCompound("argon"));
        stirring = tag.getBoolean("stirring");
        stirredS = tag.getDouble("stirred_s");
    }
}
