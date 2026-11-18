package dev.denfry.promptcritical.sim.drilling;

import java.util.ArrayList;
import java.util.List;

/**
 * Вертикальная скважина при бурении и управлении ею: бурильная колонна и затрубье как столбы флюидов, приёмные
 * ёмкости, превентор и дроссель на выкиде, приток из вскрытого пласта.
 *
 * <p><b>Столбы.</b> Колонна заполнена раствором (поршневое вытеснение: насос подаёт из ёмкостей в трубы, у долота
 * раствор переходит в затрубье). Затрубье — последовательность пачек снизу вверх: раствор, жидкость пласта
 * (нефть, вода) или газ; объём газовой пачки считается по её давлению из уравнения состояния реального газа
 * ({@link NaturalGas}), поэтому газ расширяется при подъёме (Bourgoyne et al., §4.11; IADC WellSharp).
 *
 * <p><b>Давления.</b> Давление на забое — давление на устье затрубья плюс гидростатика пачек плюс, при циркуляции,
 * потери на трение в затрубье ({@link DrillingHydraulics}). Давление на стояке — то же минус гидростатика колонны
 * плюс потери в трубах и на долоте. При открытом превенторе давление на устье равно нулю и лишний объём уходит в
 * ёмкости (рост уровня — главный признак притока). При закрытом и остановленном насосе давление на устье таково,
 * что содержимое помещается в ствол с учётом сжимаемости раствора и упругости ствола (эффективная сжимаемость
 * 10⁻⁹ 1/Па): так устанавливаются SIDPP и SICP, а всплытие газа в закрытой скважине (0,085 м/с, около 1000 фут/ч —
 * правило IADC) поднимает давление на устье и забое. При циркуляции через штуцер давление на устье задаёт
 * дроссель: автоматический режим держит давление на стояке по листу глушения ({@link DrillersMethod}) или,
 * без него, постоянное давление в затрубье, с постоянной времени исполнительного механизма 10 с.
 *
 * <p><b>Приток.</b> Если давление на забое у вскрытого интервала ниже пластового, пласт отдаёт флюид по
 * радиальному закону Дарси для псевдоустановившегося режима q = 2πkh·Δp / (μ(ln(r_e/r_w) − 0,75)) (Dake,
 * «Fundamentals of Reservoir Engineering», §6.4), h — вскрытая мощность. В закрытой скважине объём притока за шаг
 * ограничен точным решением линейной задачи «приток — накопление»: иначе высокопроницаемый пласт раскачал бы
 * явную схему.
 *
 * <p><b>Пределы.</b> Давление у башмака выше давления гидроразрыва ломает породу: давление на устье не растёт
 * выше этого, раствор уходит в пласт (поглощение). Давление на устье выше рабочего давления превентора
 * 34,5 МПа (5000 psi, API 16A) или выход пластового флюида на устье при открытом превенторе и продолжающемся
 * притоке — открытый фонтан; дальше скважина только фонтанирует.
 *
 * <p><b>Поглощение</b> (09 §9.5.10): когда давление на забое выше давления гидроразрыва открытого ствола (та же
 * эквивалентная плотность, что у башмака, на глубине забоя), раствор уходит в пласт со дна затрубья
 * q = C·(p_заб − p_грп), C = {@link #LOSS_COEFF_M3_S_PA}; наполнитель в растворе (тег
 * {@code c:loss_circulation_material}) снижает C как 1/(1 + c/{@link #LCM_HALF_KGM3}). Ствол доливается из ёмкостей,
 * поэтому поглощение видно по падению выхода и уровня в ёмкостях [допущение: одна вскрытая залежь, перетока
 * «поглощение внизу — приток вверху» нет; трещиноватые известняки в разрезе не встречаются].
 *
 * <p>Модель не описывает спуско-подъёмные операции, износ долота, искривление ствола и многофазное скольжение при
 * циркуляции; при доливе раствор в ёмкостях готовится той же плотности без учёта материалов [допущение].
 */
