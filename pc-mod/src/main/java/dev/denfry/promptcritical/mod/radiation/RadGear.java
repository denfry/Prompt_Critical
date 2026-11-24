package dev.denfry.promptcritical.mod.radiation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.safety.PpeItem;
import dev.denfry.promptcritical.mod.safety.SafetyComponents;
import dev.denfry.promptcritical.sim.ops.Ppe;
import dev.denfry.promptcritical.sim.radiation.LeadApron;
import dev.denfry.promptcritical.sim.radiation.PersonalDosimeter;
import dev.denfry.promptcritical.sim.radiation.RadRespirator;
import io.netty.buffer.ByteBuf;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/**
 * Радиационные СИЗ и индивидуальная дозиметрия на игроке (04b §4b.7): какая защита дыхания надета (полумаска,
 * полнолицевая маска с йодным патроном, пневмокостюм на шланге, а также общепромышленные противогаз с фильтром P и
 * изолирующий аппарат), пропускание свинцового фартука, задержка загрязнения спецодеждой. Числа — {@code sim.radiation}.
 */
public final class RadGear {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PromptCritical.MOD_ID);

    /**
     * Показание и уставки ЭПД: доза с последнего сброса, Зв; мощность на последнем замере, Зв/ч; пресет; сработавший
     * сигнал дозы держится до сброса.
     */
    public record Epd(double doseSv, double rateSvH, int preset, boolean doseAlarm) {
        public static final Epd FRESH = new Epd(0, 0, 0, false);
        public static final Codec<Epd> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("dose").forGetter(Epd::doseSv),
                Codec.DOUBLE.optionalFieldOf("rate", 0.0).forGetter(Epd::rateSvH),
                Codec.INT.optionalFieldOf("preset", 0).forGetter(Epd::preset),
                Codec.BOOL.optionalFieldOf("dose_alarm", false).forGetter(Epd::doseAlarm)).apply(i, Epd::new));
        public static final StreamCodec<ByteBuf, Epd> STREAM = ByteBufCodecs.fromCodec(CODEC);

        public PersonalDosimeter.Preset presetValue() {
            PersonalDosimeter.Preset[] all = PersonalDosimeter.Preset.values();
            return all[Math.floorMod(preset, all.length)];
        }
    }

    /** ТЛД: накопленная доза, Зв, и игровые сутки выдачи (после считывания — снова ноль). */
    public record Tld(double doseSv, long issuedDay) {
        public static final Tld FRESH = new Tld(0, -1);
        public static final Codec<Tld> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("dose").forGetter(Tld::doseSv),
                Codec.LONG.optionalFieldOf("issued", -1L).forGetter(Tld::issuedDay)).apply(i, Tld::new));
        public static final StreamCodec<ByteBuf, Tld> STREAM = ByteBufCodecs.fromCodec(CODEC);
    }

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Epd>> EPD =
            COMPONENTS.registerComponentType("epd_reading", b -> b.persistent(Epd.CODEC).networkSynchronized(Epd.STREAM));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Tld>> TLD =
            COMPONENTS.registerComponentType("tld_dose", b -> b.persistent(Tld.CODEC).networkSynchronized(Tld.STREAM));
    /** Показание счётчика Гейгера, мкЗв/ч (уже с насыщением трубки). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Double>> GEIGER_READING =
            COMPONENTS.registerComponentType("geiger_reading", b -> b.persistent(Codec.DOUBLE).networkSynchronized(ByteBufCodecs.DOUBLE));
    /** Остаток ресурса йодного патрона полнолицевой маски, физ. с. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Double>> IODINE_CARTRIDGE =
            COMPONENTS.registerComponentType("iodine_cartridge", b -> b.persistent(Codec.DOUBLE).networkSynchronized(ByteBufCodecs.DOUBLE));
    /** Пост подачи воздуха, к которому пристёгнут шланг пневмокостюма. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> AIR_HOSE =
            COMPONENTS.registerComponentType("air_hose", b -> b.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));

    private RadGear() {
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }

    /** Радиационная маска (полумаска или полнолицевая): носится на голове или вставкой в каске. */
    public static boolean isRadMask(ItemStack s) {
        return PpeItem.is(s, PpeItem.Kind.RESPIRATOR) || PpeItem.is(s, PpeItem.Kind.FULL_FACE_RESPIRATOR);
    }

    /** Маска игрока: на голове или вставкой в каске; {@code null} — нет. */
    @Nullable
    public static ItemStack radMask(Player p) {
        ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD);
        if (isRadMask(head)) return head;
        if (PpeItem.is(head, PpeItem.Kind.HARD_HAT)) {
            ItemStack insert = SafetyComponents.insert(head);
            if (insert != null && isRadMask(insert)) return insert;
        }
        return null;
    }

    /** Остаток йодного патрона маски, физ. с. */
    public static double cartridge(ItemStack mask) {
        Double d = mask.get(IODINE_CARTRIDGE.get());
        return d == null ? 0 : d;
    }

    /** Надет весь пневмокостюм: шлем, куртка, брюки, боты. */
    public static boolean fullPneumosuit(Player p) {
        return PpeItem.is(p.getItemBySlot(EquipmentSlot.HEAD), PpeItem.Kind.PNEUMOSUIT_HELMET)
                && PpeItem.is(p.getItemBySlot(EquipmentSlot.CHEST), PpeItem.Kind.PNEUMOSUIT_JACKET)
                && PpeItem.is(p.getItemBySlot(EquipmentSlot.LEGS), PpeItem.Kind.PNEUMOSUIT_TROUSERS)
                && PpeItem.is(p.getItemBySlot(EquipmentSlot.FEET), PpeItem.Kind.PNEUMOSUIT_BOOTS);
    }

    /** Шланг куртки пневмокостюма пристёгнут к посту в пределах длины шланга. */
    public static boolean hoseConnected(Player p) {
        GlobalPos port = p.getItemBySlot(EquipmentSlot.CHEST).get(AIR_HOSE.get());
        return port != null && port.dimension() == p.level().dimension()
                && port.pos().getCenter().distanceTo(p.position()) <= RadRespirator.HOSE_M;
    }

    /**
     * Лучшая защита органов дыхания на игроке (две маски не перемножаются). Изолирующий аппарат и противогаз с фильтром
     * P — общепромышленные СИЗ 13 §13.7: аппарат держит и йод, фильтр P — только аэрозоли.
     */
    public static RadRespirator respirator(Player p) {
        RadRespirator r = RadRespirator.NONE;
        if (dev.denfry.promptcritical.mod.safety.PpeService.isolated(p))
            r = r.best(new RadRespirator(Ppe.SCBA_FACTOR, Ppe.SCBA_FACTOR, 0));
        else if (dev.denfry.promptcritical.mod.safety.PpeService.particleMask(p))
            r = r.best(new RadRespirator(Ppe.GAS_MASK_FACTOR, 1, 0));
        ItemStack mask = radMask(p);
        if (mask != null) {
            if (PpeItem.is(mask, PpeItem.Kind.RESPIRATOR)) r = r.best(RadRespirator.HALF_MASK_P3);
            else r = r.best(cartridge(mask) > 0 ? RadRespirator.FULL_FACE_IODINE : RadRespirator.FULL_FACE);
        }
        // PAPR: с работающим вентилятором ×1000, с севшей батареей — как полнолицевая маска ×50 (PoweredRespirator)
        ItemStack papr = dev.denfry.promptcritical.mod.gear.ElectricGearService.worn(p, dev.denfry.promptcritical.mod.gear.ElectricGearItem.Kind.PAPR);
        if (papr != null) {
            boolean blowing = dev.denfry.promptcritical.mod.gear.ElectricGearItem.data(papr).powerW10() > 0;
            double apf = blowing ? dev.denfry.promptcritical.sim.gear.PoweredRespirator.APF_POWERED
                    : dev.denfry.promptcritical.sim.gear.PoweredRespirator.APF_UNPOWERED;
            r = r.best(new RadRespirator(apf, apf, 0));
        }
        if (fullPneumosuit(p)) r = r.best(hoseConnected(p) ? RadRespirator.PNEUMOSUIT
                : new RadRespirator(1, 1, RadRespirator.PNEUMOSUIT.skinRetained()));
        return r;
    }

    /** Пропускание носимой защиты для фотонов энергии E: свинцовый фартук на груди, иначе 1. */
    public static DoubleUnaryOperator armorTransmission(Player p) {
        return PpeItem.is(p.getItemBySlot(EquipmentSlot.CHEST), PpeItem.Kind.LEAD_APRON) ? LeadApron::transmission : e -> 1;
    }

    /**
     * Что на ногах задерживает загрязнение пола вместо обуви: бахилы (доля {@link RadRespirator#OVERALLS_RETAINED}) или
     * боты пневмокостюма; {@code null} — ничего.
     */
    @Nullable
    public static ItemStack footCover(Player p) {
        ItemStack feet = p.getItemBySlot(EquipmentSlot.FEET);
        return PpeItem.is(feet, PpeItem.Kind.BOOT_COVERS) || PpeItem.is(feet, PpeItem.Kind.PNEUMOSUIT_BOOTS) ? feet : null;
    }

    /** Доля загрязнения, которую задерживает покрытие ног. */
    public static double footRetained(ItemStack cover) {
        return PpeItem.is(cover, PpeItem.Kind.PNEUMOSUIT_BOOTS) ? RadRespirator.PNEUMOSUIT.skinRetained() : RadRespirator.OVERALLS_RETAINED;
    }

    /** Перчатки пневмокостюма закрывают руки: загрязнение предмета в руке ложится на куртку. */
    @Nullable
    public static ItemStack handCover(Player p) {
        return fullPneumosuit(p) ? p.getItemBySlot(EquipmentSlot.CHEST) : null;
    }

    /** Вещи зоны, которые снимают в «грязном» шкафу: спецодежда, бахилы, маски, пневмокостюм, фартук. */
    public static boolean zoneClothing(ItemStack s) {
        if (!(s.getItem() instanceof PpeItem item)) return false;
        return switch (item.kind()) {
            case RESPIRATOR, FULL_FACE_RESPIRATOR, PNEUMOSUIT_HELMET, PNEUMOSUIT_JACKET, PNEUMOSUIT_TROUSERS, PNEUMOSUIT_BOOTS,
                    PROTECTIVE_OVERALLS, BOOT_COVERS, LEAD_APRON -> true;
            default -> false;
        };
    }
}
