package dev.denfry.promptcritical.sim.gasprocessing;

/**
 * Абсорбционная осушка газа триэтиленгликолем (ТЭГ) с огневой регенерацией (GPSA Engineering Data Book, разд. 20;
 * Campbell, «Gas Conditioning and Processing», т. 2, гл. 18; Бекиров, гл. 4).
 *
 * <p><b>Равновесие.</b> Над раствором ТЭГ парциальное давление воды p_w = γx_w·p_s(T), коэффициент активности воды в
 * ТЭГ γ = 0,65 [допущение: среднее по данным Парриша и др. для 25–200 °C]. Отсюда константа равновесия абсорбера
 * K = γp_s/p и концентрация регенерированного гликоля: в кубе регенератора при атмосферном давлении пар — почти чистая
 * вода, x_w = p_атм/(γp_s(T_куба)); при 204 °C это 98,8 % масс. ТЭГ (GPSA: 98,7 %).
 *
 * <p><b>Абсорбер.</b> Тарельчатый, {@code stages} теоретических тарелок, доля поглощённой воды — по уравнению
 * Кремсера E = (A^{N+1} − A)/(A^{N+1} − 1), A = L/(KV); выход y = y_вх − E(y_вх − Kx_тощ). Циркуляция гликоля —
 * {@code litresPerKg} литров на килограмм воды во входящем газе (GPSA: 17–33 л/кг, обычно 25).
 *
 * <p><b>Регенерация.</b> Тепло куба на килограмм воды: нагрев раствора от абсорбера до куба
 * c_p(ТЭГ) = 2,5 кДж/(кг·К) плюс теплота испарения воды 2,26 МДж/кг с флегмой колонки 25 % (Campbell, гл. 18).
 *
 * @param reboilerK   температура куба регенератора, К (не выше 477 K: ТЭГ разлагается при 206 °C)
 * @param stages      теоретических тарелок абсорбера
 * @param litresPerKg циркуляция ТЭГ на кг поглощаемой воды
 */
public record GlycolDehydration(double reboilerK, double stages, double litresPerKg) {
    public static final double GAMMA = 0.65, TEG_KG_MOL = 0.150_17, TEG_KGM3 = 1125, CP_J_KG_K = 2_500, LATENT_J_KG = 2.26e6;
    public static final double DECOMPOSITION_K = 479.15;
    public static final GlycolDehydration STANDARD = new GlycolDehydration(477, 2, 25);

    /** Мольная доля воды в регенерированном гликоле. */
    public double leanWaterMoleFraction() {
        return Math.min(1, 101_325 / (GAMMA * WaterContent.vaporPressurePa(Math.min(reboilerK, DECOMPOSITION_K))));
    }

    /** Массовая доля ТЭГ в регенерированном гликоле. */
    public double leanTegMassFraction() {
        double x = leanWaterMoleFraction();
        double w = x * WaterContent.WATER_KG_MOL, g = (1 - x) * TEG_KG_MOL;
        return g / (w + g);
    }

    /**
     * Влагосодержание осушенного газа, мг/нм³, и тепло регенерации.
     *
     * @param wetMgSm3 влагосодержание газа на входе
     * @param tK       температура контакта
     * @param pAbsPa   давление абсорбера
     */
    public Result dry(double wetMgSm3, double tK, double pAbsPa) {
        double k = GAMMA * WaterContent.vaporPressurePa(tK) / pAbsPa;
        double yIn = WaterContent.moleFraction(wetMgSm3);
        double waterKgPerSm3 = wetMgSm3 * 1e-6;
        double tegMolPerSm3 = litresPerKg * waterKgPerSm3 * TEG_KGM3 / 1000 / TEG_KG_MOL;
        double a = tegMolPerSm3 / (k * WaterContent.MOL_PER_SM3);
        double e = Math.abs(a - 1) < 1e-9 ? stages / (stages + 1) : (Math.pow(a, stages + 1) - a) / (Math.pow(a, stages + 1) - 1);
        double yEq = k * leanWaterMoleFraction();
        double yOut = Math.max(yEq, yIn - e * (yIn - yEq));
        double outMg = WaterContent.mgPerSm3(yOut);
        double removedKgPerSm3 = (wetMgSm3 - outMg) * 1e-6;
        double tegKgPerSm3 = litresPerKg * waterKgPerSm3 * TEG_KGM3 / 1000;
        double heat = tegKgPerSm3 * CP_J_KG_K * (reboilerK - tK) + removedKgPerSm3 * LATENT_J_KG * 1.25;
        return new Result(outMg, WaterContent.dewPointK(outMg, pAbsPa), removedKgPerSm3, heat);
    }

    /**
     * @param dryMgSm3       влагосодержание осушенного газа
     * @param dewPointK      его точка росы по воде при давлении абсорбера
     * @param waterKgPerSm3  поглощённая вода на нм³ газа
     * @param heatJPerSm3    тепло регенерации на нм³ газа
     */
    public record Result(double dryMgSm3, double dewPointK, double waterKgPerSm3, double heatJPerSm3) {
    }
}