public final class Well {
    public static final double G = 9.806_65, P_ATM = 101_325;
    public static final double MAX_SUBSTEP_S = 1.0;
    public static final double SLIP_VELOCITY_MS = 0.085;
    public static final double EFFECTIVE_COMPRESSIBILITY_PER_PA = 1.0e-9;
    public static final double BOP_RATED_PA = 34.47e6;
    /** Регулируемый штуцер 2″: проходное сечение полностью открытого, коэффициент расхода. */
    public static final double CHOKE_AREA_M2 = Math.PI / 4 * 0.0508 * 0.0508, CHOKE_CD = 0.85;
    public static final double CHOKE_TAU_S = 10;
    /** Сигнал о приросте объёма в ёмкостях: 10 баррелей (IADC). */
    public static final double PIT_ALARM_M3 = 1.6;
    public static final double DRAINAGE_RADIUS_M = 300;
    public static final double STEEL_KGM3 = 7_850;
    /** Талевый блок с крюком и вертлюгом, кг. */
    public static final double BLOCK_KG = 15_000;
    /** Нагрузка на долото — не больше 85 % веса УБТ в растворе (нейтральная точка в УБТ, API RP 7G). */
    public static final double NEUTRAL_POINT_FACTOR = 0.85;
    /** Момент на долоте T = μWd/3 (Pessier, Fear, SPE 24584) и сопротивление вращению колонны, Н·м/м. */
    public static final double BIT_FRICTION = 0.25, STRING_TORQUE_NM_PER_M = 1.5;
    public static final double PUMP_EFFICIENCY = 0.85;
    /** Долив ёмкостей: ниже этой доли объёма — до второй; рабочий уровень оставляет запас под приток. */
    public static final double MAKEUP_BELOW = 0.4, MAKEUP_TO = 0.6;
    /** Коэффициент поглощения, м³/(с·Па): 10 л/с на 1 МПа сверх давления гидроразрыва [BALANCE]. */
    public static final double LOSS_COEFF_M3_S_PA = 1.0e-8;
    /** Концентрация наполнителя, вдвое снижающая поглощение, кг/м³ [BALANCE: кольматирующая пачка 40–90 кг/м³]. */
    public static final double LCM_HALF_KGM3 = 30;
    private static final double EPS = 1e-9, MERGE_KGM3 = 0.5;
    private static final double PS_MAX = 2.0e8;

    public enum Kind { MUD, LIQUID, GAS }

    /** Пачка: раствор или жидкость пласта — объём, м³, и плотность; газ — количество вещества, моль. */
    static final class Slug {
        final Kind kind;
        double amount;
        final double density;

        Slug(Kind kind, double amount, double density) {
            this.kind = kind;
            this.amount = amount;
            this.density = density;
        }

        boolean sameFluid(Kind k, double rho) {
            return kind == k && (k == Kind.GAS || Math.abs(density - rho) < MERGE_KGM3);
        }
    }

    private final WellGeometry geo;
    private final Prospect prospect;
    /** Колонна: сверху вниз, только раствор. */
    private final List<Slug> string = new ArrayList<>();
    /** Затрубье: снизу вверх. */
    private final List<Slug> annulus = new ArrayList<>();

    private double holeDepthM;
    private double pitM3, pitKgM3, pitCapacityM3, pitReferenceM3;
    private boolean bopClosed, blowout, bopFailed, shoeFractured, divertReturns;
    private double reserveM3;
    private double pitLcmKgM3, lossM3s, stepLossM3;
    private double bopRatedPa = BOP_RATED_PA;
    private double casingPa, heldCasingPa = Double.NaN, dppTargetPa = Double.NaN;

    // результаты шага
    private double bhpPa, dppPa, ecdKgM3, shoeLastPa, influxM3s, flowOutM3s, ropMs, wobN, rpm, pumpM3s, hookLoadN,
            torqueNm, blowoutM3s, gasOutMolS;
    private double totalInfluxM3, lostM3, madeUpM3, spilledM3, releasedStdM3, ventedMol;

    // накопители подшага
    private double stepOutLiquidM3, stepGasOutMol;
    private boolean stepInfluxAtSurface;

    // результаты прохода по затрубью
    private double walkBhp, walkShoe, walkGasComp;

    public Well(WellGeometry geo, Prospect prospect, double depthM, double mudKgM3, double pitCapacityM3) {
        if (!(depthM > geo.shoeDepthM() && mudKgM3 > 900 && pitCapacityM3 > 0))
            throw new IllegalArgumentException("well starts below the shoe with mud in pits");
        this.geo = geo;
        this.prospect = prospect;
        this.holeDepthM = depthM;
        this.pitKgM3 = mudKgM3;
        this.pitCapacityM3 = pitCapacityM3;
        this.pitM3 = MAKEUP_TO * pitCapacityM3;
        this.pitReferenceM3 = pitM3;
        string.add(new Slug(Kind.MUD, geo.innerAreaM2() * depthM, mudKgM3));
        annulus.add(new Slug(Kind.MUD, geo.annulusAreaM2() * depthM, mudKgM3));
        outputs(0);
    }

    // --- управление ---

    public void setBopClosed(boolean closed) {
        if (blowout && !closed) return;
        bopClosed = closed && !bopFailed;
        heldCasingPa = Double.NaN;
    }

    /** Уставка давления на стояке для автоматического дросселя, Па; NaN — держать давление в затрубье. */
    public void setDrillPipeTargetPa(double pa) {
        dppTargetPa = pa;
    }

    /** Утяжелить раствор в ёмкостях баритом, кг (объём барита не считается приростом). */
    public void addBarite(double kg) {
        if (kg <= 0) return;
        double v = kg / KillSheet.BARITE_KGM3;
        pitKgM3 = (pitKgM3 * pitM3 + kg) / (pitM3 + v);
        pitM3 += v;
        pitReferenceM3 += v;
    }

