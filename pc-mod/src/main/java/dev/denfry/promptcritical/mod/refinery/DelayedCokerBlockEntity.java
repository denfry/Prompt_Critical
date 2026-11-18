package dev.denfry.promptcritical.mod.refinery;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.uranium.UraniumProcessBlockEntity;
import dev.denfry.promptcritical.sim.refining.DelayedCoking;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Замедленное коксование мазута ({@link DelayedCoking}): печь с выходом змеевика 495–505 °C и две коксовые камеры,
 * которые попеременно заполняются по {@link DelayedCoking#FILL_H} ч, а затем пропариваются, охлаждаются водой и
 * режутся гидрорезкой. Мазут — трубой к боковым граням; бензин коксования — сверху, газойль коксования (в модели —
 * {@code diesel_fuel}, его доочищает гидроочистка) — снизу; нефтяной кокс выгружается партией, когда отработавшая
 * камера доходит до гидрорезки. H₂S из газа копится в аппарате и уходит в установку Клауса, которая касается его;
 * сухой газ сгорает в печи. Время процесса ускорено в {@link #TIME_SCALE} раз [BALANCE]: цикл камеры 28 ч — 14 мин.
 */
public final class DelayedCokerBlockEntity extends UraniumProcessBlockEntity {
    public static final int MB = 8_000;
    /** Мазут западносибирской нефти: коксуемость 10 %, сера 1,8 % [BALANCE: типичный атмосферный остаток]. */
    public static final double FEED_CCR = 0.10, FEED_SULFUR = 0.018, FUEL_OIL_KG_L = 0.95, GASOLINE_KG_L = 0.735, DIESEL_KG_L = 0.84;
    public static final double FEED_L_S = 2, TIME_SCALE = 120, COKE_KG_PER_ITEM = 10, H2S_HOLDUP_KG = 50;
    public static final double T_MIN_C = 470, T_MAX_C = 530;

    private final FeedTank feed = new FeedTank(PcFluids.FUEL_OIL.source.get(), MB);
    private final ProductTank naphtha = new ProductTank(PcFluids.GASOLINE.source.get(), MB);
    private final ProductTank gasOil = new ProductTank(PcFluids.DIESEL_FUEL.source.get(), MB);
    private double coilC = 500, hours, drumKg, waitingKg, cutKg, h2sKg, feedLs;
    private DelayedCoking.Yields last = DelayedCoking.yields(FEED_CCR, FEED_SULFUR, 500);

    public DelayedCokerBlockEntity(BlockPos pos, BlockState state) {
        super(RefineryBlockEntities.DELAYED_COKER.get(), pos, state, 0, 4, s -> false);
    }

    public DelayedCoking.Yields last() {
        return last;
    }

    public FeedTank feed() {
        return feed;
    }

    public ProductTank naphtha() {
        return naphtha;
    }

    public ProductTank gasOil() {
        return gasOil;
    }

    /** Часы заполнения текущей камеры. */
    public double hours() {
        return hours;
    }

    public void setCoilC(double c) {
        coilC = Math.clamp(c, T_MIN_C, T_MAX_C);
    }

    /** Фаза второй камеры: она начала заполняться на {@link DelayedCoking#FILL_H} ч раньше текущей. */
    public DelayedCoking.Phase otherDrum() {
        return DelayedCoking.phaseAt(hours + DelayedCoking.FILL_H);
    }

    /** Отдать сероводород установке Клауса, кг. */
    public double drainH2s(double maxKg) {
        double kg = Math.min(maxKg, h2sKg);
        h2sKg -= kg;
        return kg;
    }

    @Override
    protected void process(ServerLevel level, double dt) {
        feedLs = 0;
        last = DelayedCoking.yields(FEED_CCR, FEED_SULFUR, coilC);
        if (on && h2sKg < H2S_HOLDUP_KG) {
            double l = Math.min(FEED_L_S * dt, feed.amount());
            l = Math.min(l, Math.min(naphtha.room() * GASOLINE_KG_L / Math.max(1e-9, last.naphtha() * FUEL_OIL_KG_L),
                    gasOil.room() * DIESEL_KG_L / Math.max(1e-9, last.gasOil() * FUEL_OIL_KG_L)));
            if (l > 0) {
                double kg = l * FUEL_OIL_KG_L;
                feed.take(l);
                naphtha.add(kg * last.naphtha() / GASOLINE_KG_L);
                gasOil.add(kg * last.gasOil() / DIESEL_KG_L);
                drumKg += kg * last.coke();
                h2sKg += kg * last.h2sKgPerKg();
                feedLs = l / dt;
                hours += dt * TIME_SCALE / 3600;
                if (hours >= DelayedCoking.FILL_H) {
                    // переключение камер: заполненная уходит на пропарку, её кокс ждёт гидрорезки
                    hours -= DelayedCoking.FILL_H;
                    cutKg += waitingKg;
                    waitingKg = drumKg;
                    drumKg = 0;
                }
            }
        }
        if (waitingKg > 0 && otherDrum() == DelayedCoking.Phase.DECOKE) {
            cutKg += waitingKg;
            waitingKg = 0;
        }
        while (cutKg >= COKE_KG_PER_ITEM && emit(PcItems.PETROLEUM_COKE.get())) cutKg -= COKE_KG_PER_ITEM;
    }

    @Override
    protected boolean running() {
        return feedLs > 0;
    }

    /**
     * Пока установка работает, вторая камера проходит пропарку, охлаждение и гидрорезку (арт —
     * art/blockbench/refinery/delayed_coker.author.js): из её воздушника над верхним люком уходит пар, а по желобу
     * под нижним люком сходит вода гидрорезки с кусками кокса. Струи резака — вода под 14–28 МПа (2000–4000 psi) (Ellis &amp; Paul,
     * «Tutorial: Delayed Coking Fundamentals», AIChE 1998), поэтому желоб всегда мокрый.
     */
    @Override
    protected void clientTick() {
        var fx = fx();
        if (fx == null || !runningState()) return;
        if (fx.chance(4)) fx.wisp(fx.at(11.5, 13.6, 6.7), 0.1);
        if (fx.chance(1.5)) fx.puff(fx.at(11.5, 13.6, 6.7), new net.minecraft.world.phys.Vec3(0, 0.06, 0), 1);
        if (fx.chance(6)) fx.splash(fx.at(11.5, 1.2, 2.2), 0.25, 1);
        if (fx.chance(2)) fx.leak(fx.at(11.5, 1.6, 1.2));
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        if (side == Direction.UP) return naphtha;
        return side == Direction.DOWN ? gasOil : feed;
    }

    @Override
    public PanelLayout panelLayout() {
        return RefineryPanels.COKER;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        v[RefineryPanels.T_C] = (int) Math.round(coilC);
        v[RefineryPanels.A] = (int) Math.round(last.coke() * 1000);
        v[RefineryPanels.B] = (int) Math.round(100 * hours / DelayedCoking.FILL_H);
        v[RefineryPanels.C] = otherDrum().ordinal();
        v[RefineryPanels.D] = (int) Math.round(feedLs * 3600);
        v[RefineryPanels.TANK] = (int) Math.round(gasOil.fill() * 1000);
        v[RefineryPanels.FLAGS] = (on ? 1 << RefineryPanels.F_ON : 0) | (running() ? 1 << RefineryPanels.F_RUN : 0)
                | (DelayedCoking.tubeCokingFactor(coilC) > 1 ? 1 << RefineryPanels.F_WARN : 0)
                | (last.softCoke() || h2sKg >= H2S_HOLDUP_KG ? 1 << RefineryPanels.F_AUX : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case RefineryPanels.B_RUN -> use(player);
            case RefineryPanels.B_T_DOWN -> setCoilC(coilC - 5);
            case RefineryPanels.B_T_UP -> setCoilC(coilC + 5);
            default -> {
            }
        }
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        feed.save(tag, "feed");
        naphtha.save(tag, "naphtha");
        gasOil.save(tag, "gas_oil");
        tag.putDouble("coil", coilC);
        tag.putDouble("hours", hours);
        tag.putDouble("drum", drumKg);
        tag.putDouble("waiting", waitingKg);
        tag.putDouble("cut", cutKg);
        tag.putDouble("h2s", h2sKg);
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        feed.load(tag, "feed");
        naphtha.load(tag, "naphtha");
        gasOil.load(tag, "gas_oil");
        if (tag.contains("coil")) coilC = tag.getDouble("coil");
        hours = tag.getDouble("hours");
        drumKg = tag.getDouble("drum");
        waitingKg = tag.getDouble("waiting");
        cutKg = tag.getDouble("cut");
        h2sKg = tag.getDouble("h2s");
    }
}
