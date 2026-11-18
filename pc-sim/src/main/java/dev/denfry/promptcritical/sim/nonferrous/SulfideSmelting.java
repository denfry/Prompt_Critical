package dev.denfry.promptcritical.sim.nonferrous;

/**
 * Стехиометрия передела сульфидных концентратов меди и никеля (08 §8.8.3–8.8.4): флотация, плавка на штейн,
 * конвертирование. Всё считается на килограмм целевого металла.
 *
 * <p>Концентрат — рудный минерал с силикатной пустой породой: халькопирит CuFeS₂ (Cu 34,6 %) и пентландит
 * (Fe,Ni)₉S₈ с Fe:Ni = 1:1 (Ni 34,2 %). Штейн — Cu₂S·FeS (Cu 45–65 %, §8.8.3) или Ni₃S₂·FeS. При плавке
 * часть FeS окисляется: железо уходит в шлак FeO·SiO₂ (флюс — {@code vein_quartz}, 2FeO·SiO₂: 0,537 кг SiO₂ на кг
 * Fe), сера — в SO₂; при конвертировании 2FeS + 3O₂ → 2FeO + 2SO₂, затем Cu₂S + O₂ → 2Cu + SO₂ (§8.8.3). Для меди
 * получается ≈ 2 кг SO₂ на кг Cu (§8.8.3: «≈ 2 т/т Cu»).
 */
public final class SulfideSmelting {
    public static final double M_CU = 0.063546, M_NI = 0.058693, M_FE = 0.055845, M_S = 0.03206, M_O = 0.015999, M_SIO2 = 0.060084,
            M_SO2 = 0.064066, M_O2 = 0.031998, M_SN = 0.11871, M_C = 0.012011;
    /** Шлак 2FeO·SiO₂: кремнезём на кг железа. */
    public static final double SIO2_PER_FE = M_SIO2 / (2 * M_FE);
    /** Кремнезём в пустой породе концентрата, доля [BALANCE: 6–10 % SiO₂ в медном концентрате]. */
    public static final double GANGUE_SIO2 = 0.2;

    /** Линия передела. */
    public enum Metal {
        /**
         * Медь: концентрат Cu 25 % (§8.8.3: 20–30 %), штейн Cu 55 % (45–65 %); минерал CuFeS₂, сульфид штейна Cu₂S.
         */
        COPPER(0.25, 0.55, M_CU, M_FE / M_CU, 2 * M_S / M_CU, 2 * M_CU / (2 * M_CU + M_S)),
        /**
         * Никель: концентрат Ni 10 % [BALANCE: коллективный медно-никелевый 8–12 %], штейн Ni 45 % [BALANCE];
         * минерал (Fe₄.₅Ni₄.₅)S₈, сульфид штейна Ni₃S₂.
         */
        NICKEL(0.10, 0.45, M_NI, M_FE / M_NI, 8 * M_S / (4.5 * M_NI), 3 * M_NI / (3 * M_NI + 2 * M_S));

        /** Содержание металла в концентрате и штейне. */
        public final double concentrateGrade, matteGrade;
        public final double molarMass;
        /** Железо и сера минерала на кг металла. */
        public final double feInMineral, sInMineral;
        /** Доля металла в сульфиде штейна (Cu₂S, Ni₃S₂). */
        public final double metalInSulfide;

        Metal(double concentrateGrade, double matteGrade, double molarMass, double feInMineral, double sInMineral, double metalInSulfide) {
            this.concentrateGrade = concentrateGrade;
            this.matteGrade = matteGrade;
            this.molarMass = molarMass;
            this.feInMineral = feInMineral;
            this.sInMineral = sInMineral;
            this.metalInSulfide = metalInSulfide;
        }

        /** Пустая порода концентрата на кг металла. */
        public double ganguePerMetal() {
            return Math.max(0, 1 / concentrateGrade - 1 - feInMineral - sInMineral);
        }

        /** Сульфид металла в штейне на кг металла. */
        public double sulfidePerMetal() {
            return 1 / metalInSulfide;
        }

        /** FeS штейна на кг металла. */
        public double fesPerMetal() {
            return Math.max(0, 1 / matteGrade - sulfidePerMetal());
        }

        /** Железо, остающееся в штейне, на кг металла. */
        public double feInMatte() {
            return fesPerMetal() * M_FE / (M_FE + M_S);
        }

        /** Сера штейна на кг металла. */
        public double sInMatte() {
            return sulfidePerMetal() - 1 + fesPerMetal() * M_S / (M_FE + M_S);
        }
    }

    private SulfideSmelting() {
    }

    /** Плавка на штейн, на кг металла: SO₂, кислород (S → SO₂, Fe → FeO) и флюс сверх кремнезёма породы, кг. */
    public static double smeltingSo2(Metal m) {
        return Math.max(0, m.sInMineral - m.sInMatte()) * M_SO2 / M_S;
    }

    public static double smeltingO2(Metal m) {
        double fe = Math.max(0, m.feInMineral - m.feInMatte());
        return Math.max(0, m.sInMineral - m.sInMatte()) * M_O2 / M_S + fe * M_O / M_FE;
    }

    public static double smeltingFlux(Metal m) {
        double fe = Math.max(0, m.feInMineral - m.feInMatte());
        return Math.max(0, fe * SIO2_PER_FE - m.ganguePerMetal() * GANGUE_SIO2);
    }

    /** Конвертирование штейна в черновой металл, на кг металла: SO₂, O₂ дутья, флюс. */
    public static double convertingSo2(Metal m) {
        return m.sInMatte() * M_SO2 / M_S;
    }

    public static double convertingO2(Metal m) {
        return m.sInMatte() * M_O2 / M_S + m.feInMatte() * M_O / M_FE;
    }

    public static double convertingFlux(Metal m) {
        return m.feInMatte() * SIO2_PER_FE;
    }

    /** Весь SO₂ от концентрата до черновой меди, кг на кг металла. */
    public static double totalSo2(Metal m) {
        return smeltingSo2(m) + convertingSo2(m);
    }
}