    /**
     * Возврат из скважины — не в рабочую ёмкость, а в запасную: при глушении старый раствор не должен разбавлять
     * раствор глушения во всасывающей ёмкости (API RP 59, §6.4).
     */
    public void setDivertReturns(boolean divert) {
        divertReturns = divert;
    }

    /**
     * Стравить через штуцер закрытой скважины при остановленном насосе объём {@code m3}: так снимают захваченное
     * давление после глушения. Если пласт не задавлен, давление восстановится притоком — так его и отличают.
     */
    public void bleed(double m3) {
        if (!bopClosed || pumpM3s > 0 || blowout || m3 <= 0) return;
        popTop(m3, casingPa, false);
    }

    /** Раствор, ушедший в запасную ёмкость, м³. */
    public double reserveM3() {
        return reserveM3;
    }

    public void setPitCapacityM3(double m3) {
        pitCapacityM3 = Math.max(1, m3);
    }

    /** Обнулить счётчик прироста (бурильщик перед работой отмечает уровень). */
    public void zeroPitTotalizer() {
        pitReferenceM3 = pitM3 + spilledM3;
    }

    /**
     * Шаг физического времени {@code dt}, с: подача насосов, м³/с; ротор, об/мин; уставка нагрузки на долото, Н
     * (ограничена весом УБТ).
     */
    public void step(double dt, double pumpM3s, double rotaryRpm, double wobSetN) {
        if (!(dt > 0)) return;
        int n = (int) Math.ceil(dt / MAX_SUBSTEP_S);
        double h = dt / n;
        stepOutLiquidM3 = stepGasOutMol = stepLossM3 = 0;
        stepInfluxAtSurface = false;
        for (int i = 0; i < n; i++) substep(h, Math.max(0, pumpM3s), Math.max(0, rotaryRpm), Math.max(0, wobSetN));
        flowOutM3s = stepOutLiquidM3 / dt;
        lossM3s = stepLossM3 / dt;
        gasOutMolS = stepGasOutMol / dt;
        outputs(blowout ? 0 : pumpM3s);
    }

    private void substep(double dt, double q, double rotaryRpm, double wobSet) {
        if (blowout) {
            flowing(dt);
            return;
        }
        if (pitM3 <= 0) q = 0;
        pumpM3s = q;
        rpm = rotaryRpm;
        if (q > 0) circulate(q * dt);
        else heldCasingPa = Double.NaN;
        boolean drilling = q > 0 && rotaryRpm > 0 && wobSet > 0 && !bopClosed;
        wobN = drilling ? Math.min(wobSet, maxWobN()) : 0;
        ropMs = drilling ? rop() : 0;
        if (ropMs > 0) deepen(ropMs * dt);
        // приток при давлении на забое прошлого шага
        walk(casingPa);
        double bhp = walkBhp + annulusFrictionPa(q);
        double rate = influxRate(bhp);
        double v = rate * dt;
        if (bopClosed && rate > 0) {
            // закрытая скважина: точное решение dP/dt = PI(Pₚ − P)/C на шаге
            double pi = rate / Math.max(1e-3, porePressurePa() - pwfAtPay(bhp));
            double c = EFFECTIVE_COMPRESSIBILITY_PER_PA * (annulusCapacityM3() + stringCapacityM3()) + walkGasComp;
            v = (rate / pi) * c * -Math.expm1(-pi * dt / c);
        }
        influxM3s = v / dt;
        if (v > 0) addInflux(v, bhp);
        lose(dt, bhp);
        if (q <= 0) migrate(dt);
        reconcile(dt, q);
        if (blowout) return;
        if (!bopClosed && stepInfluxAtSurface && influxM3s > 0) blowout = true;
        pits();
    }

    // --- перенос ---

    private double circulate(double dv) {
        double take = Math.min(dv, pitM3);
        if (take <= 0) return 0;
        pitM3 -= take;
        pushTopOfString(take, pitKgM3);
        double rem = take;
        while (rem > EPS && !string.isEmpty()) {
            Slug s = string.getLast();
            double t = Math.min(s.amount, rem);
            s.amount -= t;
            rem -= t;
            pushBottom(Kind.MUD, t, s.density);
            if (s.amount <= EPS) string.removeLast();
        }
        return take;
    }

    private void deepen(double dz) {
        double vin = geo.innerAreaM2() * dz, vann = geo.annulusAreaM2() * dz;
        holeDepthM += dz;
        Slug bottom = annulus.isEmpty() ? null : annulus.getFirst();
        pushBottom(Kind.MUD, vann, bottom != null && bottom.kind == Kind.MUD ? bottom.density : pitKgM3);
        pushTopOfString(vin, pitKgM3);
        // новый ствол заполняется из ёмкостей: ожидаемый уровень снижается так же, прирост это не трогает
        pitM3 -= vin + vann;
        pitReferenceM3 -= vin + vann;
    }

