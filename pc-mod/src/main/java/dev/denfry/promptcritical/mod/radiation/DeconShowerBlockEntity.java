package dev.denfry.promptcritical.mod.radiation;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Душ санпропускника: игрок под душем (в блоке перед ним или рядом) моет руки с мылом
 * ({@link dev.denfry.promptcritical.sim.radiation.DeconMethod#SOAP_SHOWER}) и обувь водой; полная помывка —
 * {@value #FULL_WASH_S} с, расход {@value #FLOW_L_S} л/с (12 л/мин, обычная душевая сетка). Вода — трубой в боковые
 * грани или сверху; смыв — жидкие радиоактивные отходы — снизу (спецканализация санпропускника). Без воды не моет.
 */
public final class DeconShowerBlockEntity extends DeviceBlockEntity implements FoundryDevice {
    public static final int WATER_MB = 4000, WASTE_MB = 8000;
    public static final double FULL_WASH_S = 60, FLOW_L_S = 0.2;

    private final FeedTank water = new FeedTank(Fluids.WATER, WATER_MB);
    private final ProductTank waste = new ProductTank(PcFluids.CONTAMINATED_DECON_SOLUTION.source.get(), WASTE_MB);
    private double wasteBq;

    public DeconShowerBlockEntity(BlockPos pos, BlockState state) {
        super(RadiationBlockEntities.DECON_SHOWER.get(), pos, state);
    }

    public ProductTank waste() {
        return waste;
    }

    public double wasteBq() {
        return wasteBq;
    }

    /**
     * Помыть игрока {@code seconds} секунд: тратит воду, смыв — в отходы.
     *
     * @return мыл ли (есть вода и место под смыв)
     */
    public boolean wash(ServerPlayer p, double seconds) {
        double litres = FLOW_L_S * seconds;
        if (water.amount() < litres || waste.room() < litres) return false;
        water.take(litres);
        waste.add(litres);
        wasteBq += ContaminationService.shower(p, seconds / FULL_WASH_S);
        setChanged();
        return true;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel s) || s.getGameTime() % 20 != 0) return;
        boolean any = false;
        for (ServerPlayer p : s.getEntitiesOfClass(ServerPlayer.class, new AABB(worldPosition).inflate(1, 1, 1))) {
            if (p.isSpectator()) continue;
            if (wash(p, 1)) any = true;
            else p.displayClientMessage(ContaminationText.msg("shower.dry").withStyle(ChatFormatting.GOLD), true);
        }
        if (any) {
            var c = worldPosition.getCenter();
            s.sendParticles(ParticleTypes.FALLING_WATER, c.x, c.y + 0.4, c.z, 30, 0.4, 0.2, 0.4, 0);
            s.playSound(null, worldPosition, SoundEvents.WEATHER_RAIN, SoundSource.BLOCKS, 0.3f, 1.4f);
        }
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return side == Direction.DOWN ? waste : water;
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
    public InteractionResult use(Player player) {
        player.displayClientMessage(ContaminationText.msg("shower.status", String.format(Locale.ROOT, "%.0f", water.amount()),
                String.format(Locale.ROOT, "%.0f", waste.amount()), ContaminationText.num(wasteBq / 1e6)), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        water.save(tag, "water");
        waste.save(tag, "waste");
        tag.putDouble("waste_bq", wasteBq);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        water.load(tag, "water");
        waste.load(tag, "waste");
        wasteBq = tag.getDouble("waste_bq");
    }
}
