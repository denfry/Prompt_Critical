package dev.denfry.promptcritical.mod.gear;

import dev.denfry.promptcritical.mod.item.ElectricGearData;
import dev.denfry.promptcritical.mod.registry.PcDataComponents;
import dev.denfry.promptcritical.sim.battery.Battery;
import dev.denfry.promptcritical.sim.gear.HeatedSuit;
import dev.denfry.promptcritical.sim.gear.PowerPack;
import dev.denfry.promptcritical.sim.gear.PoweredRespirator;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Носимое электроснаряжение §15.8 со своей батареей из {@code sim.battery}: респиратор с принудительной подачей
 * воздуха ({@link PoweredRespirator}, поясной блок с маской — слот груди) и электрообогреваемый костюм
 * ({@link HeatedSuit}, комбинезон — слот ног; голова остаётся под головной светильник). Брони не даёт — это СИЗ.
 * Батарея садится, пока предмет надет ({@link ElectricGearService}), и заряжается на стеллаже ламповой.
 */
public class ElectricGearItem extends Item implements Equipable {
    public enum Kind {
        RESPIRATOR(EquipmentSlot.CHEST), HEATED_SUIT(EquipmentSlot.LEGS);

        public final EquipmentSlot slot;

        Kind(EquipmentSlot slot) {
            this.slot = slot;
        }
    }

    private final Kind kind;

    public ElectricGearItem(Properties props, Kind kind) {
        super(props.stacksTo(1));
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return kind.slot;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        // лежащее в инвентаре снаряжение помечается текущим тиком, чтобы стеллаж считал заряд с момента постановки
        if (level.isClientSide() || (level.getGameTime() + entity.getId()) % 20 != 0) return;
        ElectricGearData d = data(stack);
        if (d.lastTick() != level.getGameTime())
            stack.set(PcDataComponents.ELECTRIC_GEAR.get(), new ElectricGearData(d.removedAh(), d.cycles(), d.tempC(),
                    d.filterMol(), d.powerW10(), level.getGameTime()));
    }

    public static ElectricGearData data(ItemStack stack) {
        return stack.getOrDefault(PcDataComponents.ELECTRIC_GEAR.get(), ElectricGearData.FRESH);
    }

    /** Респиратор в состоянии предмета. */
    public static PoweredRespirator respirator(ElectricGearData d) {
        PoweredRespirator r = PoweredRespirator.standard();
        r.restore(d.removedAh(), d.cycles(), d.tempC(), d.filterMol());
        return r;
    }

    /** Костюм в состоянии предмета. */
    public static HeatedSuit suit(ElectricGearData d) {
        HeatedSuit s = HeatedSuit.standard();
        s.restore(d.removedAh(), d.cycles(), d.tempC());
        return s;
    }

    /** Батарея снаряжения этого вида в состоянии предмета. */
    public PowerPack pack(ElectricGearData d) {
        return kind == Kind.RESPIRATOR ? respirator(d).pack() : suit(d).pack();
    }

    /** Состояние после шага модели. */
    public static ElectricGearData of(PowerPack p, double filterMol, long tick) {
        Battery b = p.battery();
        return new ElectricGearData(b.removedAh(), b.cycles(), b.temperatureC(), filterMol, (int) Math.round(p.powerW() * 10), tick);
    }

    public double soc(ItemStack stack) {
        return pack(data(stack)).battery().soc();
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13 * (float) soc(stack));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb((float) soc(stack) / 3f, 1, 1);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        ElectricGearData d = data(stack);
        PowerPack p = pack(d);
        Battery b = p.battery();
        tip.add(Component.translatable("item.promptcritical.electric_gear.battery", b.spec().cells(),
                fmt("%.1f", b.spec().nominalVolts()), fmt("%.0f", b.spec().ratedAh()), fmt("%.0f", p.ratedWh())).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("item.promptcritical.electric_gear.charge", fmt("%.0f", 100 * b.soc()),
                fmt("%.1f", d.powerW10() / 10.0), fmt("%.0f", b.cycles())).withStyle(ChatFormatting.GRAY));
        if (kind == Kind.RESPIRATOR) {
            PoweredRespirator r = respirator(d);
            tip.add(Component.translatable(r.filterSpent() ? "item.promptcritical.powered_respirator.filter_spent"
                    : "item.promptcritical.powered_respirator.filter", fmt("%.0f", 100 * r.filterLoad()))
                    .withStyle(r.filterSpent() ? ChatFormatting.RED : ChatFormatting.DARK_GREEN));
            tip.add(Component.translatable("item.promptcritical.powered_respirator.no_co").withStyle(ChatFormatting.GOLD));
        } else {
            tip.add(Component.translatable("item.promptcritical.heated_suit.heater", fmt("%.0f", HeatedSuit.HEATER_MAX_W),
                    fmt("%.1f", HeatedSuit.SUIT_CLO)).withStyle(ChatFormatting.DARK_GREEN));
        }
    }

    private static String fmt(String f, double v) {
        return String.format(Locale.ROOT, f, v);
    }
}
