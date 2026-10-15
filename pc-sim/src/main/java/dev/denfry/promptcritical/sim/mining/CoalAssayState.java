package dev.denfry.promptcritical.sim.mining;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import java.util.Arrays;

/** Запись {@link CoalAssay} в нейтральное состояние (05 §5.2.8) и сравнение анализов по значению. */
public final class CoalAssayState {
    private CoalAssayState() {
    }

    public static void write(StateWriter out, String key, CoalAssay a) {
        out.beginObject(key);
        out.putDoubles("v", new double[] {a.moistureFrac(), a.ashDryFrac(), a.sulfurDryFrac(), a.pyriticShare(),
                a.volatileDaf(), a.plasticLayerMm(), a.cDaf(), a.hDaf(), a.oDaf(), a.nDaf(), a.strengthM25()}, 11);
        float[] f = a.densityFractions();
        double[] d = new double[f.length];
        for (int i = 0; i < f.length; i++) d[i] = f[i];
        out.putDoubles("fractions", d, d.length);
        out.putString("rank", a.rank().name());
        out.endObject();
    }

    public static CoalAssay read(StateReader in, String key) {
        in.beginObject(key);
        double[] v = in.getDoubles("v");
        double[] d = in.getDoubles("fractions");
        CoalRank rank = CoalRank.valueOf(in.getString("rank"));
        in.endObject();
        float[] f = new float[d.length];
        for (int i = 0; i < d.length; i++) f[i] = (float) d[i];
        return new CoalAssay(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], f, v[10], rank);
    }

    /** Анализы совпадают по значению (у записи {@link CoalAssay} массив фракций сравнивается по ссылке). */
    public static boolean same(CoalAssay a, CoalAssay b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.moistureFrac() == b.moistureFrac() && a.ashDryFrac() == b.ashDryFrac()
                && a.sulfurDryFrac() == b.sulfurDryFrac() && a.pyriticShare() == b.pyriticShare()
                && a.volatileDaf() == b.volatileDaf() && a.plasticLayerMm() == b.plasticLayerMm()
                && a.cDaf() == b.cDaf() && a.hDaf() == b.hDaf() && a.oDaf() == b.oDaf() && a.nDaf() == b.nDaf()
                && Double.compare(a.strengthM25(), b.strengthM25()) == 0 && a.rank() == b.rank()
                && Arrays.equals(a.densityFractions(), b.densityFractions());
    }
}
