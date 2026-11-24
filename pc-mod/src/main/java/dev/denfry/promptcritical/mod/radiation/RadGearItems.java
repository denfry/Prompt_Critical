package dev.denfry.promptcritical.mod.radiation;

import dev.denfry.promptcritical.mod.environment.RadiationService;
import dev.denfry.promptcritical.mod.sound.PcSfx;
import dev.denfry.promptcritical.sim.radiation.GeigerCounter;
import dev.denfry.promptcritical.sim.radiation.PersonalDosimeter;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Приборы индивидуального контроля и йодная профилактика 04b §4b.7.1, §4b.8.3. Счёт дозы — {@link RadGearService}. */
public final class RadGearItems {
    private RadGearItems() {
    }

    /**
     * Бытовой счётчик Гейгера (СБМ-20 без коррекции мёртвого времени): в слабом поле честный и «гуляет», в сильном
     * захлёбывается и показывает меньше, чем есть, вплоть до нуля — и щёлкает реже.
     */
    public static final class Geiger extends Item {
        public Geiger(Properties props) {
            super(props.stacksTo(1));
        }

        @Override
        public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
            if (!level.isClientSide || !(entity instanceof Player player)) return;
            if (!selected && player.getOffhandItem() != stack) return;
            Double r = stack.get(RadGear.GEIGER_READING.get());
            if (r == null) return;
            // щёлкает наблюдаемый счёт трубки: в насыщении щелчков меньше, а не больше
            double cps = r * GeigerCounter.SBM20.cpsPerUSvH();
            if (level.random.nextDouble() < 1 - Math.exp(-cps / 20))
                level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(), PcSfx.GEIGER_CLICK.get(), SoundSource.PLAYERS, 0.5f,
                        0.95f + level.random.nextFloat() * 0.1f, false);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.promptcritical.geiger_counter.tip").withStyle(ChatFormatting.GRAY));
            Double r = stack.get(RadGear.GEIGER_READING.get());
            if (r != null) tooltip.add(RadGearText.msg("geiger", String.format(Locale.ROOT, "%.2f", r)).withStyle(ChatFormatting.AQUA));
        }
    }

    /** Электронный индивидуальный дозиметр: ПКМ — показание, ПКМ с Shift — следующий пресет уставок и сброс дозы. */
    public static final class Epd extends Item {
        public Epd(Properties props) {
            super(props.stacksTo(1));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide) return InteractionResultHolder.success(stack);
            RadGear.Epd e = stack.getOrDefault(RadGear.EPD.get(), RadGear.Epd.FRESH);
            if (player.isShiftKeyDown()) {
                PersonalDosimeter.Preset next = e.presetValue().next();
                stack.set(RadGear.EPD.get(), new RadGear.Epd(0, e.rateSvH(), next.ordinal(), false));
                player.displayClientMessage(RadGearText.msg("epd_preset", RadGearText.presetName(next), RadGearText.dose(next.doseSv),
                        RadGearText.rate(next.rateSvH)).withStyle(ChatFormatting.YELLOW), true);
            } else {
                player.displayClientMessage(RadGearText.epdLine(e), true);
            }
            return InteractionResultHolder.consume(stack);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.promptcritical.personal_dosimeter_epd.tip").withStyle(ChatFormatting.GRAY));
            RadGear.Epd e = stack.get(RadGear.EPD.get());
            if (e != null) tooltip.add(RadGearText.epdLine(e).withStyle(ChatFormatting.AQUA));
        }
    }

    /** Термолюминесцентный дозиметр: показаний нет, дозу видно только на считывателе. */
    public static final class Tld extends Item {
        public Tld(Properties props) {
            super(props.stacksTo(1));
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.promptcritical.tld_badge.tip").withStyle(ChatFormatting.GRAY));
        }
    }

    /** Таблетка йодистого калия 130 мг: блокирует щитовидную железу на сутки. */
    public static final class Iodide extends Item {
        public Iodide(Properties props) {
            super(props);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide) return InteractionResultHolder.success(stack);
            ServerPlayer sp = (ServerPlayer) player;
            double refund = RadiationService.takeIodide(sp);
            if (!player.isCreative()) stack.shrink(1);
            level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.5f, 1.4f);
            player.displayClientMessage(refund > 0 ? RadGearText.msg("iodide_refund", RadGearText.dose(refund)).withStyle(ChatFormatting.GREEN)
                    : RadGearText.msg("iodide_taken").withStyle(ChatFormatting.GREEN), true);
            return InteractionResultHolder.consume(stack);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.promptcritical.potassium_iodide.tip").withStyle(ChatFormatting.GRAY));
        }
    }
}