    private void pushTopOfString(double v, double rho) {
        if (v <= 0) return;
        if (!string.isEmpty() && string.getFirst().sameFluid(Kind.MUD, rho)) {
            Slug s = string.getFirst();
            string.set(0, new Slug(Kind.MUD, s.amount + v, (s.density * s.amount + rho * v) / (s.amount + v)));
        } else {
            string.addFirst(new Slug(Kind.MUD, v, rho));
        }
    }

    private void pushBottom(Kind kind, double amount, double rho) {
        if (amount <= 0) return;
        if (!annulus.isEmpty() && annulus.getFirst().sameFluid(kind, rho)) {
            Slug s = annulus.getFirst();
            annulus.set(0, kind == Kind.GAS ? new Slug(kind, s.amount + amount, Double.NaN)
                    : new Slug(kind, s.amount + amount, (s.density * s.amount + rho * amount) / (s.amount + amount)));
        } else {
            annulus.addFirst(new Slug(kind, amount, rho));
        }
    }

    private void addInflux(double volumeM3, double bhp) {
        totalInfluxM3 += volumeM3;
        FormationFluid f = influxFluid();
        if (f == FormationFluid.GAS) {
            pushBottom(Kind.GAS, NaturalGas.moles(volumeM3, bhp + P_ATM, Trap.temperatureK(holeDepthM)), Double.NaN);
        } else {
            pushBottom(Kind.LIQUID, volumeM3, f.liquidDensityKgM3);
        }
    }

    /** Всплытие газа в неподвижном растворе: жидкость над пачкой газа перетекает под неё. */
    private void migrate(double dt) {
        double dv = geo.annulusAreaM2() * SLIP_VELOCITY_MS * dt;
        for (int i = annulus.size() - 2; i >= 0; i--) {
            Slug gas = annulus.get(i);
            Slug above = annulus.get(i + 1);
            if (gas.kind != Kind.GAS || above.kind == Kind.GAS) continue;
            double take = Math.min(dv, above.amount);
            above.amount -= take;
            if (above.amount <= EPS) annulus.remove(i + 1);
            if (i > 0 && annulus.get(i - 1).sameFluid(above.kind, above.density)) {
                Slug below = annulus.get(i - 1);
                below.amount += take;
            } else {
                annulus.add(i, new Slug(above.kind, take, above.density));
            }
        }
    }

    /** Поглощение в открытый ствол у забоя при давлении выше давления гидроразрыва. */
    private void lose(double dt, double bhp) {
        double excess = bhp - openHoleFracturePa();
        if (excess <= 0 || annulus.isEmpty()) return;
        double v = lossCoefficient() * excess * dt;
        Slug bottom = annulus.getFirst();
        if (bottom.kind != Kind.MUD) return;
        v = Math.min(v, bottom.amount - EPS);
        if (v <= 0) return;
        bottom.amount -= v;
        lostM3 += v;
        stepLossM3 += v;
    }

    /** Давление гидроразрыва открытого ствола у забоя, Па. */
    public double openHoleFracturePa() {
        return geo.shoeFractureKgM3() * G * holeDepthM;
    }

    /** Коэффициент поглощения с учётом наполнителя в растворе, м³/(с·Па). */
    public double lossCoefficient() {
        return LOSS_COEFF_M3_S_PA / (1 + pitLcmKgM3 / LCM_HALF_KGM3);
    }

    /** Ввести наполнитель поглощения в ёмкости, кг (объём не учитывается). */
    public void addLcm(double kg) {
        if (kg <= 0 || pitM3 <= 0) return;
        pitLcmKgM3 += kg / pitM3;
    }

    public double pitLcmKgM3() {
        return pitLcmKgM3;
    }

    public void setPitLcmKgM3(double c) {
        pitLcmKgM3 = Math.max(0, c);
    }

    /** Поглощение за последний шаг, м³/с. */
    public double lossM3s() {
        return lossM3s;
    }

    /**
     * Репрессия на вскрытый пласт: давление в стволе против середины вскрытого интервала минус пластовое, Па; 0 —
     * пласт не вскрыт.
     */
    public double overbalancePa() {
        double pp = porePressurePa();
        return Double.isNaN(pp) || blowout ? 0 : pwfAtPay(bhpPa) - pp;
    }

    /** Длина УБТ против вскрытого коллектора, м (долото на забое). */
    public double collarsAcrossPayM() {
        return Math.min(exposedPayM(), bhaInHoleM());
    }

    /**
     * Давление удержания закрытого превентора, Па: стек задаёт по самому прочному загерметизировавшему элементу
     * (09 §9.5.7, износ уплотнителя кольцевого).
     */
    public void setBopRatedPa(double pa) {
        bopRatedPa = Math.max(0, pa);
    }

    public double bopRatedPa() {
        return bopRatedPa;
    }

    // --- давление и объём ---

