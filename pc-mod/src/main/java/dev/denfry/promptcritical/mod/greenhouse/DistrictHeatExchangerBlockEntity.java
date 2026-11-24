package dev.denfry.promptcritical.mod.greenhouse;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.environment.EnvironmentService;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.powerhouse.SteamTurbineBlockEntity;
import dev.denfry.promptcritical.sim.thermal.DistrictHeating;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Сетевой подогреватель ТЭЦ {@code district_heat_exchanger} (07 §7.14.2): стоит под паровой турбиной вместо
 * конденсатора, принимает её отработавший пар при давлении насыщения температуры подачи теплосети и отдаёт теплоту
 * конденсации поселениям в радиусе {@value #RADIUS} блоков (поиск — раз в минуту и не чаще раза в 5 с по клику). Объём зданий поселения — по кроватям (точкам интереса
 * «дом»), {@value #HOUSE_M3} м³ на кровать [оценка: деревенский дом около 6 × 5 × 5 м].
 *
 * <p>Упрощение: трубопроводы теплосети не строятся — подогреватель отдаёт тепло напрямую поселениям в радиусе, без
 * гидравлики и потерь в сети; недоотпуск зимой показывается, но штраф 14 не начисляется (экономики плат пока нет).
 */
public final class DistrictHeatExchangerBlockEntity extends DeviceBlockEntity implements FoundryDevice {
    public static final int RADIUS = 128, HOUSE_M3 = 150, RESCAN_TICKS = 1200, USE_RESCAN_TICKS = 100;

    private int beds = -1, ticks;
    private long lastScan = Long.MIN_VALUE;
    private double supplyW, loadW, outdoorC;

    public DistrictHeatExchangerBlockEntity(BlockPos pos, BlockState state) {
        super(GreenhouseBlockEntities.DISTRICT_HEAT_EXCHANGER.get(), pos, state);
    }

    /** Кроватей поселений в радиусе (по последнему пересчёту). */
    public int beds() {
        return Math.max(0, beds);
    }

    public double supplyW() {
        return supplyW;
    }

    public double loadW() {
        return loadW;
    }

    /** Отдано потребителям, Вт: меньшее из пара и нагрузки. */
    public double deliveredW() {
        return Math.min(supplyW, loadW);
    }

    /** Пересчитать кровати поселений вокруг. */
    public void rescan(ServerLevel sl) {
        lastScan = sl.getGameTime();
        beds = (int) sl.getPoiManager().getInRange(t -> t.is(PoiTypes.HOME), worldPosition, RADIUS, PoiManager.Occupancy.ANY).count();
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl)) return;
        if (beds < 0 || ticks % RESCAN_TICKS == 0) rescan(sl);
        if (ticks++ % 20 == 0) sample(sl);
    }

    /** Пересчитать погоду, тепло пара и нагрузку поселений. */
    public void sample(ServerLevel sl) {
        outdoorC = EnvironmentService.sample(sl, worldPosition, 2).airTempC();
        supplyW = DistrictHeating.heatFromSteamW(SteamTurbineBlockEntity.exhaustSteamKgS(sl, worldPosition));
        loadW = DistrictHeating.heatingLoadW((double) beds() * HOUSE_M3, outdoorC);
    }

    @Override
    public InteractionResult use(Player player) {
        if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        // по клику — не чаще раза в 5 с: поиск кроватей по точкам интереса дорогой
        if (sl.getGameTime() - lastScan >= USE_RESCAN_TICKS) rescan(sl);
        sample(sl);
        player.displayClientMessage(GreenhouseText.msg("dh_status", f(outdoorC), f(DistrictHeating.supplyC(outdoorC)), f(DistrictHeating.returnC(outdoorC)),
                f(DistrictHeating.exhaustPa(dev.denfry.promptcritical.sim.thermo.WaterProperties.tables(), outdoorC) / 1e5)), false);
        player.displayClientMessage(GreenhouseText.msg("dh_heat", f(supplyW / 1e3), f(loadW / 1e3), beds(), f(deliveredW() / 1e3),
                String.format(Locale.ROOT, "%.2f", DistrictHeating.networkWaterKgS(deliveredW(), outdoorC))), false);
        if (loadW > 0 && supplyW < loadW)
            player.displayClientMessage(GreenhouseText.msg("dh_short", Math.round(100 * (1 - supplyW / loadW))).withStyle(ChatFormatting.GOLD), false);
        return InteractionResult.SUCCESS;
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return false;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
