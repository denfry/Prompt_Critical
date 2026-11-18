package dev.denfry.promptcritical.sim.gasprocessing;
import org.junit.jupiter.api.Test;
class TmpProbeTest {
    @Test void probe() {
        double[] m = GasComponent.pure(GasComponent.C1);
        System.out.printf("muJT CH4 300K 1bar %.4f K/bar%n", PengRobinson.jouleThomson(m, 300, 1e5) * 1e5);
        System.out.printf("CH4 70->35 bar from 293K: %.2f K drop%n", 293 - PengRobinson.throttle(m, 293, 7e6, 3.5e6));
        double[] x = GasComponent.associatedGas();
        System.out.printf("assoc gas gravity %.3f, 7.1->3.5 MPa from 308: T=%.1f K%n", GasComponent.gravity(x), PengRobinson.throttle(x, 308, 7.1e6, 3.5e6));
        System.out.printf("W 1000psia 100F = %.1f lb/MMscf; 40F %.2f%n", WaterContent.saturatedMgPerSm3((100-32)/1.8+273.15, 1000*6894.757)/16.018, WaterContent.saturatedMgPerSm3((40-32)/1.8+273.15, 1000*6894.757)/16.018);
        GlycolDehydration g = GlycolDehydration.STANDARD;
        System.out.printf("lean TEG %.4f%n", g.leanTegMassFraction());
        var d = g.dry(WaterContent.saturatedMgPerSm3(308, 7.1e6), 308, 7.1e6);
        System.out.printf("dry %.1f mg, dew %.1f C, heat %.0f J/Sm3%n", d.dryMgSm3(), d.dewPointK()-273.15, d.heatJPerSm3());
        for (double p : new double[]{2e6, 3.5e6, 5e6}) {
            LowTemperatureSeparation l = new LowTemperatureSeparation(g); l.setLtsPressurePa(p); l.setChillK(253.15);
            var r = l.process(1, 0, 1);
            System.out.printf("LTS %.1f MPa: T %.1f C, V %.4f, ngl %.2e m3/Sm3, ltsDew %.1f C, hyd %.1f C, free %.2e kg/s, work %.0f J/Sm3%n", p/1e6, r.ltsK()-273.15, r.salesSm3s(), r.nglM3s(), r.ltsDewPointK()-273.15, r.hydrateK()-273.15, r.freeWaterKgS(), r.compressorW());
            NglFractionator nf = new NglFractionator(15, 2);
            var s = nf.split(r.nglComposition());
            System.out.printf("   C3 purity %.3f, C4 %.3f, C5+ %.3f, fuel %.3f prop %.3f but %.3f gas %.3f%n", s.propanePurity(), s.butanePurity(), s.gasolinePurity(), NglFractionator.Result.total(s.fuelGas()), NglFractionator.Result.total(s.propane()), NglFractionator.Result.total(s.butane()), NglFractionator.Result.total(s.gasoline()));
        }
    }
}