    /** Проход сверху вниз при давлении на устье {@code ps}: объём содержимого, давление внизу, у башмака. */
    private double walk(double ps) {
        double a = geo.annulusAreaM2(), shoe = geo.shoeDepthM();
        double z = 0, p = ps, vol = 0, gasComp = 0;
        double shoeP = Double.NaN;
        for (int i = annulus.size() - 1; i >= 0; i--) {
            Slug s = annulus.get(i);
            double v, rho;
            if (s.kind == Kind.GAS) {
                double t = Trap.temperatureK(z), pabs = p + P_ATM;
                v = NaturalGas.volumeM3(s.amount, pabs, t);
                rho = NaturalGas.densityKgM3(pabs, t);
                gasComp += v / pabs;
            } else {
                v = s.amount;
                rho = s.density;
            }
            double h = v / a;
            if (Double.isNaN(shoeP) && z + h >= shoe) shoeP = p + rho * G * (shoe - z);
            p += rho * G * h;
            z += h;
            vol += v;
        }
        walkBhp = p;
        walkShoe = Double.isNaN(shoeP) ? p : shoeP;
        walkGasComp = gasComp;
        return vol;
    }

    private void reconcile(double dt, double q) {
        double cap = annulusCapacityM3();
        double c = EFFECTIVE_COMPRESSIBILITY_PER_PA * (cap + stringCapacityM3());
        if (!bopClosed) {
            casingPa = 0;
            for (int it = 0; it < 8; it++) {
                double ex = walk(0) - cap;
                if (Math.abs(ex) < 1e-7) break;
                if (ex > 0) popTop(ex, 0, false);
                else fillTop(-ex);
            }
            return;
        }
        double ps;
        if (q <= 0) {
            ps = solveClosed(cap, c);
        } else {
            double target = chokeTarget(q);
            ps = Math.max(0, casingPa + (target - casingPa) * -Math.expm1(-dt / CHOKE_TAU_S));
            if (walk(ps) - cap - c * ps < 0) ps = solveClosed(cap, c);
        }
        walk(ps);
        double frac = geo.shoeFractureKgM3() * G * geo.shoeDepthM();
        if (walkShoe > frac) {
            shoeFractured = true;
            ps = Math.max(0, ps - (walkShoe - frac));
            double ex = walk(ps) - cap - c * ps;
            if (ex > 0) popTop(ex, ps, true);
        }
        casingPa = ps;
        for (int it = 0; it < 8; it++) {
            double ex = walk(ps) - cap - c * ps;
            if (ex <= 1e-7) break;
            popTop(ex, ps, false);
        }
        if (casingPa > bopRatedPa) {
            bopFailed = true;
            blowout = true;
            bopClosed = false;
        }
    }

    /** Давление на устье закрытой скважины: содержимое помещается в ствол с учётом сжимаемости. */
    private double solveClosed(double cap, double c) {
        if (walk(0) - cap <= 0) return 0;
        double lo = 0, hi = Math.max(1e5, 2 * casingPa + 1e6);
        while (walk(hi) - cap - c * hi > 0 && hi < PS_MAX) hi *= 2;
        for (int i = 0; i < 60; i++) {
            double mid = 0.5 * (lo + hi);
            if (walk(mid) - cap - c * mid > 0) lo = mid;
            else hi = mid;
        }
        return hi;
    }

    /** Требуемое давление на устье для автоматического дросселя. */
    private double chokeTarget(double q) {
        if (!Double.isNaN(dppTargetPa)) return Math.max(0, casingPa + dppTargetPa - drillPipePressurePa(casingPa, q));
        if (Double.isNaN(heldCasingPa)) heldCasingPa = casingPa;
        return heldCasingPa;
    }

    private double drillPipePressurePa(double ps, double q) {
        walk(ps);
        return walkBhp + annulusFrictionPa(q) - stringHydrostaticPa() + stringFrictionPa(q) + bitDropPa(q);
    }

    /** Убрать объём сверху затрубья: в ёмкости (раствор, жидкость пласта), в сепаратор или факел (газ), в пласт. */
    private void popTop(double volume, double ps, boolean lost) {
        double pabs = ps + P_ATM, t = Trap.SURFACE_K;
        while (volume > EPS && !annulus.isEmpty()) {
            Slug s = annulus.getLast();
            if (s.kind == Kind.GAS) {
                double avail = NaturalGas.volumeM3(s.amount, pabs, t);
                double take = Math.min(avail, volume);
                double mol = take >= avail ? s.amount : NaturalGas.moles(take, pabs, t);
                s.amount -= mol;
                volume -= take;
                if (!lost) {
                    stepGasOutMol += mol;
                    ventedMol += mol;
                    stepInfluxAtSurface = true;
                }
                if (take >= avail || s.amount <= 1e-9) annulus.removeLast();
            } else {
                double take = Math.min(s.amount, volume);
                s.amount -= take;
                volume -= take;
                if (lost) {
                    lostM3 += take;
                } else {
                    if (divertReturns) {
                        reserveM3 += take;
                        pitReferenceM3 -= take;
                    } else {
                        toPit(take, s.density);
                    }
                    stepOutLiquidM3 += take;
                    if (s.kind == Kind.LIQUID) stepInfluxAtSurface = true;
                }
                if (s.amount <= EPS) annulus.removeLast();
            }
        }
    }

