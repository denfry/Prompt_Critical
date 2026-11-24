package dev.denfry.promptcritical.mod.safety;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.acoustics.HearingProtectionItem;
import dev.denfry.promptcritical.sim.ops.FilterType;
import dev.denfry.promptcritical.sim.ops.GasMask;
import dev.denfry.promptcritical.sim.ops.Scba;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Общепромышленные СИЗ 13 §13.7 — броня со своим слоем ({@code textures/models/armor/<id>_layer_N.png}, 00 §0.12.1):
 * каска со вставкой, противогаз со сменным фильтром, изолирующий аппарат, огнезащитный костюм, страховочная привязь.
 * Действие на игрока — {@link PpeService}.
 *
 * <p>Слоты [решение: в Minecraft один слот головы и один груди]: каска и противогаз — голова, противогаз и
 * противошумные наушники вставляются в каску; аппарат (баллон с маской) и костюм — грудь; привязь — ноги, чтобы
 * сочеталась с аппаратом и костюмом.
 */
public final class PpeItem extends ArmorItem {
    public enum Kind {
        HARD_HAT(Type.HELMET, 1), GAS_MASK(Type.HELMET, 0), SCBA(Type.CHESTPLATE, 0), FIRE_SUIT(Type.CHESTPLATE, 1),
        SAFETY_HARNESS(Type.LEGGINGS, 0),
        // радиационные СИЗ 04b §4b.7.3 (действие — radiation.RadGear): маски, пневмокостюм, спецодежда, фартук
        RESPIRATOR(Type.HELMET, 0), FULL_FACE_RESPIRATOR(Type.HELMET, 0), PNEUMOSUIT_HELMET(Type.HELMET, 0, "pneumosuit"),
        PNEUMOSUIT_JACKET(Type.CHESTPLATE, 0, "pneumosuit"), PNEUMOSUIT_TROUSERS(Type.LEGGINGS, 0, "pneumosuit"),
        PNEUMOSUIT_BOOTS(Type.BOOTS, 0, "pneumosuit"), PROTECTIVE_OVERALLS(Type.CHESTPLATE, 0), BOOT_COVERS(Type.BOOTS, 0),
        LEAD_APRON(Type.CHESTPLATE, 1);

        final Type type;
        final int defense;
        /** Слой брони {@code textures/models/armor/<layer>_layer_N.png}: части пневмокостюма делят один. */
        final String layer;

        Kind(Type type, int defense) {
            this(type, defense, null);
        }

        Kind(Type type, int defense, String layer) {
            this.type = type;
            this.defense = defense;
            this.layer = layer == null ? name().toLowerCase(Locale.ROOT) : layer;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, PromptCritical.MOD_ID);
    private static final Map<Kind, DeferredHolder<ArmorMaterial, ArmorMaterial>> BY_KIND = new EnumMap<>(Kind.class);

    static {
        for (Kind k : Kind.values())
            BY_KIND.put(k, MATERIALS.register(k.id(), () -> new ArmorMaterial(Map.of(k.type, k.defense), 0,
                    SoundEvents.ARMOR_EQUIP_GENERIC, () -> Ingredient.EMPTY,
                    List.of(new ArmorMaterial.Layer(PromptCritical.id(k.layer))), 0, 0)));
    }

    private final Kind kind;

    public PpeItem(Properties props, Kind kind) {
        super(material(kind), kind.type, props.stacksTo(1));
        this.kind = kind;
    }

    private static Holder<ArmorMaterial> material(Kind kind) {
        return BY_KIND.get(kind);
    }

    public Kind kind() {
        return kind;
    }

    public static boolean is(ItemStack s, Kind kind) {
        return s.getItem() instanceof PpeItem p && p.kind == kind;
    }

    /** Противогаз стека (сам противогаз или вставка каски), {@code null} — его нет. */
    public static GasMask mask(ItemStack mask) {
        SafetyComponents.Filter f = mask.get(SafetyComponents.FILTER_TYPE.get());
        return f == null ? new GasMask(null, 0) : new GasMask(f.type(), f.loaded());
    }

