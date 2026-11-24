package dev.denfry.promptcritical.sim.ops;

import static dev.denfry.promptcritical.sim.ops.CommissioningCheck.ALIGNMENT;
import static dev.denfry.promptcritical.sim.ops.CommissioningCheck.BRAKE_TEST;
import static dev.denfry.promptcritical.sim.ops.CommissioningCheck.INSULATION_TEST;
import static dev.denfry.promptcritical.sim.ops.CommissioningCheck.INSTRUMENT_CHECK;
import static dev.denfry.promptcritical.sim.ops.CommissioningCheck.LOOP_CHECK;
import static dev.denfry.promptcritical.sim.ops.CommissioningCheck.PRESSURE_TEST;
import static dev.denfry.promptcritical.sim.ops.CommissioningCheck.PRIMING;
import static dev.denfry.promptcritical.sim.ops.CommissioningCheck.PURGE;
import static dev.denfry.promptcritical.sim.ops.CommissioningCheck.RELIEF_VALVE_SET;
import static dev.denfry.promptcritical.sim.ops.CommissioningCheck.ROTATION_CHECK;
import static dev.denfry.promptcritical.sim.ops.CommissioningCheck.SIS_PROOF_TEST;
import static dev.denfry.promptcritical.sim.ops.CommissioningCheck.TRIAL_RUN;

import java.util.EnumSet;
import java.util.Set;