    private void fillTop(double v) {
        double take = Math.min(v, pitM3);
        if (take <= 0) return;
        pitM3 -= take;
        if (!annulus.isEmpty() && annulus.getLast().sameFluid(Kind.MUD, pitKgM3)) annulus.getLast().amount += take;
        else annulus.addLast(new Slug(Kind.MUD, take, pitKgM3));
    }

    private void toPit(double v, double rho) {
        double mass = pitM3 * pitKgM3 + v * rho;
        pitM3 += v;
        pitKgM3 = mass / pitM3;
    }

    private void pits() {
        if (pitM3 < MAKEUP_BELOW * pitCapacityM3) {
            double add = MAKEUP_TO * pitCapacityM3 - pitM3;
            pitLcmKgM3 *= pitM3 / (pitM3 + add);
            pitM3 += add;
            pitReferenceM3 += add;
            madeUpM3 += add;
        }
        if (pitM3 > pitCapacityM3) {
            spilledM3 += pitM3 - pitCapacityM3;
            pitM3 = pitCapacityM3;
        }
    }

    // --- пласт ---

    private FormationFluid influxFluid() {
        return prospect.trap().fluidAt(prospect.topM());
    }

    /** Вскрытая мощность коллектора, м. */
    public double exposedPayM() {
        return prospect == null ? 0 : Math.max(0, Math.min(holeDepthM, prospect.baseM()) - prospect.topM());
    }

    private double payMidM() {
        return prospect.topM() + exposedPayM() / 2;
    }

    /** Пластовое давление в середине вскрытого интервала, Па; NaN — коллектор не вскрыт. */
    public double porePressurePa() {
        return exposedPayM() <= 0 ? Double.NaN : prospect.trap().pressureAt(payMidM());
    }

    private double pwfAtPay(double bhp) {
        return bhp - pitKgM3 * G * (holeDepthM - payMidM());
    }

    private double productivityIndex(double h, FormationFluid f) {
        return 2 * Math.PI * prospect.trap().permeabilityM2() * h
                / (f.viscosityPaS * (Math.log(DRAINAGE_RADIUS_M / (geo.holeDiameterM() / 2)) - 0.75));
    }

    private double influxRate(double bhp) {
        double h = exposedPayM();
        if (h <= 0) return 0;
        double dp = porePressurePa() - pwfAtPay(bhp);
        return dp > 0 ? productivityIndex(h, influxFluid()) * dp : 0;
    }

    /** Открытый фонтан: ствол разгружен, забойное давление — вес столба пластового флюида. */
    private void flowing(double dt) {
        FormationFluid f = influxFluid();
        double h = Math.max(exposedPayM(), 1), mid = payMidM(), pp = prospect.trap().pressureAt(mid);
        double t = Trap.temperatureK(mid);
        double pwf = f == FormationFluid.GAS ? 0.5 * NaturalGas.densityKgM3(pp + P_ATM, t) * G * mid
                : f.liquidDensityKgM3 * G * mid;
        blowoutM3s = productivityIndex(h, f) * Math.max(0, pp - pwf);
        double v = blowoutM3s * dt;
        releasedStdM3 += f == FormationFluid.GAS ? NaturalGas.standardVolumeM3(NaturalGas.moles(v, pwf + P_ATM, t)) : v;
        bhpPa = pwf;
        casingPa = 0;
        influxM3s = blowoutM3s;
        pumpM3s = ropMs = wobN = 0;
    }

    // --- трение и вес ---

    private double annulusFrictionPa(double q) {
        return DrillingHydraulics.annulusGradientPaM(q, geo.holeDiameterM(), geo.pipeOuterM(), pitKgM3,
                geo.plasticViscosityPaS(), geo.yieldPointPa()) * holeDepthM;
    }

    private double stringFrictionPa(double q) {
        return DrillingHydraulics.pipeGradientPaM(q, geo.pipeInnerM(), pitKgM3, geo.plasticViscosityPaS(), geo.yieldPointPa())
                * holeDepthM;
    }

    private double bitDropPa(double q) {
        return DrillingHydraulics.bitPressureDropPa(q, pitKgM3, geo.nozzleAreaM2());
    }

    private double stringHydrostaticPa() {
        double p = 0;
        for (Slug s : string) p += s.density * G * s.amount / geo.innerAreaM2();
        return p;
    }

    /** Потери давления на циркуляцию при подаче {@code q}: трубы, долото, затрубье, Па (давление прокачки SCR). */
    public double circulatingLossPa(double q) {
        return stringFrictionPa(q) + bitDropPa(q) + annulusFrictionPa(q);
    }

    private double bhaInHoleM() {
        return Math.min(holeDepthM, geo.bhaLengthM());
    }

    private double buoyancy() {
        return 1 - pitKgM3 / STEEL_KGM3;
    }

    /** Наибольшая допустимая нагрузка на долото, Н. */
    public double maxWobN() {
        return NEUTRAL_POINT_FACTOR * geo.bhaKgPerM() * bhaInHoleM() * G * buoyancy();
    }