    public static double air(ItemStack scba) {
        Double a = scba.get(SafetyComponents.AIR_REMAINING.get());
        return a == null ? 0 : a;
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access) {
        switch (kind) {
            case HARD_HAT -> {
                ItemStack insert = SafetyComponents.insert(stack);
                if (other.isEmpty() && action == ClickAction.SECONDARY && insert != null) {
                    access.set(insert);
                    SafetyComponents.setInsert(stack, ItemStack.EMPTY);
                    if (!player.level().isClientSide) player.displayClientMessage(SafetyText.msg("insert_removed", insert.getHoverName()), true);
                    return true;
                }
                if (insert == null && (is(other, Kind.GAS_MASK) || dev.denfry.promptcritical.mod.radiation.RadGear.isRadMask(other)
                        || other.getItem() instanceof HearingProtectionItem)) {
                    SafetyComponents.setInsert(stack, other);
                    if (!player.level().isClientSide) player.displayClientMessage(SafetyText.msg("insert_added", other.getHoverName()), true);
                    other.shrink(1);
                    return true;
                }
            }
            case GAS_MASK -> {
                if (other.getItem() instanceof GasMaskFilterItem) {
                    SafetyComponents.Filter f = GasMaskFilterItem.filter(other);
                    SafetyComponents.Filter old = stack.get(SafetyComponents.FILTER_TYPE.get());
                    stack.set(SafetyComponents.FILTER_TYPE.get(), f);
                    other.shrink(1);
                    if (old != null && old.loaded() < old.type().capacity())
                        player.getInventory().placeItemBackInInventory(GasMaskFilterItem.of(old));
                    if (!player.level().isClientSide) player.displayClientMessage(SafetyText.msg("filter_fitted", f.type().name()), true);
                    return true;
                }
            }
            case FULL_FACE_RESPIRATOR -> {
                if (other.getItem() == dev.denfry.promptcritical.mod.registry.PcItems.IODINE_CARTRIDGE.get()) {
                    stack.set(dev.denfry.promptcritical.mod.radiation.RadGear.IODINE_CARTRIDGE.get(),
                            dev.denfry.promptcritical.sim.radiation.RadRespirator.IODINE_CARTRIDGE_PHYS_S);
                    other.shrink(1);
                    if (!player.level().isClientSide) player.displayClientMessage(
                            dev.denfry.promptcritical.mod.radiation.RadGearText.msg("cartridge_fitted"), true);
                    return true;
                }
            }
            case SCBA -> {
                if (other.getItem() == dev.denfry.promptcritical.mod.registry.PcItems.SCBA_CYLINDER.get()) {
                    stack.set(SafetyComponents.AIR_REMAINING.get(), Scba.AIR_L);
                    other.shrink(1);
                    if (!player.level().isClientSide) player.displayClientMessage(SafetyText.msg("scba_filled", (int) Scba.AIR_L), true);
                    return true;
                }
            }
            default -> {
            }
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.promptcritical." + kind.id() + ".hint").withStyle(ChatFormatting.GRAY));
        switch (kind) {
            case HARD_HAT -> {
                ItemStack insert = SafetyComponents.insert(stack);
                if (insert != null) {
                    tooltip.add(Component.translatable("item.promptcritical.hard_hat.insert", insert.getHoverName()).withStyle(ChatFormatting.YELLOW));
                    if (is(insert, Kind.GAS_MASK)) maskLine(insert, tooltip);
                }
            }
            case GAS_MASK -> maskLine(stack, tooltip);
            case FULL_FACE_RESPIRATOR, PNEUMOSUIT_JACKET -> dev.denfry.promptcritical.mod.radiation.RadGearText.gearLine(stack, tooltip);
            case SCBA -> {
                double a = air(stack);
                tooltip.add(Component.translatable("item.promptcritical.scba.air", Math.round(a), Math.round(a / Scba.CONSUMPTION_LPM))
                        .withStyle(a < Scba.AIR_L * Scba.WHISTLE_FRACTION ? ChatFormatting.RED : ChatFormatting.AQUA));
            }
            default -> {
            }
        }
    }

    private static void maskLine(ItemStack mask, List<Component> tooltip) {
        GasMask m = mask(mask);
        FilterType t = m.filter();
        tooltip.add(t == null ? Component.translatable("item.promptcritical.gas_mask.no_filter").withStyle(ChatFormatting.RED)
                : Component.translatable("item.promptcritical.gas_mask.filter", t.name(), Math.round(m.usedFraction() * 100))
                        .withStyle(m.spent() ? ChatFormatting.RED : ChatFormatting.AQUA));
    }
}
