package dev.denfry.promptcritical.mod.sound.client;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.boiler.FireTubeBoilerBlockEntity;
import dev.denfry.promptcritical.mod.chlor.BrineTreaterBlockEntity;
import dev.denfry.promptcritical.mod.chlor.EdcCrackerBlockEntity;
import dev.denfry.promptcritical.mod.chem.CrackedGasUnitBlockEntity;
import dev.denfry.promptcritical.mod.chlor.EdcUnitBlockEntity;
import dev.denfry.promptcritical.mod.foundry.CupolaBlockEntity;
import dev.denfry.promptcritical.mod.foundry.MachineToolBlockEntity;
import dev.denfry.promptcritical.mod.foundry.ProductionToolBlockEntity;
import dev.denfry.promptcritical.mod.foundry.SandMoldBlockEntity;
import dev.denfry.promptcritical.mod.foundry.ShakeoutGrateBlockEntity;
import dev.denfry.promptcritical.mod.grid.ElectricMotorBlockEntity;
import dev.denfry.promptcritical.mod.grid.TransformerBlockEntity;
import dev.denfry.promptcritical.mod.mine.DegasificationPumpBlockEntity;
import dev.denfry.promptcritical.mod.mine.LongwallShearerBlockEntity;
import dev.denfry.promptcritical.mod.mine.MainMineFanBlockEntity;
import dev.denfry.promptcritical.mod.mine.MineDrainagePumpBlockEntity;
import dev.denfry.promptcritical.mod.mine.MineHoistBlockEntity;
import dev.denfry.promptcritical.mod.nitrogen.SteamReformerBlockEntity;
import dev.denfry.promptcritical.mod.npp.BwrReactorBlockEntity;
import dev.denfry.promptcritical.mod.npp.SteamGeneratorBlockEntity;
import dev.denfry.promptcritical.mod.nuclear.CascadeBlockEntity;
import dev.denfry.promptcritical.mod.refinery.ClausUnitBlockEntity;
import dev.denfry.promptcritical.mod.methanol.MethanolConverterBlockEntity;
import dev.denfry.promptcritical.mod.nitrogen.AmmoniaConverterBlockEntity;
import dev.denfry.promptcritical.mod.npp.CanduFuelling;
import dev.denfry.promptcritical.mod.refinery.DelayedCokerBlockEntity;
import dev.denfry.promptcritical.mod.npp.CanduReactorBlockEntity;
import dev.denfry.promptcritical.mod.sound.PcSfx;
import dev.denfry.promptcritical.sim.metallurgy.ProductionToolUnit;
import dev.denfry.promptcritical.sim.mining.Hoist;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * Звуковой фон цеха на клиенте: раз в {@link #PERIOD} тика обходит машины в ближних чанках и держит петлю работы у
 * каждой работающей — по тому, что клиент видит: свойство {@code running}/{@code lit} блока, обороты вала Create.
 * Громкость и высота — от оборотов, где они есть (насос на 32 об/мин гудит ниже и тише, чем на 64). Разовые звуки
 * (такт выхлопа, ход резца, удар стола, щелчок аппарата) играют сами машины.
 */
public final class MachineSoundscape {
    private static final int PERIOD = 4, CHUNKS = 2;
    /** Номинальные обороты вала машин на Create, об/мин. */
    private static final float NOMINAL_RPM = 64;
    private static final Map<Long, MachineLoopSound> PLAYING = new HashMap<>();

    /** Что звучит у машины сейчас: петля, громкость, высота; {@code null} — молчит. */
    private record Loop(PcSfx sfx, float volume, float pitch) {
    }

    private MachineSoundscape() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(MachineSoundscape::tick);
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        PLAYING.values().removeIf(MachineLoopSound::done);
        if (level == null || mc.player == null || mc.isPaused() || level.getGameTime() % PERIOD != 0) return;
        ChunkPos c = mc.player.chunkPosition();
        for (int dx = -CHUNKS; dx <= CHUNKS; dx++) {
            for (int dz = -CHUNKS; dz <= CHUNKS; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(c.x + dx, c.z + dz, false);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    Loop l = loop(be);
                    if (l != null) keep(mc, be.getBlockPos(), l);
                }
            }
        }
    }

    private static void keep(Minecraft mc, BlockPos pos, Loop l) {
        long key = pos.asLong() * 31 + l.sfx().ordinal();
        MachineLoopSound s = PLAYING.get(key);
        if (s != null && !s.done()) {
            s.keep(l.volume(), l.pitch());
            return;
        }
        s = new MachineLoopSound(l.sfx(), pos, l.volume(), l.pitch());
        PLAYING.put(key, s);
        mc.getSoundManager().play(s);
    }

    private static boolean flag(BlockState s, net.minecraft.world.level.block.state.properties.BooleanProperty p) {
        return s.hasProperty(p) && s.getValue(p);
    }

    /** Доля номинальных оборотов вала Create (0 — стоит). */
    private static float shaft(BlockEntity be) {
        return be instanceof KineticBlockEntity k ? Math.abs(k.getSpeed()) / NOMINAL_RPM : 0;
    }

    @Nullable
    private static Loop loop(BlockEntity be) {
        BlockState s = be.getBlockState();
        boolean running = flag(s, PcProperties.RUNNING);
        float n = shaft(be);
        return switch (be) {
            case FireTubeBoilerBlockEntity b -> flag(s, BlockStateProperties.LIT) ? new Loop(PcSfx.BOILER_FIRE, 0.8f, 1) : null;
            case CupolaBlockEntity b -> flag(s, BlockStateProperties.LIT) ? new Loop(PcSfx.CUPOLA_BLAST, 0.9f, 0.8f + 0.2f * n) : null;
            case ElectricMotorBlockEntity m -> running || n > 0 ? new Loop(PcSfx.ELECTRIC_MOTOR, 0.5f, 1) : null;
            case TransformerBlockEntity t -> t.hum() > 0 ? new Loop(PcSfx.TRANSFORMER_HUM, 0.25f + 0.05f * t.hum(), 1) : null;
            // Guibal: ротор ⌀9 м на 60–90 об/мин — низкий гул вместо воя крыльчатки
            case MainMineFanBlockEntity f -> running ? (f.wholeModelId() != null ? new Loop(PcSfx.MINE_FAN, 1.6f, 0.55f)
                    : new Loop(PcSfx.MINE_FAN, 1.2f, 1)) : null;
            case MineDrainagePumpBlockEntity p -> running ? new Loop(PcSfx.PUMP, 0.4f + 0.4f * n, 0.6f + 0.4f * n) : null;
            case DegasificationPumpBlockEntity p -> running ? new Loop(PcSfx.PUMP, 0.3f + 0.3f * n, 0.7f + 0.4f * n) : null;
            // лебёдка: громче и выше с ростом скорости каната до предельной Hoist.MAX_SPEED
            case MineHoistBlockEntity h -> Math.abs(h.speedMs()) > 0.05
                    ? new Loop(PcSfx.HOIST_WINCH, 0.5f + (float) (0.6 * Math.abs(h.speedMs()) / Hoist.MAX_SPEED),
                    0.7f + (float) (0.5 * Math.abs(h.speedMs()) / Hoist.MAX_SPEED)) : null;
            case LongwallShearerBlockEntity sh -> running ? new Loop(PcSfx.SHEARER_CUTTING, 1.0f, 1) : null;
            case MachineToolBlockEntity t -> running ? new Loop(PcSfx.LATHE, 0.5f, t.isDrillPress() ? 1.3f : 0.8f + 0.2f * n) : null;
            case ProductionToolBlockEntity t -> running && t.kind() != ProductionToolUnit.Kind.SHAPER
                    ? new Loop(t.kind() == ProductionToolUnit.Kind.MILLING ? PcSfx.MILLING : PcSfx.GRINDER, 0.5f, 0.8f + 0.2f * n)
                    : null;
            case ShakeoutGrateBlockEntity g -> running ? new Loop(PcSfx.SHAKEOUT_RATTLE, 0.6f, 0.8f + 0.2f * n) : null;
            case SandMoldBlockEntity m -> s.hasProperty(PcProperties.MOLD_STAGE) && s.getValue(PcProperties.MOLD_STAGE) == PcProperties.MoldStage.POURED
                    ? new Loop(PcSfx.METAL_POUR, 0.5f, 1) : null;
            // кипящий реактор на мощности: шум потока пароводяной смеси в корпусе и паропроводах
            case BwrReactorBlockEntity r -> flag(s, BlockStateProperties.LIT) ? new Loop(PcSfx.PUMP, 0.35f, 0.55f) : null;
            case SteamReformerBlockEntity r -> flag(s, BlockStateProperties.LIT) ? new Loop(PcSfx.BOILER_FIRE, 0.9f, 0.85f) : null;
            case ClausUnitBlockEntity c -> running ? new Loop(PcSfx.BOILER_FIRE, 0.45f, 1.1f) : null;
            case EdcCrackerBlockEntity c -> flag(s, BlockStateProperties.LIT) ? new Loop(PcSfx.BOILER_FIRE, 0.6f, 1.0f) : null;
            case BrineTreaterBlockEntity b -> running ? new Loop(PcSfx.PUMP, 0.3f, 0.8f) : null;
            // преобразователь частоты каскада: тонкий вой на высокой частоте питания моторов центрифуг
            case CascadeBlockEntity c -> running ? new Loop(PcSfx.ELECTRIC_MOTOR, 0.35f, 1.8f) : null;
            case SteamGeneratorBlockEntity g -> flag(s, BlockStateProperties.LIT) ? new Loop(PcSfx.PUMP, 0.2f, 0.5f) : null;
            // печь пиролиза: подовые горелки гудят, пока печь в работе или на выжиге кокса
            case dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity f -> flag(s, BlockStateProperties.LIT) ? new Loop(PcSfx.BOILER_FIRE, 1.0f, 0.8f) : null;
            // гиперкомпрессор ПЭВД: тяжёлый поршневой стук, чаще с оборотами вала
            case dev.denfry.promptcritical.mod.chem.LdpeReactorBlockEntity r -> running || n > 0 ? new Loop(PcSfx.PUMP, 0.5f + 0.4f * Math.min(1, n), 0.5f + 0.3f * Math.min(1, n)) : null;
            // гидроочистка: сырьевой насос и циркуляционный компрессор водорода за стеной
            case dev.denfry.promptcritical.mod.refinery.HydrotreaterBlockEntity h -> running ? new Loop(PcSfx.PUMP, 0.3f, 1.1f) : null;
            // воздушный компрессор агрегата азотной кислоты: гул улитки, выше с оборотами
            case dev.denfry.promptcritical.mod.nitrogen.NitricAcidBlockEntity a -> running || n > 0 ? new Loop(PcSfx.MINE_FAN, 0.6f, 0.8f + 0.4f * Math.min(1, n)) : null;
            // электролизёр: гудит выпрямленный ток в шинах и выпрямитель
            case dev.denfry.promptcritical.mod.chlor.CellRoomBlockEntity c -> running ? new Loop(PcSfx.TRANSFORMER_HUM, 0.3f, 1) : null;
            // ГЦН: насос гудит по оборотам вала
            case dev.denfry.promptcritical.mod.npp.PrimaryPumpBlockEntity p -> running || n > 0 ? new Loop(PcSfx.PUMP, 0.4f + 0.4f * Math.min(1, n), 0.6f + 0.5f * Math.min(1, n)) : null;
            // каньон PUREX: вентиляция каньона, пока линия в работе
            case dev.denfry.promptcritical.mod.reprocessing.PurexCellBlockEntity c -> running ? new Loop(PcSfx.MINE_FAN, 0.3f, 1.2f) : null;
            // печь восстановления UO2: гул нагревателей и их трансформатора
            case dev.denfry.promptcritical.mod.nuclear.Uo2ConversionBlockEntity u -> flag(s, BlockStateProperties.LIT) ? new Loop(PcSfx.TRANSFORMER_HUM, 0.3f, 0.9f) : null;
            // сервер MES: вентиляторы серверов в стойке
            case dev.denfry.promptcritical.mod.mes.MesServerBlockEntity m -> flag(s, BlockStateProperties.LIT) ? new Loop(PcSfx.ELECTRIC_MOTOR, 0.15f, 1.8f) : null;
            // CANDU: моторы моста и тележки перегрузочной машины на ходу, иначе — гул насосов теплоносителя по мощности
            case CanduReactorBlockEntity r -> CanduFuelling.moving(r.refuelChannel(), r.getLevel().getGameTime() - r.refuelTick())
                    ? new Loop(PcSfx.ELECTRIC_MOTOR, 0.6f, 1.2f)
                    : r.viewPower() > 0.01f ? new Loop(PcSfx.PUMP, 0.25f + 0.35f * Math.min(1, r.viewPower()), 0.7f) : null;
            // коксование: насос высокого давления гидрорезки
            case DelayedCokerBlockEntity c -> running ? new Loop(PcSfx.PUMP, 0.6f, 0.9f) : null;
            // технологические установки I3: компрессоры пирогаза и циркуляторы синтеза, насосы ЭДХ
            case CrackedGasUnitBlockEntity g -> running ? new Loop(PcSfx.COMPRESSOR, 1.0f, 0.9f) : null;
            case EdcUnitBlockEntity e -> running ? new Loop(PcSfx.PUMP, 0.35f, 1.1f) : null;
            case MethanolConverterBlockEntity m -> running ? new Loop(PcSfx.COMPRESSOR, 0.4f + 0.4f * n, 0.8f + 0.3f * n) : null;
            case AmmoniaConverterBlockEntity a -> running ? new Loop(PcSfx.COMPRESSOR, 0.4f + 0.4f * n, 0.7f + 0.3f * n) : null;
            // НТС: пропановый холодильный компрессор, пока установка в холодном режиме
            case dev.denfry.promptcritical.mod.gas.GasPlantBlockEntity g -> flag(s, BlockStateProperties.LIT) ? new Loop(PcSfx.COMPRESSOR, 0.5f, 1.2f) : null;
            // компенсатор давления: гудят тиристорные ключи и шины блоков нагревателей
            case dev.denfry.promptcritical.mod.npp.PressurizerBlockEntity p -> flag(s, BlockStateProperties.LIT) ? new Loop(PcSfx.TRANSFORMER_HUM, 0.35f, 1) : null;
            default -> null;
        };
    }
}