    private double rop() {
        boolean pay = prospect != null && prospect.contains(holeDepthM);
        double pore = pay ? prospect.trap().equivalentDensityKgM3(holeDepthM) : PetroleumField.HYDROSTATIC_PA_M / G;
        double ecd = ecdKgM3 > 0 ? ecdKgM3 : pitKgM3;
        return BourgoyneYoung.ropMs(pay ? BourgoyneYoung.SANDSTONE : BourgoyneYoung.SHALE, holeDepthM, pore, ecd, wobN,
                geo.holeDiameterM(), rpm, 0, DrillingHydraulics.jetImpactN(pumpM3s, pitKgM3, geo.nozzleAreaM2()));
    }

    private void outputs(double q) {
        if (!blowout) {
            walk(casingPa);
            bhpPa = walkBhp + annulusFrictionPa(q);
            shoeLastPa = walkShoe + (q > 0 ? annulusFrictionPa(q) * geo.shoeDepthM() / holeDepthM : 0);
            double hydStr = stringHydrostaticPa();
            if (q > 0) dppPa = Math.max(0, bhpPa - hydStr + stringFrictionPa(q) + bitDropPa(q));
            else dppPa = bopClosed ? Math.max(0, walkBhp - hydStr) : 0;
        } else {
            dppPa = 0;
            shoeLastPa = bhpPa * geo.shoeDepthM() / holeDepthM;
        }
        ecdKgM3 = DrillingHydraulics.equivalentDensityKgM3(bhpPa, holeDepthM);
        double steelKg = geo.pipeKgPerM() * (holeDepthM - bhaInHoleM()) + geo.bhaKgPerM() * bhaInHoleM();
        hookLoadN = steelKg * G * buoyancy() + BLOCK_KG * G - wobN;
        torqueNm = rpm > 0 && !blowout ? BIT_FRICTION * wobN * geo.holeDiameterM() / 3 + STRING_TORQUE_NM_PER_M * holeDepthM : 0;
    }

    // --- показания ---

    public WellGeometry geometry() {
        return geo;
    }

    public Prospect prospect() {
        return prospect;
    }

    public double holeDepthM() {
        return holeDepthM;
    }

    public double bottomholePressurePa() {
        return bhpPa;
    }

    /** Давление на стояке (в трубах), Па. */
    public double drillPipePressurePa() {
        return dppPa;
    }

    /** Давление в затрубье на устье (перед штуцером), Па. */
    public double casingPressurePa() {
        return casingPa;
    }

    public double shoePressurePa() {
        return shoeLastPa;
    }

    public double ecdKgM3() {
        return ecdKgM3;
    }

    /** МДДК при плотности раствора в ёмкостях, Па. */
    public double maaspPa() {
        return Math.max(0, (geo.shoeFractureKgM3() - pitKgM3) * G * geo.shoeDepthM());
    }

    public double hookLoadN() {
        return hookLoadN;
    }

    public double wobN() {
        return wobN;
    }

    public double rotaryTorqueNm() {
        return torqueNm;
    }

    public double ropMs() {
        return ropMs;
    }

    public double pumpRateM3s() {
        return pumpM3s;
    }

    /** Гидравлическая мощность насосов на валу, Вт. */
    public double pumpPowerW() {
        return pumpM3s * dppPa / PUMP_EFFICIENCY;
    }

    public double rotaryPowerW() {
        return torqueNm * rpm * 2 * Math.PI / 60;
    }

    /** Выход жидкости из затрубья на поверхности, м³/с. */
    public double flowOutM3s() {
        return flowOutM3s;
    }

    /** Приток из пласта в пластовых условиях, м³/с. */
    public double influxM3s() {
        return influxM3s;
    }

    /** Газ на устье: в сепаратор при закрытом превенторе или в атмосферу при открытом, моль/с. */
    public double gasOutMolS() {
        return gasOutMolS;
    }

    public double pitVolumeM3() {
        return pitM3;
    }

    public double pitCapacityM3() {
        return pitCapacityM3;
    }

    public double pitDensityKgM3() {
        return pitKgM3;
    }

    /** Прирост против ожидаемого уровня, м³; перелитое через край тоже считается — его видит датчик потока. */
    public double pitGainM3() {
        return pitM3 + spilledM3 - pitReferenceM3;
    }

    /** Признак притока: прирост в ёмкостях или перелив при остановленном насосе и открытом превенторе. */
    public boolean kickAlarm() {
        return !blowout && (pitGainM3() > PIT_ALARM_M3 || (!bopClosed && pumpM3s <= 0 && flowOutM3s > 1e-4));
    }

    public boolean bopClosed() {
        return bopClosed;
    }

    public boolean blowout() {
        return blowout;
    }

    public boolean bopFailed() {
        return bopFailed;
    }

    public boolean shoeFractured() {
        return shoeFractured;
    }

