package dev.denfry.promptcritical.sim.ops;

/** Каталог монтажных дефектов I0 (13 §13.6.6). */
public enum CommissioningDefectKind {
    /** Заклёпочный шов «потеет» при p ≥ k_w·p_раб, рвётся при k_b·p_раб. */
    SEAM_WEEP(CommissioningCheck.PRESSURE_TEST),
    /** Пористая отливка корпуса (компонент отливки 08 §8.9). */
    CASTING_POROSITY(CommissioningCheck.PRESSURE_TEST),
    /** Смещение уставки ПК δ. */
    PSV_OFFSET(CommissioningCheck.RELIEF_VALVE_SET),
    /** ПК «прикипел»: подрыв при 1,6–2,0 уставки. */
    PSV_STUCK(CommissioningCheck.RELIEF_VALVE_SET),
    /** Смещение нуля манометра, доля шкалы: показывает меньше истинного. */
    GAUGE_OFFSET(CommissioningCheck.INSTRUMENT_CHECK),
    /** Засорён нижний кран водоуказательного стекла: стекло держит последний уровень. */
    GAUGE_GLASS_BLOCKED(CommissioningCheck.INSTRUMENT_CHECK),
    /** Обратное вращение вала Create. */
    REVERSED_ROTATION(CommissioningCheck.ROTATION_CHECK),
    /** Насос не залит. */
    DRY_PUMP(CommissioningCheck.PRIMING),
    /** Тормозной момент подъёма — доля расчётного. */
    BRAKE_MISADJUSTED(CommissioningCheck.BRAKE_TEST),
    /** Катаракт регулятора без масла. */
    DASHPOT_DRY(CommissioningCheck.TRIAL_RUN),
    /** Отсыревшая обмотка: сопротивление изоляции, МОм (сухая — {@link CommissioningService#INSULATION_DRY_MOHM}). */
    WINDING_DAMP(CommissioningCheck.INSULATION_TEST),
    /** Расцентровка муфты, мм. */
    COUPLING_MISALIGNED(CommissioningCheck.ALIGNMENT),
    /** Отсыревшая изоляция шин или кабеля, МОм. */
    INSULATION_DAMP(CommissioningCheck.INSULATION_TEST),
    /** Защита срабатывает с задержкой: время срабатывания в долях допустимого (больше 1). */
    TRIP_SLOW(CommissioningCheck.SIS_PROOF_TEST),
    /** Перепутан контур КИП: ошибка показания в долях шкалы. */
    LOOP_MISWIRED(CommissioningCheck.LOOP_CHECK),
    /** Течь фланца газопровода: утечка в долях испытательного объёма за час. */
    FLANGE_LEAK(CommissioningCheck.INERT_LEAK_TEST);

    private final CommissioningCheck revealedBy;

    CommissioningDefectKind(CommissioningCheck revealedBy) {
        this.revealedBy = revealedBy;
    }

    /** Единственная проверка, которая находит дефект гарантированно (P2). */
    public CommissioningCheck revealedBy() {
        return revealedBy;
    }
}
