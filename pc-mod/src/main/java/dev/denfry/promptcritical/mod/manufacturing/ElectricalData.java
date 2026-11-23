package dev.denfry.promptcritical.mod.manufacturing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.sim.manufacturing.CoreStack;
import dev.denfry.promptcritical.sim.manufacturing.ElectricalSheet;
import dev.denfry.promptcritical.sim.manufacturing.InsulationClass;
import dev.denfry.promptcritical.sim.manufacturing.Lamination;
import dev.denfry.promptcritical.sim.manufacturing.LaminationShape;
import dev.denfry.promptcritical.sim.manufacturing.SheetCoating;
import dev.denfry.promptcritical.sim.manufacturing.Stator;

/**
 * Компоненты электротехнических деталей (18 §18.6.8–18.6.9): лист, пачка пластин, пакет статора, намотанный статор.
 * Разбор терпимый: неизвестные имена перечислений и значения вне пределов читаются как ближайшие допустимые, чтобы
 * предмет из другой версии не ронял мир.
 */
public final class ElectricalData {
    /** Шаг квантования заусенца в паспорте пачки, мкм: пачки одного класса износа штампа складываются [BALANCE]. */
    public static final int BURR_STEP_UM = 10;
    /** Пластин в пачке: столько вырубается из одного листа ({@link LaminationShape#perSheet()}). */
    public static final int LAMINATIONS_PER_BUNDLE = LaminationShape.STATOR_112.perSheet();

    private ElectricalData() {
    }

    /** Лист: толщина, мм, кремний, %, наклёп ε и покрытие. */
    public record Sheet(float thicknessMm, float siPct, float strain, String coating) {
        public static final Codec<Sheet> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("t_mm").forGetter(Sheet::thicknessMm),
                Codec.FLOAT.fieldOf("si_pct").forGetter(Sheet::siPct),
                Codec.FLOAT.optionalFieldOf("strain", 0f).forGetter(Sheet::strain),
                Codec.STRING.optionalFieldOf("coating", "NONE").forGetter(Sheet::coating)).apply(i, Sheet::new));

        public static Sheet of(ElectricalSheet s) {
            // толщина квантуется до микрона, наклёп до сотых: листы одной прокатки складываются в стек
            return new Sheet(Math.round(s.thicknessM() * 1e6) / 1000f, Math.round(s.siFrac() * 1000) / 10f,
                    Math.round(s.strain() * 100) / 100f, s.coating().name());
        }

        public ElectricalSheet sheet() {
            double t = Float.isFinite(thicknessMm) && thicknessMm > 0.01f ? thicknessMm / 1000.0 : 1e-5;
            double si = Float.isFinite(siPct) ? Math.max(0, Math.min(10, siPct)) / 100 : 0;
            double e = Float.isFinite(strain) && strain > 0 ? strain : 0;
            return new ElectricalSheet(t, si, e, enumOr(SheetCoating.class, coating, SheetCoating.NONE));
        }
    }

    /** Пачка пластин статора одной формы. */
    public record Bundle(Sheet sheet, String shape, float burrUm, boolean relieved, boolean coatedAfter) {
        public static final Codec<Bundle> CODEC = RecordCodecBuilder.create(i -> i.group(
                Sheet.CODEC.fieldOf("sheet").forGetter(Bundle::sheet),
                Codec.STRING.optionalFieldOf("shape", LaminationShape.STATOR_112.id()).forGetter(Bundle::shape),
                Codec.FLOAT.fieldOf("burr_um").forGetter(Bundle::burrUm),
                Codec.BOOL.optionalFieldOf("relieved", false).forGetter(Bundle::relieved),
                Codec.BOOL.optionalFieldOf("coated_after", false).forGetter(Bundle::coatedAfter)).apply(i, Bundle::new));

        public static Bundle of(Lamination l) {
            return new Bundle(Sheet.of(l.sheet()), l.shape().id(), Math.round(l.burrM() * 1e6 / BURR_STEP_UM) * BURR_STEP_UM,
                    l.edgesRelieved(), l.coatedAfterPunch());
        }

        /** Та же пачка без заусенца: пачки с одинаковым ключом шихтуются в один пакет. */
        public Bundle key() {
            return new Bundle(sheet, shape, 0, relieved, coatedAfter);
        }

        public Lamination lamination() {
            double b = Float.isFinite(burrUm) && burrUm > 0 ? burrUm / 1e6 : 0;
            return new Lamination(sheet.sheet(), LaminationShape.STATOR_112, b, relieved, coatedAfter);
        }
    }

    /** Пакет статора: пачка и число пластин. */
    public record Core(Bundle bundle, int count) {
        public static final Codec<Core> CODEC = RecordCodecBuilder.create(i -> i.group(
                Bundle.CODEC.fieldOf("lamination").forGetter(Core::bundle),
                Codec.INT.fieldOf("count").forGetter(Core::count)).apply(i, Core::new));

        public static Core of(CoreStack c) {
            return new Core(Bundle.of(c.lamination()), c.count());
        }

        public CoreStack stack() {
            return new CoreStack(bundle.lamination(), Math.max(1, count));
        }
    }

    /** Намотанный статор: пакет, класс изоляции, заполнение паза, сопротивление фазы, Ом, тяжесть дефекта изоляции. */
    public record Wound(Core core, String insulation, float fill, float phaseOhm, float flaw) {
        public static final Codec<Wound> CODEC = RecordCodecBuilder.create(i -> i.group(
                Core.CODEC.fieldOf("core").forGetter(Wound::core),
                Codec.STRING.fieldOf("insulation_class").forGetter(Wound::insulation),
                Codec.FLOAT.fieldOf("fill").forGetter(Wound::fill),
                Codec.FLOAT.fieldOf("phase_ohm").forGetter(Wound::phaseOhm),
                Codec.FLOAT.optionalFieldOf("flaw", 0f).forGetter(Wound::flaw)).apply(i, Wound::new));

        public static Wound of(Stator s) {
            return new Wound(Core.of(s.core()), s.insulationClass().name(), Math.round(s.fillFactor() * 1000) / 1000f,
                    Math.round(s.phaseResistanceOhm() * 1000) / 1000f, Math.round(s.flawSeverity() * 100) / 100f);
        }

        public Stator stator() {
            double f = Float.isFinite(fill) && fill > 0 ? fill : 0.4;
            double r = Float.isFinite(phaseOhm) && phaseOhm > 0 ? phaseOhm : 1;
            double s = Float.isFinite(flaw) ? Math.max(0, Math.min(1, flaw)) : 0;
            return new Stator(core.stack(), enumOr(InsulationClass.class, insulation, InsulationClass.A), f, r, s);
        }
    }

    static <E extends Enum<E>> E enumOr(Class<E> type, String name, E fallback) {
        for (E e : type.getEnumConstants()) if (e.name().equals(name)) return e;
        return fallback;
    }
}