    /** Выход фонтана в пластовых условиях, м³/с. */
    public double blowoutRateM3s() {
        return blowout ? blowoutM3s : 0;
    }

    /** Выброшено в нормальных условиях (газ) или в пластовых (жидкость), м³. */
    public double releasedM3() {
        return releasedStdM3;
    }

    public double totalInfluxM3() {
        return totalInfluxM3;
    }

    public double lostM3() {
        return lostM3;
    }

    public double madeUpM3() {
        return madeUpM3;
    }

    /** Газ, вышедший на устье за всё время, моль. */
    public double ventedMol() {
        return ventedMol;
    }

    /** В затрубье есть флюид пласта. */
    public boolean hasInflux() {
        for (Slug s : annulus) if (s.kind != Kind.MUD && s.amount > 1e-6) return true;
        return false;
    }

    /** Плотность выходящего из затрубья раствора, кг/м³ (NaN — сверху флюид пласта). */
    public double returnsDensityKgM3() {
        Slug top = annulus.isEmpty() ? null : annulus.getLast();
        return top != null && top.kind == Kind.MUD ? top.density : Double.NaN;
    }

    /** Плотность раствора у долота в колонне, кг/м³. */
    public double bitMudDensityKgM3() {
        return string.isEmpty() ? pitKgM3 : string.getLast().density;
    }

    public double annulusCapacityM3() {
        return geo.annulusAreaM2() * holeDepthM;
    }

    public double stringCapacityM3() {
        return geo.innerAreaM2() * holeDepthM;
    }

    /** Открытие штуцера, доли (по уравнению отверстия при текущем расходе на выходе). */
    public double chokeOpening() {
        if (!bopClosed) return 1;
        double q = Math.max(flowOutM3s, 0);
        if (q <= 0 || casingPa <= 0) return casingPa > 0 ? 0 : 1;
        double area = q / CHOKE_CD * Math.sqrt(pitKgM3 / (2 * casingPa));
        return Math.min(1, area / CHOKE_AREA_M2);
    }

    /** Скважина заглушена: нет притока и флюида пласта в стволе, забойное давление не ниже пластового. */
    public boolean dead() {
        if (blowout || hasInflux() || influxM3s > 0) return false;
        double pp = porePressurePa();
        return Double.isNaN(pp) || bhpPa >= pp;
    }

    // --- сохранение ---

    private static final int HEADER = 11;

    /** Состояние для сохранения мира. */
    public double[] exportState() {
        double[] out = new double[HEADER + 3 * (string.size() + annulus.size())];
        out[0] = holeDepthM;
        out[1] = pitM3;
        out[2] = pitKgM3;
        out[3] = pitReferenceM3;
        out[4] = casingPa;
        out[5] = (bopClosed ? 1 : 0) | (blowout ? 2 : 0) | (bopFailed ? 4 : 0) | (shoeFractured ? 8 : 0);
        out[6] = string.size();
        out[7] = totalInfluxM3;
        out[8] = releasedStdM3;
        out[9] = lostM3;
        out[10] = ventedMol;
        int i = HEADER;
        for (Slug s : string) i = put(out, i, s);
        for (Slug s : annulus) i = put(out, i, s);
        return out;
    }

    private static int put(double[] out, int i, Slug s) {
        out[i] = s.kind.ordinal();
        out[i + 1] = s.amount;
        out[i + 2] = s.density;
        return i + 3;
    }

    /** Восстановить скважину; {@code null} — состояние повреждено. */
    public static Well restore(WellGeometry geo, Prospect prospect, double pitCapacityM3, double[] in) {
        if (in == null || in.length < HEADER || (in.length - HEADER) % 3 != 0 || !(in[0] > geo.shoeDepthM()) || !(in[2] > 900))
            return null;
        Well w = new Well(geo, prospect, in[0], in[2], pitCapacityM3);
        w.pitM3 = in[1];
        w.pitReferenceM3 = in[3];
        w.casingPa = in[4];
        int flags = (int) in[5];
        w.bopClosed = (flags & 1) != 0;
        w.blowout = (flags & 2) != 0 && prospect != null;
        w.bopFailed = (flags & 4) != 0;
        w.shoeFractured = (flags & 8) != 0;
        int strings = (int) in[6];
        w.totalInfluxM3 = in[7];
        w.releasedStdM3 = in[8];
        w.lostM3 = in[9];
        w.ventedMol = in[10];
        w.string.clear();
        w.annulus.clear();
        Kind[] kinds = Kind.values();
        for (int k = 0, i = HEADER; i < in.length; k++, i += 3) {
            int ord = (int) in[i];
            if (ord < 0 || ord >= kinds.length || !(in[i + 1] >= 0)) return null;
            if (kinds[ord] != Kind.MUD && prospect == null) return null;
            Slug s = new Slug(kinds[ord], in[i + 1], in[i + 2]);
            (k < strings ? w.string : w.annulus).add(s);
        }
        if (w.string.isEmpty() || w.annulus.isEmpty()) return null;
        w.outputs(0);
        return w;
    }
}
