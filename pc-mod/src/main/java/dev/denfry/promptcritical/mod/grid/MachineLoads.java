package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.acoustics.VibrationTransmitterBlockEntity;
import dev.denfry.promptcritical.mod.automation.AutomationBlockEntity;
import dev.denfry.promptcritical.mod.automation.AutomationPanelBlockEntity;
import dev.denfry.promptcritical.mod.automation.PlcBlockEntity;
import dev.denfry.promptcritical.mod.automation.RelayRackBlockEntity;
import dev.denfry.promptcritical.mod.automation.StationBatteryBlockEntity;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.boiler.FireTubeBoilerBlockEntity;
import dev.denfry.promptcritical.mod.boiler.LancashireStokerBlockEntity;
import dev.denfry.promptcritical.mod.boiler.PressureGaugeBlockEntity;
import dev.denfry.promptcritical.mod.boiler.SteamEngineBlockEntity;
import dev.denfry.promptcritical.mod.chem.CrackedGasUnitBlockEntity;
import dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.chlor.BrineTreaterBlockEntity;
import dev.denfry.promptcritical.mod.chlor.CellRoomBlockEntity;
import dev.denfry.promptcritical.mod.chlor.EdcCrackerBlockEntity;
import dev.denfry.promptcritical.mod.chlor.EdcUnitBlockEntity;
import dev.denfry.promptcritical.mod.chlor.HclFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.chlor.LiquefierBlockEntity;
import dev.denfry.promptcritical.mod.chlor.PvcReactorBlockEntity;
import dev.denfry.promptcritical.mod.content.DocumentLecternBlockEntity;
import dev.denfry.promptcritical.mod.drilling.BopAccumulatorBlockEntity;
import dev.denfry.promptcritical.mod.drilling.BopRamBlockEntity;
import dev.denfry.promptcritical.mod.drilling.CoreAnalysisStationBlockEntity;
import dev.denfry.promptcritical.mod.drilling.DrillersConsoleBlockEntity;
import dev.denfry.promptcritical.mod.drilling.LoggingUnitBlockEntity;
import dev.denfry.promptcritical.mod.drilling.MudHopperBlockEntity;
import dev.denfry.promptcritical.mod.drilling.MudPumpBlockEntity;
import dev.denfry.promptcritical.mod.drilling.SeismicRecorderBlockEntity;
import dev.denfry.promptcritical.mod.electronics.TrichlorosilaneReactorBlockEntity;
import dev.denfry.promptcritical.mod.environment.FgdAbsorberBlockEntity;
import dev.denfry.promptcritical.mod.foundry.CrucibleBlockEntity;
import dev.denfry.promptcritical.mod.foundry.CupolaRealBlockEntity;
import dev.denfry.promptcritical.mod.foundry.SandMoldBlockEntity;
import dev.denfry.promptcritical.mod.gas.GasPlantBlockEntity;
import dev.denfry.promptcritical.mod.gas.HeliumRecoveryBlockEntity;
import dev.denfry.promptcritical.mod.gas.NglFractionatorBlockEntity;
import dev.denfry.promptcritical.mod.grid.GridDispatchCenterBlockEntity;
import dev.denfry.promptcritical.mod.grid.InstrumentBlockEntity;
import dev.denfry.promptcritical.mod.grid.RelayCabinetBlockEntity;
import dev.denfry.promptcritical.mod.hazard.CpTestPostBlockEntity;
import dev.denfry.promptcritical.mod.hazard.GasStorageBulletBlockEntity;
import dev.denfry.promptcritical.mod.hazard.OilSkimmerBlockEntity;
import dev.denfry.promptcritical.mod.hazard.PigTrapBlockEntity;
import dev.denfry.promptcritical.mod.hazard.TankGaugeBlockEntity;
import dev.denfry.promptcritical.mod.heavywater.GsExchangeTowerBlockEntity;
import dev.denfry.promptcritical.mod.heavywater.HeavyWaterColumnBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.BakingFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.BlastBlowerEngineBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.BlastFurnaceControllerBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.BlastFurnaceTopBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.ByproductPlantBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.CastHouseMachineBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.CokeOvenBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.DustCatcherBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.GasExhausterRealBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.HotBlastStoveBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.LadleBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.QuenchTowerBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.SinterStrandBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.SlagGranulatorBlockEntity;
import dev.denfry.promptcritical.mod.isotopes.IrradiationChannelBlockEntity;
import dev.denfry.promptcritical.mod.isotopes.RtgBlockEntity;
import dev.denfry.promptcritical.mod.isotopes.TrigaBlockEntity;
import dev.denfry.promptcritical.mod.manufacturing.BloomeryBlockEntity;
import dev.denfry.promptcritical.mod.manufacturing.DrawPlateBlockEntity;
import dev.denfry.promptcritical.mod.manufacturing.ForgeHearthBlockEntity;
import dev.denfry.promptcritical.mod.manufacturing.PoweredSmithyDevice;
import dev.denfry.promptcritical.mod.manufacturing.SmithingAnvilBlockEntity;
import dev.denfry.promptcritical.mod.manufacturing.SmithyFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.market.MarketTerminalBlockEntity;
import dev.denfry.promptcritical.mod.mes.MesServerBlockEntity;
import dev.denfry.promptcritical.mod.methanol.MethanolColumnBlockEntity;
import dev.denfry.promptcritical.mod.mine.HeadframeSteelBlockEntity;
import dev.denfry.promptcritical.mod.mine.LongwallShearerBlockEntity;
import dev.denfry.promptcritical.mod.mine.MainMineFanBlockEntity;
import dev.denfry.promptcritical.mod.mine.MineHoistBlockEntity;
import dev.denfry.promptcritical.mod.mine.PoweredRoofSupportBlockEntity;
import dev.denfry.promptcritical.mod.mine.ShaftSignalBlockEntity;
import dev.denfry.promptcritical.mod.nitrogen.AcidConcentratorBlockEntity;
import dev.denfry.promptcritical.mod.nitrogen.Co2AbsorberBlockEntity;
import dev.denfry.promptcritical.mod.nitrogen.SteamReformerBlockEntity;
import dev.denfry.promptcritical.mod.nonferrous.ReductionPotBlockEntity;
import dev.denfry.promptcritical.mod.npp.BwrReactorBlockEntity;
import dev.denfry.promptcritical.mod.npp.CanduReactorBlockEntity;
import dev.denfry.promptcritical.mod.npp.PowerReactorBlockEntity;
import dev.denfry.promptcritical.mod.npp.PressurizerBlockEntity;
import dev.denfry.promptcritical.mod.npp.PwrReactorBlockEntity;
import dev.denfry.promptcritical.mod.npp.SfrReactorBlockEntity;
import dev.denfry.promptcritical.mod.npp.SteamGeneratorBlockEntity;
import dev.denfry.promptcritical.mod.nuclear.FuelShopBlockEntity;
import dev.denfry.promptcritical.mod.nuclear.Uo2ConversionBlockEntity;
import dev.denfry.promptcritical.mod.ops.FireDeviceBlockEntity;
import dev.denfry.promptcritical.mod.ops.HydrotestPumpBlockEntity;
import dev.denfry.promptcritical.mod.ops.LineBlindBlockEntity;
import dev.denfry.promptcritical.mod.ops.LockoutBoxBlockEntity;
import dev.denfry.promptcritical.mod.ops.MaintenancePanelBlockEntity;
import dev.denfry.promptcritical.mod.ops.PermitBoardBlockEntity;
import dev.denfry.promptcritical.mod.ops.RecordingGaugeBlockEntity;
import dev.denfry.promptcritical.mod.ops.WaterTreatmentBlockEntity;
import dev.denfry.promptcritical.mod.powerhouse.DeaeratorBlockEntity;
import dev.denfry.promptcritical.mod.powerhouse.GasTurbineBlockEntity;
import dev.denfry.promptcritical.mod.powerhouse.HrsgBlockEntity;
import dev.denfry.promptcritical.mod.powerhouse.TideGaugeBlockEntity;
import dev.denfry.promptcritical.mod.powerhouse.WaterTubeBoilerBlockEntity;
import dev.denfry.promptcritical.mod.production.CrudeColumnBlockEntity;
import dev.denfry.promptcritical.mod.production.PumpjackBlockEntity;
import dev.denfry.promptcritical.mod.production.SeparatorBlockEntity;
import dev.denfry.promptcritical.mod.production.TubeFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.production.WellheadBlockEntity;
import dev.denfry.promptcritical.mod.radiation.DeconShowerBlockEntity;
import dev.denfry.promptcritical.mod.radiation.FuelRackBlockEntity;
import dev.denfry.promptcritical.mod.radiation.HandFootMonitorBlockEntity;
import dev.denfry.promptcritical.mod.radiation.PortalMonitorBlockEntity;
import dev.denfry.promptcritical.mod.reactor.FuelChannelBlockEntity;
import dev.denfry.promptcritical.mod.reactor.PileControllerBlockEntity;
import dev.denfry.promptcritical.mod.refinery.QualityLabBlockEntity;
import dev.denfry.promptcritical.mod.reprocessing.HlwVaultBlockEntity;
import dev.denfry.promptcritical.mod.reprocessing.PurexCellBlockEntity;
import dev.denfry.promptcritical.mod.safety.FlareIgnitionPanelBlockEntity;
import dev.denfry.promptcritical.mod.safety.FlareKnockoutDrumBlockEntity;
import dev.denfry.promptcritical.mod.safety.FlareSealDrumBlockEntity;
import dev.denfry.promptcritical.mod.safety.ManwayBlockEntity;
import dev.denfry.promptcritical.mod.safety.PatrolCheckpointBlockEntity;
import dev.denfry.promptcritical.mod.safety.PortableVentilatorBlockEntity;
import dev.denfry.promptcritical.mod.safety.RescueTripodBlockEntity;
import dev.denfry.promptcritical.mod.safety.SignageBlockEntity;
import dev.denfry.promptcritical.mod.safety.TurningGearBlockEntity;
import dev.denfry.promptcritical.mod.safety.VentStackBlockEntity;
import dev.denfry.promptcritical.mod.safety.WaterIntakeBlockEntity;
import dev.denfry.promptcritical.mod.scada.AlarmHornBlockEntity;
import dev.denfry.promptcritical.mod.scada.AnnunciatorBlockEntity;
import dev.denfry.promptcritical.mod.scada.ControlDeskBlockEntity;
import dev.denfry.promptcritical.mod.scada.ControlRoomCoreBlockEntity;
import dev.denfry.promptcritical.mod.scada.HistorianBlockEntity;
import dev.denfry.promptcritical.mod.scada.SimulatorDeskBlockEntity;
import dev.denfry.promptcritical.mod.scada.TelemetryGatewayBlockEntity;
import dev.denfry.promptcritical.mod.scada.TrainingSimulatorBlockEntity;
import dev.denfry.promptcritical.mod.scada.TwinWorkstationBlockEntity;
import dev.denfry.promptcritical.mod.scada.WatchdogRelayBlockEntity;
import dev.denfry.promptcritical.mod.site.ConstructionSiteBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.AirSeparatorBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.AsuCompressorBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.BatchFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.BofVesselBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.CryoTankBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.HydrotestBenchBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.IngotMoldBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.LadleStationBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.MillStandBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.OxygenLanceBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.PiercingMillBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.PipeWelderBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.QuenchTankBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.ReheatingFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.uranium.UraniumProcessBlockEntity;
import dev.denfry.promptcritical.mod.waste.ReactorWaterCleanupBlockEntity;
import dev.denfry.promptcritical.mod.waste.RepositoryBlockEntity;
import dev.denfry.promptcritical.mod.uranium.MvProcessBlockEntity;
import dev.denfry.promptcritical.mod.nonferrous.ElectrorefiningCellBlockEntity;
import dev.denfry.promptcritical.mod.uranium.FluorineCellBlockEntity;
import dev.denfry.promptcritical.mod.refinery.DesalterBlockEntity;
import dev.denfry.promptcritical.mod.waste.LrwEvaporatorBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Чем питается каждая машина мода (энергопроект 2, решение владельца 2026-11-23, раздела в GDD нет): у каждого класса
 * BE — один класс нагрузки. GameTest {@code MachineLoadGameTests} создаёт BE всех блоков мода и падает на любом
 * неразобранном, поэтому новая машина не выйдет без решения, откуда она берёт энергию.
 *
 * <p>Класс ищется вверх по иерархии: устройства сети ({@link GridDevice}) и машины на валу Create
 * ({@link KineticBlockEntity}) разобраны целиком. Блоки с общим {@link DeviceBlockEntity} (физика вся в домене)
 * разбираются по id блока.
 */
