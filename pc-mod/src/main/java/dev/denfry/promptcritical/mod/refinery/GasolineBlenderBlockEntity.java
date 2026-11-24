package dev.denfry.promptcritical.mod.refinery;

import dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.uranium.UraniumProcessBlockEntity;
import dev.denfry.promptcritical.sim.refining.GasolineBlending;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * Станция смешения товарного бензина (09 §9.9.14): регулирующие клапаны четырёх компонентов с расходомерами в общий
 * резервуар смешения. Компоненты — прямогонный бензин (LSR, {@code gasoline} атмосферной колонны), риформат,
 * бензин каткрекинга и н-бутан ГФУ, трубами к боковым граням и верху; товарный бензин {@code motor_gasoline} — снизу.
 *
 * <p>Качество смеси в резервуаре — пассивные скаляры: доли компонентов смешиваются по объёму, свойства — по
 * {@link GasolineBlending#actual} (смесительные ОЧ плюс поправки RT-70: рецепт ЛП на границе ОЧМ уходит из
 * спецификации, его поправляют по анализатору). Качества компонентов — эталонные данные §9.9.14
 * ({@link GasolineBlending#LSR} и т. д.) [допущение: жидкости в трубах не несут {@code fuel_quality}; поэтому, например,
 * ОЧИ риформата здесь 98 по §9.9.14, а не честные 81–85 модели риформинга на парафинистом сырье, 00 §0.13 R48].
 *
 * <p>Игроку качество не видно (09 §9.9.12): его показывает поточный анализатор ({@code quality_analyzer},
 * касается станции; дрейф {@value #DRIFT_RON_PER_H} ОЧ за час работы, калибровка — по свежему паспорту
 * лаборатории) или лаборатория по пробе ({@code sample_bottle}), которая и выдаёт паспорт партии. Задача ЛП и
 * рецепт — через CC: {@code getBlendData()} и {@code setValves(...)} периферии {@code pc_tank}.
 */
public final class GasolineBlenderBlockEntity extends UraniumProcessBlockEntity {
    /** Насосы компонентов, Вт. */
    @Override
    protected double ratedPowerW() {
        return 55e3;
    }

    public static final int FEED_MB = 8_000, BLEND_MB = 16_000;
    /** Клапан компонента — до 6 м³/ч [BALANCE: больше, чем дают установки модели]. */
    public static final double VALVE_MAX_M3H = 6, VALVE_STEP_M3H = 0.1, DRIFT_RON_PER_H = 0.3;
    public static final List<GasolineBlending.Component> COMPONENTS = List.of(GasolineBlending.LSR, GasolineBlending.REFORMATE,
            GasolineBlending.FCC_GASOLINE, GasolineBlending.BUTANE);
    public static final List<GasolineBlending.Grade> GRADES = List.of(GasolineBlending.NORMAL80, GasolineBlending.AI92);

    private final FeedTank[] feeds = {new FeedTank(PcFluids.GASOLINE.source.get(), FEED_MB),
            new FeedTank(PcFluids.REFORMATE.source.get(), FEED_MB), new FeedTank(PcFluids.FCC_GASOLINE.source.get(), FEED_MB),
            new FeedTank(PcFluids.BUTANE.source.get(), FEED_MB)};
    private final IFluidHandler inlet = new PyrolysisFurnaceBlockEntity.MultiInlet(feeds);
    private final ProductTank blend = new ProductTank(PcFluids.MOTOR_GASOLINE.source.get(), BLEND_MB);
    private final double[] valves = new double[COMPONENTS.size()], fractions = new double[COMPONENTS.size()];
    private int selected, grade;
    private double rateM3h, sinceCalibrationH, blendedSinceCertL;
    private boolean analyzer;
    /** Паспорт партии: измеренное качество, время анализа (−1 — нет). */
    @Nullable
    private RefineryComponents.FuelQuality passport;
    private long certifiedAt = -1, calibratedAt = -1;
    private String passportGrade = "";

    public GasolineBlenderBlockEntity(BlockPos pos, BlockState state) {
        super(RefineryBlockEntities.GASOLINE_BLENDER.get(), pos, state, 0, 0, s -> false);
    }

    public FeedTank feed(int i) {
        return feeds[i];
    }

    public ProductTank blend() {
        return blend;
    }

    public double[] valves() {
        return valves.clone();
    }

    public GasolineBlending.Grade grade() {
        return GRADES.get(grade);
    }

    public boolean analyzer() {
        return analyzer;
    }

    public long certifiedAt() {
        return certifiedAt;
    }

    @Nullable
    public RefineryComponents.FuelQuality passport() {
        return passport;
    }

    /** Паспорт действует: смесь не доливали после анализа. */
    public boolean certified() {
        return passport != null && blendedSinceCertL < 1;
    }

    public void setValve(int i, double m3h) {
        valves[i] = Math.clamp(Math.round(m3h * 1000) / 1000.0, 0, VALVE_MAX_M3H);
        setChanged();
    }

    public void setGrade(int g) {
        grade = Math.floorMod(g, GRADES.size());
        setChanged();
    }

    /** Истинное качество смеси в резервуаре (скрыто от игрока); пустой резервуар — {@code null}. */
    @Nullable
    public GasolineBlending.Properties blendProperties() {
        if (blend.amount() < 1) return null;
        GasolineBlending.Properties p = GasolineBlending.actual(COMPONENTS, fractions);
        return p.volume() > 0 ? p : null;
    }

    /** Показание анализатора: истинное плюс дрейф с последней калибровки; без анализатора — {@code null}. */
    @Nullable
    public RefineryComponents.FuelQuality analyzerReading() {
        GasolineBlending.Properties p = blendProperties();
        if (!analyzer || p == null) return null;
        double d = DRIFT_RON_PER_H * sinceCalibrationH;
        RefineryComponents.FuelQuality q = RefineryComponents.FuelQuality.of(p);
        return new RefineryComponents.FuelQuality(q.ron() + d, q.mon() + d, q.rvpKpa(), q.sulfurPpm(), q.benzenePct(), q.aromaticsPct(),
                q.olefinsPct(), q.density());
    }

    /** Лаборатория записывает паспорт партии (09 §9.9.13). */
    public void certify(RefineryComponents.FuelQuality measured, long gameTime) {
        passport = measured;
        certifiedAt = gameTime;
        passportGrade = grade().id();
        blendedSinceCertL = 0;
        setChanged();
    }

    /** Калибровка анализатора по паспорту, свежее последней калибровки. */
    public boolean calibrate() {
        if (passport == null || certifiedAt <= calibratedAt) return false;
        calibratedAt = certifiedAt;
        sinceCalibrationH = 0;
        setChanged();
        return true;
    }

    private boolean findAnalyzer(ServerLevel level) {
        for (Direction d : Direction.values())
            if (level.getBlockState(worldPosition.relative(d)).is(PcBlocks.QUALITY_ANALYZER.get())) return true;
        return false;
    }

    @Override
    protected void process(ServerLevel level, double dt) {
        if (level.getGameTime() % 20 == 0 || !analyzer) analyzer = findAnalyzer(level);
        if (analyzer) sinceCalibrationH += dt / 3600;
        rateM3h = 0;
        if (!on) return;
        double total = 0;
        double[] want = new double[valves.length];
        for (int i = 0; i < valves.length; i++) {
            want[i] = valves[i] * 1000 / 3600 * dt;
            total += want[i];
        }
        if (total <= 0) return;
        // регулятор соотношения: не хватает одного компонента — пропорционально прикрываются все
        double s = Math.min(1, blend.room() / total);
        for (int i = 0; i < valves.length; i++) if (want[i] > 0) s = Math.min(s, feeds[i].amount() / want[i]);
        if (s <= 0) return;
        double before = blend.amount(), added = 0;
        for (int i = 0; i < valves.length; i++) {
            double l = feeds[i].take(want[i] * s);
            fractions[i] = fractions[i] * before + l;
            added += l;
        }
        double after = before + added;
        for (int i = 0; i < fractions.length; i++) fractions[i] = after > 0 ? fractions[i] / after : 0;
        blend.add(added);
        blendedSinceCertL += added;
        rateM3h = added / 1000 / dt * 3600;
    }

    @Override
    protected boolean running() {
        return rateM3h > 0;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return side == Direction.DOWN ? blend : inlet;
    }

    // ------------------------------------------------------------------ CC: задача ЛП (09 §9.9.14, 03b)

    /** {@code getBlendData()}: компоненты (эталонные качества, наличие — по клапану), марка, рецепт, анализатор, паспорт. */
    public Map<String, Object> blendData() {
        List<Map<String, Object>> comps = new ArrayList<>();
        for (int i = 0; i < COMPONENTS.size(); i++) {
            GasolineBlending.Component c = COMPONENTS.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.id());
            m.put("ron", c.ron());
            m.put("mon", c.mon());
            m.put("rvp", c.rvpKpa());
            m.put("sulfur", c.sulfurPpm());
            m.put("benzene", c.benzenePct());
            m.put("aromatics", c.aromaticsPct());
            m.put("olefins", c.olefinsPct());
            m.put("density", c.density());
            m.put("available", VALVE_MAX_M3H);
            m.put("stockL", feeds[i].amount());
            m.put("cost", c.cost());
            m.put("valve", valves[i]);
            comps.add(m);
        }
        GasolineBlending.Grade g = grade();
        Map<String, Object> gr = new LinkedHashMap<>();
        gr.put("id", g.id());
        gr.put("ron", g.ron());
        gr.put("mon", g.mon());
        gr.put("rvpMin", g.rvpMinKpa());
        gr.put("rvpMax", g.rvpMaxKpa());
        gr.put("sulfurMax", g.sulfurMaxPpm());
        gr.put("benzeneMax", g.benzeneMaxPct());
        gr.put("aromaticsMax", g.aromaticsMaxPct());
        gr.put("olefinsMax", g.olefinsMaxPct());
        gr.put("densityMin", g.densityMin());
        gr.put("densityMax", g.densityMax());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("components", comps);
        out.put("grade", gr);
        out.put("tankL", blend.amount());
        RefineryComponents.FuelQuality a = analyzerReading();
        if (a != null) out.put("analyzer", quality(a));
        if (passport != null) {
            Map<String, Object> p = quality(passport);
            p.put("certifiedAt", certifiedAt);
            p.put("grade", passportGrade);
            p.put("valid", certified());
            out.put("passport", p);
        }
        return out;
    }

    private static Map<String, Object> quality(RefineryComponents.FuelQuality q) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ron", q.ron());
        m.put("mon", q.mon());
        m.put("rvp", q.rvpKpa());
        m.put("sulfur", q.sulfurPpm());
        m.put("benzene", q.benzenePct());
        m.put("aromatics", q.aromaticsPct());
        m.put("olefins", q.olefinsPct());
        m.put("density", q.density());
        return m;
    }

    // ------------------------------------------------------------------ щит

    @Override
    public PanelLayout panelLayout() {
        return ConversionPanels.BLENDER;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        RefineryComponents.FuelQuality a = analyzerReading();
        if (a != null) {
            v[ConversionPanels.BL_RON_X10] = (int) Math.round(a.ron() * 10);
            v[ConversionPanels.BL_MON_X10] = (int) Math.round(a.mon() * 10);
            v[ConversionPanels.BL_RVP_X10] = (int) Math.round(a.rvpKpa() * 10);
        }
        v[ConversionPanels.BL_TANK] = (int) Math.round(blend.fill() * 1000);
        for (int i = 0; i < valves.length; i++) v[ConversionPanels.BL_V0 + i] = (int) Math.round(valves[i] * 100);
        v[ConversionPanels.BL_SEL] = selected;
        v[ConversionPanels.BL_GRADE] = grade;
        v[ConversionPanels.BL_RATE_X100] = (int) Math.round(rateM3h * 100);
        v[ConversionPanels.BL_PASS_RON_X10] = passport == null ? 0 : (int) Math.round(passport.ron() * 10);
        boolean passOk = passport != null && passport.onSpec(grade());
        v[ConversionPanels.BL_FLAGS] = (on ? 1 << ConversionPanels.F_ON : 0) | (running() ? 1 << ConversionPanels.F_RUN : 0)
                | (analyzer ? 1 << ConversionPanels.F_AUX : 0) | (a != null && !a.onSpec(grade()) ? 1 << ConversionPanels.F_WARN : 0)
                | (certified() && passOk ? 1 << ConversionPanels.F_LOW : 0) | (certified() && !passOk ? 1 << ConversionPanels.F_ALARM : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case ConversionPanels.B_RUN -> use(player);
            case ConversionPanels.B_DOWN -> setValve(selected, valves[selected] - VALVE_STEP_M3H);
            case ConversionPanels.B_UP -> setValve(selected, valves[selected] + VALVE_STEP_M3H);
            case ConversionPanels.B_DOWN2 -> selected = Math.floorMod(selected + 1, valves.length);
            case ConversionPanels.B_UP2 -> setGrade(grade + 1);
            case ConversionPanels.B_ACTION -> calibrate();
            default -> {
            }
        }
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        for (int i = 0; i < feeds.length; i++) {
            feeds[i].save(tag, "feed" + i);
            tag.putDouble("valve" + i, valves[i]);
            tag.putDouble("x" + i, fractions[i]);
        }
        blend.save(tag, "blend");
        tag.putInt("selected", selected);
        tag.putInt("grade", grade);
        tag.putDouble("drift_h", sinceCalibrationH);
        tag.putDouble("since_cert", blendedSinceCertL);
        tag.putLong("certified_at", certifiedAt);
        tag.putLong("calibrated_at", calibratedAt);
        if (passport != null)
            RefineryComponents.FuelQuality.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, passport).result()
                    .ifPresent(t -> tag.put("passport", t));
        tag.putString("passport_grade", passportGrade);
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        for (int i = 0; i < feeds.length; i++) {
            feeds[i].load(tag, "feed" + i);
            valves[i] = tag.getDouble("valve" + i);
            fractions[i] = tag.getDouble("x" + i);
        }
        blend.load(tag, "blend");
        selected = Math.floorMod(tag.getInt("selected"), valves.length);
        grade = Math.floorMod(tag.getInt("grade"), GRADES.size());
        sinceCalibrationH = tag.getDouble("drift_h");
        blendedSinceCertL = tag.getDouble("since_cert");
        certifiedAt = tag.contains("certified_at") ? tag.getLong("certified_at") : -1;
        calibratedAt = tag.contains("calibrated_at") ? tag.getLong("calibrated_at") : -1;
        passport = tag.contains("passport")
                ? RefineryComponents.FuelQuality.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("passport")).result().orElse(null)
                : null;
        passportGrade = tag.getString("passport_grade");
    }
}
