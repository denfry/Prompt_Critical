package dev.denfry.promptcritical.mod.radiation;

import dev.denfry.promptcritical.mod.radiation.ContaminationData.SprayerCharge;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.radiation.ContaminationTransfer;
import dev.denfry.promptcritical.sim.radiation.DeconMethod;
import dev.denfry.promptcritical.sim.radiation.SurfaceContamination;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Распылитель дезактивации с пылесосом-сборником смыва: ПКМ по баку или аппарату с трубами — слить собранный смыв
 * ({@code contaminated_decon_solution}) и набрать раствор: воду, лимонно-щавелевый ({@code decon_solution}) или щелочной
 * перманганат ({@code decon_permanganate}); ПКМ по поверхности — обработать верхнюю грань блока (1 м²) нормой раствора
 * способа ({@link DeconMethod#liquidLPerM2}). Окислительно-восстановительная обработка — двумя проходами: перманганат
 * окисляет плёнку (блок помечается), следующий проход кислотой по этому блоку даёт {@link DeconMethod#PERMANGANATE_OXALIC}.
 * Весь пролитый раствор собирается обратно со снятой активностью — это жидкие РАО.
 */
public final class DeconSprayerItem extends Item {
    /** Бак раствора и бак смыва, л: передвижная установка, а не ранцевый опрыскиватель. */
    public static final double CAPACITY_L = 40;

    public DeconSprayerItem(Properties props) {
        super(props.stacksTo(1));
    }

    public static SprayerCharge charge(ItemStack st) {
        SprayerCharge c = st.get(ContaminationData.SPRAYER.get());
        return c == null ? SprayerCharge.EMPTY : c;
    }

    private static String id(Fluid f) {
        return BuiltInRegistries.FLUID.getKey(f).toString();
    }

    private static Fluid[] solutions() {
        return new Fluid[] {Fluids.WATER, PcFluids.DECON_SOLUTION.source.get(), PcFluids.DECON_PERMANGANATE.source.get()};
    }

    /** Способ для раствора в баке и состояния поверхности; {@code null} — не раствор дезактивации. */
    @Nullable
    static DeconMethod method(String fluid, boolean oxidised) {
        if (fluid.equals(id(Fluids.WATER))) return DeconMethod.WATER_RINSE;
        if (fluid.equals(id(PcFluids.DECON_SOLUTION.source.get()))) return oxidised ? DeconMethod.PERMANGANATE_OXALIC : DeconMethod.CITRIC_OXALIC;
        if (fluid.equals(id(PcFluids.DECON_PERMANGANATE.source.get()))) return DeconMethod.ALKALINE_PERMANGANATE;
        return null;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level) || ctx.getPlayer() == null) return InteractionResult.SUCCESS;
        ItemStack st = ctx.getItemInHand();
        IFluidHandler h = level.getCapability(Capabilities.FluidHandler.BLOCK, ctx.getClickedPos(), ctx.getClickedFace());
        if (h != null) exchange(st, h);
        else spray(level, ctx.getClickedPos(), st, ctx.getPlayer());
        ctx.getPlayer().displayClientMessage(status(charge(st)), true);
        return InteractionResult.CONSUME;
    }

    /** Слить смыв в {@code h}, затем набрать из него раствор (тот же, что в баке, или любой, если бак пуст). */
    public static void exchange(ItemStack st, IFluidHandler h) {
        SprayerCharge c = charge(st);
        double wasteL = c.wasteL(), wasteBq = c.wasteBq();
        int mb = (int) Math.floor(wasteL);
        if (mb > 0) {
            int filled = h.fill(new FluidStack(PcFluids.CONTAMINATED_DECON_SOLUTION.source.get(), mb), IFluidHandler.FluidAction.EXECUTE);
            if (filled > 0) {
                wasteBq *= 1 - filled / wasteL;
                wasteL -= filled;
            }
        }
        String fluid = c.fluid();
        double litres = c.litres();
        int room = (int) Math.floor(CAPACITY_L - litres);
        if (room > 0)
            for (Fluid f : solutions()) {
                if (litres > 0 && !fluid.equals(id(f))) continue;
                FluidStack got = h.drain(new FluidStack(f, room), IFluidHandler.FluidAction.EXECUTE);
                if (got.isEmpty()) continue;
                fluid = id(f);
                litres += got.getAmount();
                break;
            }
        if (litres <= 0) fluid = "";
        st.set(ContaminationData.SPRAYER.get(), new SprayerCharge(fluid, litres, wasteL, wasteBq));
    }

    /**
     * Обработать верхнюю грань блока {@code pos}.
     *
     * @return прошла ли обработка (хватило раствора и места под смыв)
     */
    public static boolean spray(ServerLevel level, BlockPos pos, ItemStack st, @Nullable Player player) {
        SprayerCharge c = charge(st);
        ContaminationMap m = ContaminationMap.of(level);
        DeconMethod method = method(c.fluid(), m.oxidised(pos));
        if (method == null) return false;
        double need = method.liquidLPerM2 * ContaminationTransfer.BLOCK_FACE_CM2 / 1e4;
        if (c.litres() < need || CAPACITY_L - c.wasteL() < need) return false;
        SurfaceContamination before = m.get(pos), after = before.after(method);
        m.set(pos, after);
        if (method == DeconMethod.ALKALINE_PERMANGANATE) m.setOxidised(pos, true);
        else if (method == DeconMethod.PERMANGANATE_OXALIC) m.setOxidised(pos, false);
        double removedBq = (before.total() - after.total()) * ContaminationTransfer.BLOCK_FACE_CM2;
        double left = c.litres() - need;
        st.set(ContaminationData.SPRAYER.get(), new SprayerCharge(left > 0 ? c.fluid() : "", Math.max(0, left), c.wasteL() + need,
                c.wasteBq() + removedBq));
        var at = pos.getCenter();
        level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.55, at.z, 30, 0.4, 0.05, 0.4, 0.1);
        level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.4f, 1.6f);
        if (player != null && before.total() > 0)
            player.displayClientMessage(ContaminationText.msg("sprayed", ContaminationText.num(before.total()), ContaminationText.num(after.total()))
                    .withStyle(ChatFormatting.AQUA), false);
        return true;
    }

    private static Component status(SprayerCharge c) {
        Component fluid = c.fluid().isEmpty() ? ContaminationText.msg("sprayer.empty")
                : BuiltInRegistries.FLUID.get(net.minecraft.resources.ResourceLocation.parse(c.fluid())).getFluidType().getDescription();
        return ContaminationText.msg("sprayer.status", fluid, String.format(Locale.ROOT, "%.0f", c.litres()),
                String.format(Locale.ROOT, "%.0f", c.wasteL()), ContaminationText.num(c.wasteBq() / 1e6));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(status(charge(stack)).copy().withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.promptcritical.decon_sprayer.tip").withStyle(ChatFormatting.DARK_GRAY));
    }
}
