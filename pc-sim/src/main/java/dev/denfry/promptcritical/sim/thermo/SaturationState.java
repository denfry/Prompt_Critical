package dev.denfry.promptcritical.sim.thermo;

/**
 * Полный набор свойств на линии насыщения при давлении {@code pressure} (01 §1.3.2(а)). Единицы — СИ.
 *
 * <p>Как и {@link ThermoState}, это переиспользуемый буфер: горячий цикл заполняет его через
 * {@link #set(double, double[])} из {@link WaterProperties#saturationP(double, double[])}, без
 * аллокаций (01 §1.12.3, T-1.12-02). {@link WaterProperties#saturationP(double)} отдаёт новый объект.
 */
public final class SaturationState {
    private double pressure, tSat, vF, vG, hF, hG, uF, uG, sF, sG, cpF, cpG, muF, muG, kF, kG, sigma, dTsatdP;

    /** Пустой буфер (NaN) для {@link #set}. */
    public SaturationState() {
        pressure = Double.NaN;
    }

    public SaturationState(double pressure, double tSat, double vF, double vG,
            double hF, double hG, double uF, double uG, double sF, double sG,
            double cpF, double cpG, double muF, double muG, double kF, double kG,
            double sigma, double dTsatdP) {
        this.pressure = pressure;
        this.tSat = tSat;
        this.vF = vF;
        this.vG = vG;
        this.hF = hF;
        this.hG = hG;
        this.uF = uF;
        this.uG = uG;
        this.sF = sF;
        this.sG = sG;
        this.cpF = cpF;
        this.cpG = cpG;
        this.muF = muF;
        this.muG = muG;
        this.kF = kF;
        this.kG = kG;
        this.sigma = sigma;
        this.dTsatdP = dTsatdP;
    }

    /** Заполняет из 17 величин {@link WaterProperties#saturationP(double, double[])} при давлении p. */
    public SaturationState set(double p, double[] s) {
        pressure = p;
        tSat = s[0];
        vF = s[1];
        vG = s[2];
        hF = s[3];
        hG = s[4];
        uF = s[5];
        uG = s[6];
        sF = s[7];
        sG = s[8];
        cpF = s[9];
        cpG = s[10];
        muF = s[11];
        muG = s[12];
        kF = s[13];
        kG = s[14];
        sigma = s[15];
        dTsatdP = s[16];
        return this;
    }

    public double pressure() { return pressure; }
    public double tSat() { return tSat; }
    public double vF() { return vF; }
    public double vG() { return vG; }
    public double hF() { return hF; }
    public double hG() { return hG; }
    public double uF() { return uF; }
    public double uG() { return uG; }
    public double sF() { return sF; }
    public double sG() { return sG; }
    public double cpF() { return cpF; }
    public double cpG() { return cpG; }
    public double muF() { return muF; }
    public double muG() { return muG; }
    public double kF() { return kF; }
    public double kG() { return kG; }
    public double sigma() { return sigma; }
    public double dTsatdP() { return dTsatdP; }

    public double rhoF() {
        return 1.0 / vF;
    }

    public double rhoG() {
        return 1.0 / vG;
    }

    /** Теплота парообразования h'' − h', Дж/кг. */
    public double latentHeat() {
        return hG - hF;
    }

    @Override
    public String toString() {
        return "SaturationState[p=" + pressure + ", tSat=" + tSat + "]";
    }
}