public final class MachineLoads {
    /** Класс нагрузки. */
    public enum Load {
        /** Активная машина до 200 кВт: номинал от розетки, щита или ИБП 0,4 кВ ({@link MachinePower#supply}). */
        ACTIVE_LV,
        /** Активная машина мощнее 200 кВт: свой ввод 6,3 кВ ({@link MachinePower.Mv}). */
        ACTIVE_MV,
        /** Приборы и автоматика: малая нагрузка 0,4 кВ, обычно от ИБП ({@link MachinePower#powered}). */
        IC,
        /** Устройство сети или её потребитель со своим вводом ({@link GridDevice}, {@link GridLoad}). */
        GRID,
        /** Привод от вала Create: двигатель сети, паровая машина или турбина на том же валу. */
        SHAFT,
        /** Своё топливо, пар, ручной привод или свой источник (выпрямитель, оперативный ток). */
        OWN,
        /** Источник энергии. */
        SOURCE,
        /** Пассивный блок: ничего не берёт. */
        PASSIVE
    }

    private static final Map<Class<?>, Load> BY_CLASS = new LinkedHashMap<>();
    private static final Map<String, Load> BY_BLOCK = new LinkedHashMap<>();

    static {
        // насосы, мешалки, компрессоры установки
        put(Load.ACTIVE_LV, BrineTreaterBlockEntity.class);
        put(Load.ACTIVE_LV, DeconShowerBlockEntity.class);
        put(Load.ACTIVE_LV, EdcUnitBlockEntity.class);
        put(Load.ACTIVE_LV, FgdAbsorberBlockEntity.class);
        put(Load.ACTIVE_LV, GsExchangeTowerBlockEntity.class);
        put(Load.ACTIVE_LV, HeavyWaterColumnBlockEntity.class);
        put(Load.ACTIVE_LV, HeliumRecoveryBlockEntity.class);
        put(Load.ACTIVE_LV, LiquefierBlockEntity.class);
        put(Load.ACTIVE_LV, ReactorWaterCleanupBlockEntity.class);
        // приводы машины
        put(Load.ACTIVE_LV, BlastFurnaceTopBlockEntity.class);
        put(Load.ACTIVE_LV, CastHouseMachineBlockEntity.class);
        put(Load.ACTIVE_LV, HydrotestBenchBlockEntity.class);
        put(Load.ACTIVE_LV, OxygenLanceBlockEntity.class);
        put(Load.ACTIVE_LV, PoweredRoofSupportBlockEntity.class);
        // семейство установок: номинал в ratedPowerW()
        put(Load.ACTIVE_LV, UraniumProcessBlockEntity.class);
        // мешалки, насосы, вентиляторы, нагреватели
        put(Load.ACTIVE_LV, AcidConcentratorBlockEntity.class);
        put(Load.ACTIVE_LV, BakingFurnaceBlockEntity.class);
        put(Load.ACTIVE_LV, BatchFurnaceBlockEntity.class);
        put(Load.ACTIVE_LV, ByproductPlantBlockEntity.class);
        put(Load.ACTIVE_LV, CrackedGasUnitBlockEntity.class);
        put(Load.ACTIVE_LV, CrudeColumnBlockEntity.class);
        put(Load.ACTIVE_LV, EdcCrackerBlockEntity.class);
        put(Load.ACTIVE_LV, FuelShopBlockEntity.class);
        put(Load.PASSIVE, dev.denfry.promptcritical.mod.nuclear.CentrifugeRealBlockEntity.class);
        put(Load.PASSIVE, dev.denfry.promptcritical.mod.nuclear.PlacedItemBlockEntity.class);
        put(Load.ACTIVE_LV, dev.denfry.promptcritical.mod.nuclear.Uf6AutoclaveBlockEntity.class);
        put(Load.ACTIVE_LV, GasPlantBlockEntity.class);
        put(Load.ACTIVE_LV, HclFurnaceBlockEntity.class);
        put(Load.ACTIVE_LV, MethanolColumnBlockEntity.class);
        put(Load.ACTIVE_LV, NglFractionatorBlockEntity.class);
        put(Load.ACTIVE_LV, PortableVentilatorBlockEntity.class);
        put(Load.ACTIVE_LV, PurexCellBlockEntity.class);
        put(Load.ACTIVE_LV, PvcReactorBlockEntity.class);
        put(Load.ACTIVE_LV, PyrolysisFurnaceBlockEntity.class);
        put(Load.ACTIVE_LV, QuenchTowerBlockEntity.class);
        put(Load.ACTIVE_LV, ReheatingFurnaceBlockEntity.class);
        put(Load.ACTIVE_LV, SlagGranulatorBlockEntity.class);
        put(Load.ACTIVE_LV, SteamReformerBlockEntity.class);
        put(Load.ACTIVE_LV, TrichlorosilaneReactorBlockEntity.class);
        put(Load.ACTIVE_LV, Uo2ConversionBlockEntity.class);
        put(Load.ACTIVE_LV, WaterTreatmentBlockEntity.class);
        // лаборатория, пульт, приборы
        put(Load.IC, AlarmHornBlockEntity.class);
        put(Load.IC, AnnunciatorBlockEntity.class);
        put(Load.IC, CoreAnalysisStationBlockEntity.class);
        put(Load.IC, DrillersConsoleBlockEntity.class);
        put(Load.IC, GridDispatchCenterBlockEntity.class);
        put(Load.IC, HistorianBlockEntity.class);
        put(Load.IC, SeismicRecorderBlockEntity.class);
        put(Load.IC, SimulatorDeskBlockEntity.class);
        put(Load.IC, TankGaugeBlockEntity.class);
        put(Load.IC, TelemetryGatewayBlockEntity.class);
        put(Load.IC, TrainingSimulatorBlockEntity.class);
        put(Load.IC, TwinWorkstationBlockEntity.class);
        put(Load.IC, WatchdogRelayBlockEntity.class);
        // автоматика, вычислитель, монитор
        put(Load.IC, ControlDeskBlockEntity.class);
        put(Load.IC, FireDeviceBlockEntity.class);
        put(Load.IC, FlareIgnitionPanelBlockEntity.class);
        put(Load.IC, HandFootMonitorBlockEntity.class);
        put(Load.IC, MarketTerminalBlockEntity.class);
        put(Load.IC, MesServerBlockEntity.class);
        put(Load.IC, PortalMonitorBlockEntity.class);
        // горячая камера: лампы, вытяжка и приводы манипуляторов; контейнер, тележка (своя батарея) и модуль — без сети
        put(Load.ACTIVE_LV, dev.denfry.promptcritical.mod.radiation.HotCellBlockEntity.class);
        put(Load.PASSIVE, dev.denfry.promptcritical.mod.radiation.SpentFuelCaskBlockEntity.class);
        put(Load.PASSIVE, dev.denfry.promptcritical.mod.radiation.CaskCartBlockEntity.class);
        put(Load.PASSIVE, dev.denfry.promptcritical.mod.radiation.DryStorageModuleBlockEntity.class);
        put(Load.IC, QualityLabBlockEntity.class);
        put(Load.IC, ShaftSignalBlockEntity.class);
        put(Load.IC, VibrationTransmitterBlockEntity.class);
        // вал Create
        put(Load.SHAFT, PoweredSmithyDevice.class);
        // вал Create (порт привода)
        put(Load.SHAFT, AsuCompressorBlockEntity.class);
        put(Load.SHAFT, BlastBlowerEngineBlockEntity.class);
        put(Load.SHAFT, BopAccumulatorBlockEntity.class);
        put(Load.SHAFT, CupolaRealBlockEntity.class);
        put(Load.SHAFT, GasExhausterRealBlockEntity.class);
        put(Load.SHAFT, LancashireStokerBlockEntity.class);
        put(Load.SHAFT, LongwallShearerBlockEntity.class);
        put(Load.SHAFT, MainMineFanBlockEntity.class);
        put(Load.SHAFT, MillStandBlockEntity.class);
        put(Load.SHAFT, MineHoistBlockEntity.class);
        put(Load.SHAFT, MudPumpBlockEntity.class);
        put(Load.SHAFT, OilSkimmerBlockEntity.class);
        put(Load.SHAFT, PiercingMillBlockEntity.class);
        put(Load.SHAFT, PipeWelderBlockEntity.class);
        put(Load.SHAFT, PumpjackBlockEntity.class);
        put(Load.SHAFT, dev.denfry.promptcritical.mod.production.MainlinePumpBlockEntity.class);
        put(Load.SHAFT, SinterStrandBlockEntity.class);
        put(Load.SHAFT, TurningGearBlockEntity.class);
        // своё топливо, пар, ручной привод или свой источник
        put(Load.OWN, BloomeryBlockEntity.class);
        put(Load.OWN, dev.denfry.promptcritical.mod.production.LineHeaterBlockEntity.class);
        put(Load.OWN, BofVesselBlockEntity.class);
        put(Load.OWN, CellRoomBlockEntity.class);
        put(Load.OWN, CokeOvenBlockEntity.class);
        put(Load.OWN, CrucibleBlockEntity.class);
        put(Load.OWN, FireTubeBoilerBlockEntity.class);
        put(Load.OWN, ForgeHearthBlockEntity.class);
        put(Load.OWN, HotBlastStoveBlockEntity.class);
        put(Load.OWN, LoggingUnitBlockEntity.class);
        put(Load.OWN, RelayCabinetBlockEntity.class);
        put(Load.OWN, SmithyFurnaceBlockEntity.class);
        put(Load.OWN, WaterTubeBoilerBlockEntity.class);
        // источник энергии
        put(Load.SOURCE, BwrReactorBlockEntity.class);
        put(Load.SOURCE, CanduReactorBlockEntity.class);
        put(Load.SOURCE, GasTurbineBlockEntity.class);
        put(Load.SOURCE, HrsgBlockEntity.class);
        put(Load.SOURCE, PowerReactorBlockEntity.class);
        put(Load.SOURCE, PwrReactorBlockEntity.class);
        put(Load.SOURCE, RtgBlockEntity.class);
        put(Load.SOURCE, SfrReactorBlockEntity.class);
        put(Load.SOURCE, StationBatteryBlockEntity.class);
        put(Load.SOURCE, SteamEngineBlockEntity.class);
        put(Load.SOURCE, TrigaBlockEntity.class);
        // пассивный блок
        put(Load.PASSIVE, AirSeparatorBlockEntity.class);
        put(Load.PASSIVE, AutomationBlockEntity.class);
        put(Load.PASSIVE, BopRamBlockEntity.class);
        put(Load.PASSIVE, ConstructionSiteBlockEntity.class);
        put(Load.PASSIVE, CpTestPostBlockEntity.class);
        put(Load.PASSIVE, CryoTankBlockEntity.class);
        put(Load.PASSIVE, DeaeratorBlockEntity.class);
        put(Load.PASSIVE, DocumentLecternBlockEntity.class);
        put(Load.PASSIVE, DrawPlateBlockEntity.class);
        put(Load.PASSIVE, DustCatcherBlockEntity.class);
        put(Load.PASSIVE, FlareKnockoutDrumBlockEntity.class);
        put(Load.PASSIVE, FlareSealDrumBlockEntity.class);
        put(Load.PASSIVE, FuelChannelBlockEntity.class);
        put(Load.PASSIVE, FuelRackBlockEntity.class);
        put(Load.PASSIVE, GasStorageBulletBlockEntity.class);
        put(Load.PASSIVE, HeadframeSteelBlockEntity.class);
        put(Load.PASSIVE, dev.denfry.promptcritical.mod.drilling.DerrickRealBlockEntity.class);
        put(Load.PASSIVE, dev.denfry.promptcritical.mod.drilling.DrawworksRealBlockEntity.class);
        put(Load.PASSIVE, HlwVaultBlockEntity.class);
        put(Load.PASSIVE, HydrotestPumpBlockEntity.class);
        put(Load.PASSIVE, IngotMoldBlockEntity.class);
        put(Load.PASSIVE, InstrumentBlockEntity.class);
        put(Load.PASSIVE, IrradiationChannelBlockEntity.class);
        put(Load.PASSIVE, LadleBlockEntity.class);
        put(Load.PASSIVE, LineBlindBlockEntity.class);
        put(Load.PASSIVE, LockoutBoxBlockEntity.class);
        put(Load.PASSIVE, MaintenancePanelBlockEntity.class);
        put(Load.PASSIVE, ManwayBlockEntity.class);
        put(Load.PASSIVE, MudHopperBlockEntity.class);
        put(Load.PASSIVE, PatrolCheckpointBlockEntity.class);
        put(Load.PASSIVE, PermitBoardBlockEntity.class);
        put(Load.PASSIVE, PigTrapBlockEntity.class);
        put(Load.PASSIVE, PressureGaugeBlockEntity.class);
        put(Load.PASSIVE, PressurizerBlockEntity.class);
        put(Load.PASSIVE, QuenchTankBlockEntity.class);
        put(Load.PASSIVE, RecordingGaugeBlockEntity.class);
        put(Load.PASSIVE, RelayRackBlockEntity.class);
        put(Load.PASSIVE, RepositoryBlockEntity.class);
        put(Load.PASSIVE, RescueTripodBlockEntity.class);
        put(Load.PASSIVE, SandMoldBlockEntity.class);
        put(Load.PASSIVE, SeparatorBlockEntity.class);
        put(Load.PASSIVE, dev.denfry.promptcritical.mod.production.OilTankBlockEntity.class);
        put(Load.PASSIVE, SignageBlockEntity.class);
        put(Load.PASSIVE, SmithingAnvilBlockEntity.class);
        // сборочный стенд ручной ступени (18 §18.8.2): балансировка на ножах, обкатка вручную
        put(Load.PASSIVE, dev.denfry.promptcritical.mod.manufacturing.AssemblyStandBlockEntity.class);
        // светильники берут свои ватты у осветительного щитка или РИТЭГа (LightingBoards), не у машинного щита
        put(Load.OWN, dev.denfry.promptcritical.mod.grid.LampBlock.LampBlockEntity.class);
        // настенные часы — механические
        put(Load.PASSIVE, dev.denfry.promptcritical.mod.content.WallClockBlock.WallClockBlockEntity.class);
        // ГГС и операторский терминал (16 §16.3.4, §16.4.1): электроника 0,4 кВ от ИБП
        put(Load.IC, dev.denfry.promptcritical.mod.pa.IntercomStationBlockEntity.class);
        put(Load.IC, dev.denfry.promptcritical.mod.pa.PlantLoudspeakerBlockEntity.class);
        put(Load.IC, dev.denfry.promptcritical.mod.scada.OperatorTerminalBlockEntity.class);
        // санпропускник 04b: шкафы — мебель; контроллер ЗКД — табличка с журналом, дозу берёт с приборов зоны
        put(Load.PASSIVE, dev.denfry.promptcritical.mod.radiation.LockerBlockEntity.class);
        put(Load.PASSIVE, dev.denfry.promptcritical.mod.radiation.ZoneControllerBlockEntity.class);
        put(Load.PASSIVE, SteamGeneratorBlockEntity.class);
        put(Load.PASSIVE, TideGaugeBlockEntity.class);
        put(Load.PASSIVE, VentStackBlockEntity.class);
        put(Load.PASSIVE, WaterIntakeBlockEntity.class);
        put(Load.PASSIVE, WellheadBlockEntity.class);

        // установки со своим вводом 6,3 кВ
        put(Load.ACTIVE_MV, MvProcessBlockEntity.class);
        // ванны на постоянном токе выпрямителя
        put(Load.OWN, ReductionPotBlockEntity.class);
        put(Load.OWN, ElectrorefiningCellBlockEntity.class);
        put(Load.OWN, FluorineCellBlockEntity.class);
        // свой ввод 6,3 кВ: GridLoad
        put(Load.GRID, DesalterBlockEntity.class);
        put(Load.GRID, LrwEvaporatorBlockEntity.class);
        // насосы раствора учтены в номинале печи риформинга метана
        put(Load.PASSIVE, Co2AbsorberBlockEntity.class);

        // аргон из труб и ферросплавы по кнопке; дымосос трубчатой печи — в номинале атмосферной колонны
        put(Load.PASSIVE, LadleStationBlockEntity.class);
        put(Load.PASSIVE, TubeFurnaceBlockEntity.class);

        // ПЛК и ПАЗ: оперативное питание 24 В от блока питания автоматики по цепям
        put(Load.OWN, AutomationPanelBlockEntity.class);
        put(Load.OWN, PlcBlockEntity.class);
        // ядро щитовой оценивает помещение, само ничего не берёт
        put(Load.PASSIVE, ControlRoomCoreBlockEntity.class);
        // пульты котла (без питания — сброс стержней) и домны (без питания — гаснут приборы)
        put(Load.IC, PileControllerBlockEntity.class);
        put(Load.IC, BlastFurnaceControllerBlockEntity.class);

        // общий BE устройства: по id блока
        BY_BLOCK.put("degasification_borehole", Load.PASSIVE);
        // шкаф горного диспетчера: связь и сигнализация — приборная нагрузка
        BY_BLOCK.put("mine_dispatch_cabinet", Load.IC);
    }

    private MachineLoads() {
    }

    private static void put(Load load, Class<? extends BlockEntity> type) {
        BY_CLASS.put(type, load);
    }

    /**
     * Класс нагрузки BE блока {@code block}.
     *
     * @return {@code null} — машина не разобрана
     */
    @Nullable
    public static Load of(BlockEntity be, ResourceLocation block) {
        if (be.getClass() == DeviceBlockEntity.class) return BY_BLOCK.get(block.getPath());
        for (Class<?> c = be.getClass(); c != null && c != BlockEntity.class; c = c.getSuperclass()) {
            Load l = BY_CLASS.get(c);
            if (l != null) return l;
        }
        if (be instanceof GridDevice) return Load.GRID;
        if (be instanceof KineticBlockEntity) return Load.SHAFT;
        return null;
    }
}
