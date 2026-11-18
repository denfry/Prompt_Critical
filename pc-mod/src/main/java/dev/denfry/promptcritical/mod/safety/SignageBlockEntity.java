package dev.denfry.promptcritical.mod.safety;

import dev.denfry.promptcritical.mod.acoustics.HearingProtectionItem;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.grid.GridMountedBlock;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.ops.HazardCodes;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Маркировка опасностей 13 §13.8: знак безопасности {@code hazard_sign} (ГОСТ 12.4.026-2015: запрещающие,
 * предупреждающие, предписывающие, пиктограммы СГС — группа задаётся свойством, вариант в группе — компонентом
 * {@code sign_variant} блока), ромб NFPA 704 {@code hazard_diamond} и табличка ДОПОГ {@code un_placard}.
 *
 * <ul>
 *   <li>Знак: ПКМ — следующий вариант в группе, Shift + ПКМ — следующая группа. Предписывающие знаки «Работать в
 *   каске», «… в противогазе», «… в наушниках» напоминают игроку без этого СИЗ в {@value #REMIND_RADIUS} блоках
 *   (строка действий, раз в {@value #REMIND_TICKS} тиков).</li>
 *   <li>Ромб и табличка читают продукт ёмкости, на которой висят ({@link HazardCodes}, §13.8.2): ПКМ — номер ООН, код
 *   Кемлера и ромб «здоровье-пожар-реакционная способность-особая».</li>
 * </ul>
 * Изображение варианта на модели и цифры ромба клиент не рисует: у знака четыре модели по группам [допущение:
 * ≈ 32 изображения §13.8.1 — бэклог арта].
 */
public final class SignageBlockEntity extends DeviceBlockEntity implements FoundryDevice {
    public static final int REMIND_RADIUS = 4, REMIND_TICKS = 200;
    /** Варианты знака по группам: 0 — предупреждающие, 1 — запрещающие, 2 — предписывающие, 3 — СГС. */
    public static final String[][] VARIANTS = {
            {"fire_hazard", "explosive", "toxic", "corrosive", "radioactive", "voltage", "gas_cylinder", "asphyxiation"},
            {"no_open_flame", "no_entry", "no_smoking", "no_switching_on"},
            {"wear_hard_hat", "wear_gas_mask", "wear_hearing_protection", "wear_harness"},
            {"ghs_flammable", "ghs_toxic", "ghs_corrosive", "ghs_gas_under_pressure", "ghs_oxidizer", "ghs_health_hazard"}};

    private int variant;
    private final Map<UUID, Long> reminded = new HashMap<>();

    public SignageBlockEntity(BlockPos pos, BlockState state) {
        super(SafetyBlockEntities.SIGNAGE.get(), pos, state);
    }

    public int group() {
        BlockState s = getBlockState();
        return s.hasProperty(PcProperties.SEVERITY) ? s.getValue(PcProperties.SEVERITY) : 0;
    }

    public String variantKey() {
        String[] g = VARIANTS[group()];
        return g[Math.floorMod(variant, g.length)];
    }

    public void setVariant(int group, int v) {
        BlockState s = getBlockState();
        if (level != null && s.hasProperty(PcProperties.SEVERITY) && s.getValue(PcProperties.SEVERITY) != group)
            level.setBlock(worldPosition, s.setValue(PcProperties.SEVERITY, group), Block.UPDATE_ALL);
        variant = v;
        setChanged();
    }

    private boolean sign() {
        return getBlockState().is(PcBlocks.HAZARD_SIGN.get());
    }

    /** Маркировка продукта ёмкости, на которой висит ромб или табличка; {@code null} — продукта нет или он не в таблице. */
    @Nullable
    public HazardCodes.Code product() {
        if (level == null) return null;
        String id = productId();
        return id == null ? null : HazardCodes.of(id);
    }

    @Nullable
    String productId() {
        BlockPos mount = GridMountedBlock.mountPos(getBlockState(), worldPosition);
        for (Direction d : Direction.values()) {
            IFluidHandler h = level.getCapability(Capabilities.FluidHandler.BLOCK, mount, d);
            if (h == null) continue;
            for (int i = 0; i < h.getTanks(); i++) {
                FluidStack f = h.getFluidInTank(i);
                if (!f.isEmpty()) return BuiltInRegistries.FLUID.getKey(f.getFluid()).getPath().replace("flowing_", "");
            }
        }
        return null;
    }

    @Override
    public InteractionResult use(Player player) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (sign()) {
            if (player.isSecondaryUseActive()) setVariant((group() + 1) % VARIANTS.length, 0);
            else setVariant(group(), (variant + 1) % VARIANTS[group()].length);
            player.displayClientMessage(SafetyText.msg("sign", Component.translatable("sign.promptcritical." + variantKey())), true);
            return InteractionResult.SUCCESS;
        }
        HazardCodes.Code c = product();
        player.displayClientMessage(c == null ? SafetyText.msg("placard_none")
                : SafetyText.msg("placard", c.un(), c.kemler(), c.diamond()), false);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl) || !sign() || group() != 2 || (sl.getGameTime() + worldPosition.hashCode()) % 20 != 0) return;
        String key = variantKey();
        Vec3 c = worldPosition.getCenter();
        long now = sl.getGameTime();
        for (ServerPlayer p : sl.players()) {
            if (p.isSpectator() || p.distanceToSqr(c) > REMIND_RADIUS * REMIND_RADIUS || wears(p, key)) continue;
            Long last = reminded.get(p.getUUID());
            if (last != null && now - last < REMIND_TICKS) continue;
            reminded.put(p.getUUID(), now);
            p.displayClientMessage(SafetyText.msg("sign_mandatory", Component.translatable("sign.promptcritical." + key)).withStyle(ChatFormatting.BLUE), true);
        }
    }

    /** Предписание знака выполнено. */
    public static boolean wears(Player p, String key) {
        ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD);
        ItemStack insert = PpeItem.is(head, PpeItem.Kind.HARD_HAT) ? SafetyComponents.insert(head) : null;
        return switch (key) {
            case "wear_hard_hat" -> PpeItem.is(head, PpeItem.Kind.HARD_HAT);
            case "wear_gas_mask" -> PpeItem.is(head, PpeItem.Kind.GAS_MASK) || (insert != null && PpeItem.is(insert, PpeItem.Kind.GAS_MASK))
                    || PpeService.isolated(p);
            case "wear_hearing_protection" -> head.getItem() instanceof HearingProtectionItem || PpeService.earsProtected(p);
            case "wear_harness" -> PpeItem.is(p.getItemBySlot(EquipmentSlot.LEGS), PpeItem.Kind.SAFETY_HARNESS);
            default -> true;
        };
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

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("sign_variant", variant);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        variant = tag.getInt("sign_variant");
    }
}