/** Установки с пусконаладкой, их эпоха и чек-листы (13 §13.6.6); эпоха — ступень лицензии профиля licensing. */
public enum UnitKind {
    FIRE_TUBE_BOILER(PlantEra.I0, EnumSet.of(PRESSURE_TEST, INSTRUMENT_CHECK, RELIEF_VALVE_SET, TRIAL_RUN),
            EnumSet.of(UnitComponent.SHELL, UnitComponent.RELIEF_VALVE, UnitComponent.PRESSURE_GAUGE,
                    UnitComponent.GAUGE_GLASS),
            EnumSet.of(CommissioningDefectKind.SEAM_WEEP, CommissioningDefectKind.PSV_OFFSET,
                    CommissioningDefectKind.PSV_STUCK, CommissioningDefectKind.GAUGE_OFFSET,
                    CommissioningDefectKind.GAUGE_GLASS_BLOCKED)),
    STEAM_ENGINE(PlantEra.I0, EnumSet.of(PRESSURE_TEST, TRIAL_RUN),
            EnumSet.of(UnitComponent.CYLINDER, UnitComponent.STOP_VALVE, UnitComponent.GOVERNOR),
            EnumSet.of(CommissioningDefectKind.CASTING_POROSITY, CommissioningDefectKind.DASHPOT_DRY)),
    MAIN_MINE_FAN(PlantEra.I0, EnumSet.of(ROTATION_CHECK, TRIAL_RUN), EnumSet.of(UnitComponent.DRIVE),
            EnumSet.of(CommissioningDefectKind.REVERSED_ROTATION)),
    MINE_DRAINAGE_PUMP(PlantEra.I0, EnumSet.of(PRIMING, ROTATION_CHECK, TRIAL_RUN),
            EnumSet.of(UnitComponent.PUMP, UnitComponent.DRIVE),
            EnumSet.of(CommissioningDefectKind.DRY_PUMP, CommissioningDefectKind.REVERSED_ROTATION)),
    MINE_HOIST(PlantEra.I0, EnumSet.of(BRAKE_TEST, TRIAL_RUN), EnumSet.of(UnitComponent.BRAKE),
            EnumSet.of(CommissioningDefectKind.BRAKE_MISADJUSTED)),
    /** Синхронный генератор I1: изоляция, центровка, пробный пуск под нагрузкой (план HP2.1). */
    SYNC_GENERATOR(PlantEra.I1, EnumSet.of(INSULATION_TEST, ALIGNMENT, TRIAL_RUN),
            EnumSet.of(UnitComponent.STATOR, UnitComponent.COUPLING),
            EnumSet.of(CommissioningDefectKind.WINDING_DAMP, CommissioningDefectKind.COUPLING_MISALIGNED)),
    /** Доменная печь I1: опрессовка охлаждения кладки, КИП, пробная работа (план HP2.1). */
    BLAST_FURNACE(PlantEra.I1, EnumSet.of(PRESSURE_TEST, INSTRUMENT_CHECK, TRIAL_RUN),
            EnumSet.of(UnitComponent.SHELL, UnitComponent.PRESSURE_GAUGE),
            EnumSet.of(CommissioningDefectKind.SEAM_WEEP, CommissioningDefectKind.GAUGE_OFFSET)),
    /** Ячейка выключателя I1: изоляция присоединения, отключение от защиты, опробование под напряжением (план HP2.2). */
    BREAKER_CELL(PlantEra.I1, EnumSet.of(INSULATION_TEST, SIS_PROOF_TEST, TRIAL_RUN),
            EnumSet.of(UnitComponent.BUSBAR, UnitComponent.TRIP_DEVICE),
            EnumSet.of(CommissioningDefectKind.INSULATION_DAMP, CommissioningDefectKind.TRIP_SLOW)),
    /**
     * Водотрубный котёл I1: гидроиспытание барабана, КИП, предпусковая вентиляция BMS, отсечка топлива по уровню,
     * пробная работа (план HP2.2).
     */
    WATER_TUBE_BOILER(PlantEra.I1, EnumSet.of(PRESSURE_TEST, INSTRUMENT_CHECK, PURGE, SIS_PROOF_TEST, TRIAL_RUN),
            EnumSet.of(UnitComponent.SHELL, UnitComponent.PRESSURE_GAUGE, UnitComponent.TRIP_DEVICE),
            EnumSet.of(CommissioningDefectKind.SEAM_WEEP, CommissioningDefectKind.GAUGE_OFFSET,
                    CommissioningDefectKind.TRIP_SLOW)),
    /** Печь Ачесона I1: изоляция токоподвода, пробная графитизация (план HP2.2). */
    ACHESON_FURNACE(PlantEra.I1, EnumSet.of(INSULATION_TEST, TRIAL_RUN), EnumSet.of(UnitComponent.BUSBAR),
            EnumSet.of(CommissioningDefectKind.INSULATION_DAMP)),
    /** Графитовый котёл T0: контуры контроля потока, сброс аварийных стержней, работа на мощности (план HP2.2). */
    GRAPHITE_PILE(PlantEra.I1, EnumSet.of(LOOP_CHECK, SIS_PROOF_TEST, TRIAL_RUN),
            EnumSet.of(UnitComponent.FLUX_CHANNEL, UnitComponent.TRIP_DEVICE),
            EnumSet.of(CommissioningDefectKind.LOOP_MISWIRED, CommissioningDefectKind.TRIP_SLOW));

    private final PlantEra era;
    private final Set<CommissioningCheck> checklist;
    private final Set<UnitComponent> components;
    private final Set<CommissioningDefectKind> defects;

    UnitKind(PlantEra era, EnumSet<CommissioningCheck> checklist, EnumSet<UnitComponent> components,
            EnumSet<CommissioningDefectKind> defects) {
        this.era = era;
        this.checklist = Set.copyOf(checklist);
        this.components = Set.copyOf(components);
        this.defects = Set.copyOf(defects);
    }

    /** Эпоха, лицензия на которую нужна для пуска в профиле licensing. */
    public PlantEra era() {
        return era;
    }

    /** Обязательные проверки. */
    public Set<CommissioningCheck> checklist() {
        return checklist;
    }

    public Set<UnitComponent> components() {
        return components;
    }

    /** Дефекты, которые бывают у этой установки. */
    public Set<CommissioningDefectKind> possibleDefects() {
        return defects;
    }
}
