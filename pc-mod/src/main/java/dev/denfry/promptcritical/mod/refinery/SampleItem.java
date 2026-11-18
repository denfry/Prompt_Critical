package dev.denfry.promptcritical.mod.refinery;

import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.refining.GasolineBlending;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Пробоотборник {@code sample_bottle} и паспорт партии {@code batch_certificate} (09 §9.9.13): ПКМ бутылкой по
 * станции смешения, трубе с баком или машине, где есть бензин или его компонент, — проба с компонентом
 * {@code sample_data}. Истинное качество пробы игроку не видно, его показывает лаборатория ({@code quality_lab}).
 */
public final class SampleItem extends Item {
    private final boolean certificate;

    public SampleItem(Properties props, boolean certificate) {
        super(props);
        this.certificate = certificate;
    }

    /** Качество компонентов смешения по жидкости (эталонные данные 09 §9.9.14). */
    @Nullable
    public static GasolineBlending.Component component(Fluid f) {
        Map<Fluid, GasolineBlending.Component> m = Map.of(PcFluids.GASOLINE.source.get(), GasolineBlending.LSR,
                PcFluids.REFORMATE.source.get(), GasolineBlending.REFORMATE, PcFluids.FCC_GASOLINE.source.get(), GasolineBlending.FCC_GASOLINE,
                PcFluids.BUTANE.source.get(), GasolineBlending.BUTANE);
        return m.get(f);
    }

    /** Проба из блока: партия станции смешения или компонент в любом баке; {@code null} — брать нечего. */
    @Nullable
    public static RefineryComponents.SampleData sample(Level level, BlockPos pos, @Nullable net.minecraft.core.Direction face) {
        long now = level.getGameTime();
        if (level.getBlockEntity(pos) instanceof GasolineBlenderBlockEntity b) {
            GasolineBlending.Properties p = b.blendProperties();
            if (p == null) return null;
            return new RefineryComponents.SampleData(BuiltInRegistries.FLUID.getKey(PcFluids.MOTOR_GASOLINE.source.get()).toString(),
                    pos.asLong(), now, RefineryComponents.FuelQuality.of(p), Optional.empty(), -1, b.grade().id());
        }
        IFluidHandler h = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face);
        if (h == null) return null;
        for (int t = 0; t < h.getTanks(); t++) {
            FluidStack fs = h.getFluidInTank(t);
            GasolineBlending.Component c = fs.isEmpty() ? null : component(fs.getFluid());
            if (c != null)
                return new RefineryComponents.SampleData(BuiltInRegistries.FLUID.getKey(fs.getFluid()).toString(), pos.asLong(), now,
                        RefineryComponents.FuelQuality.of(c), Optional.empty(), -1, "");
        }
        return null;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        ItemStack stack = ctx.getItemInHand();
        if (certificate || stack.has(RefineryComponents.SAMPLE_DATA.get())) return InteractionResult.PASS;
        Level level = ctx.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        Player player = ctx.getPlayer();
        RefineryComponents.SampleData d = sample(level, ctx.getClickedPos(), ctx.getClickedFace());
        if (d == null) {
            if (player != null) player.displayClientMessage(ConversionPanels.NOTHING_TO_SAMPLE.get(), true);
            return InteractionResult.FAIL;
        }
        ItemStack filled = new ItemStack(PcItems.SAMPLE_BOTTLE.get());
        filled.set(RefineryComponents.SAMPLE_DATA.get(), d);
        stack.shrink(1);
        if (player != null && !player.getInventory().add(filled)) player.drop(filled, false);
        if (player != null) player.displayClientMessage(ConversionPanels.SAMPLED.get(fluidName(d)), true);
        return InteractionResult.CONSUME;
    }

    static Component fluidName(RefineryComponents.SampleData d) {
        Fluid f = BuiltInRegistries.FLUID.get(net.minecraft.resources.ResourceLocation.parse(d.fluid()));
        return f.getFluidType().getDescription();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        RefineryComponents.SampleData d = stack.get(RefineryComponents.SAMPLE_DATA.get());
        if (d == null) {
            if (!certificate) tooltip.add(ConversionPanels.EMPTY_BOTTLE.get().withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(fluidName(d).copy().withStyle(ChatFormatting.GRAY));
        if (d.measured().isEmpty()) {
            tooltip.add(ConversionPanels.NOT_ANALYSED.get().withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        ConversionPanels.qualityLines(d.measured().get(), tooltip);
        if (!d.grade().isEmpty()) {
            GasolineBlending.Grade g = GasolineBlenderBlockEntity.GRADES.stream().filter(x -> x.id().equals(d.grade())).findFirst().orElse(null);
            if (g != null) {
                boolean ok = d.measured().get().onSpec(g);
                tooltip.add((ok ? ConversionPanels.ON_GRADE : ConversionPanels.OFF_GRADE).get(ConversionPanels.gradeName(g))
                        .withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
        }
    }
}
