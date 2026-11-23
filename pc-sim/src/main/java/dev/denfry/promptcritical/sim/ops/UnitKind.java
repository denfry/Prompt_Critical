package dev.denfry.promptcritical.sim.ops;

import static dev.denfry.promptcritical.sim.ops.CommissioningCheck.BRAKE_TEST;
import static dev.denfry.promptcritical.sim.ops.CommissioningCheck.INSTRUMENT_CHECK;
import static dev.denfry.promptcritical.sim.ops.CommissioningCheck.PRESSURE_TEST;
import static dev.denfry.promptcritical.sim.ops.CommissioningCheck.PRIMING;
import static dev.denfry.promptcritical.sim.ops.CommissioningCheck.RELIEF_VALVE_SET;
import static dev.denfry.promptcritical.sim.ops.CommissioningCheck.ROTATION_CHECK;
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
            EnumSet.of(CommissioningDefectKind.BRAKE_MISADJUSTED));

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
