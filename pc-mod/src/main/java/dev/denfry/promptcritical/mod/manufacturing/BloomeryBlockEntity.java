package dev.denfry.promptcritical.mod.manufacturing;

import dev.denfry.promptcritical.mod.foundry.MachineInventory;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.manufacturing.Bloomery;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.Tags;

/**
 * Сыродутный горн {@code bloomery} (18 §18.6.1, §18.7, руками): руда и древесный уголь в шахту, мехи качает игрок кнопкой
 * щита — дутьё спадает за несколько секунд, его надо поддерживать, но не перегреть: выше 1350 °C дольше пяти минут
 * железо науглероживается, и из горна выходит чугун, а не крица. Модель — {@link Bloomery}, шаг раз в секунду.
 */
public final class BloomeryBlockEntity extends SmithyDevice {
    /** Заряд руды и угля, кг [BALANCE: ручной горн — десятки кг за плавку]. */
    public static final double ORE_CAPACITY_KG = 40, COAL_CAPACITY_KG = 60;
    /** Масса руды и угля на предмет, кг [BALANCE]. */
    public static final double ORE_PER_ITEM_KG = 4, COAL_PER_ITEM_KG = 3;
    /** Железо в сырой руде, доля [сверить: гематитовая руда 50–65 %]. */
    public static final double ORE_FE_FRAC = 0.6;
    /** Прибавка дутья за качок и спад в секунду [BALANCE: качать каждые 3–4 с, чтобы держать 1200 °C]. */
    public static final double BELLOWS_PUMP = 0.25, BLAST_DECAY_S = 0.08;
    private static final MachineInventory.Role[] ROLES = {MachineInventory.Role.INPUT, MachineInventory.Role.INPUT,
            MachineInventory.Role.OUTPUT};

    private Bloomery furnace = Bloomery.charged(0, ORE_FE_FRAC, 0);
    private double blast;

    public BloomeryBlockEntity(BlockPos pos, BlockState state) {
        super(SmithyBlockEntities.BLOOMERY.get(), pos, state, ROLES, (slot, s) -> slot == 0 ? isOre(s) : slot == 1 && isCharcoal(s));
    }

    public static boolean isOre(ItemStack s) {
        return s.is(Tags.Items.RAW_MATERIALS_IRON);
    }

    public static boolean isCharcoal(ItemStack s) {
        return s.is(Items.CHARCOAL);
    }

    /** Сейчас: модель горна и дутьё (для GameTest и щита). */
    public Bloomery furnace() {
        return furnace;
    }

    public double blast() {
        return blast;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel)) {
            clientTick();
            return;
        }
        if (level.getGameTime() % 20 != 0) return;
        step(1.0);
    }

    /** Секунда горна: подсыпать заряд, шаг модели, спад дутья, лампа огня. */
    public void step(double seconds) {
        ItemStack ore = inv.getStackInSlot(0);
        // руду досыпают, пока восстановление не началось: начатая крица не принимает новой руды
        while (!ore.isEmpty() && furnace.ironKg() == 0 && furnace.oreKg() + ORE_PER_ITEM_KG <= ORE_CAPACITY_KG) {
            furnace = new Bloomery(furnace.oreKg() + ORE_PER_ITEM_KG, ORE_FE_FRAC, furnace.charcoalKg(), furnace.tempK(), 0,
                    furnace.overheatS(), furnace.castIron());
            ore.shrink(1);
        }
        ItemStack coal = inv.getStackInSlot(1);
        while (!coal.isEmpty() && furnace.charcoalKg() + COAL_PER_ITEM_KG <= COAL_CAPACITY_KG) {
            furnace = new Bloomery(furnace.oreKg(), furnace.oreFeFrac(), furnace.charcoalKg() + COAL_PER_ITEM_KG,
                    furnace.tempK(), furnace.ironKg(), furnace.overheatS(), furnace.castIron());
            coal.shrink(1);
        }
        furnace = furnace.step(blast, seconds);
        blast = Math.max(0, blast - BLAST_DECAY_S * seconds);
        setChanged();
        boolean lit = furnace.charcoalKg() > 0 && furnace.tempK() > 773.15;
        BlockState st = getBlockState();
        if (st.hasProperty(BlockStateProperties.LIT) && st.getValue(BlockStateProperties.LIT) != lit)
            level.setBlock(worldPosition, st.setValue(BlockStateProperties.LIT, lit), Block.UPDATE_ALL);
    }

    /** Качнуть мехами. */
    public void pump() {
        blast = Math.min(1, blast + BELLOWS_PUMP);
        if (level != null) level.playSound(null, worldPosition, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.3f, 0.6f);
    }

    /** Вынуть крицу (или закозлившийся чугун) в выходной слот; горн остаётся горячим с остатком угля. */
    public boolean take() {
        if (!inv.getStackInSlot(2).isEmpty()) return false;
        ItemStack out;
        if (furnace.castIron()) {
            out = new ItemStack(PcItems.PIG_IRON.get(), Math.max(1, (int) Math.round(furnace.ironKg() / ORE_PER_ITEM_KG)));
        } else if (furnace.done()) {
            out = Smithy.stack(furnace.bloom(), level.getGameTime());
        } else return false;
        inv.setStackInSlot(2, out);
        furnace = new Bloomery(0, ORE_FE_FRAC, furnace.charcoalKg(), furnace.tempK(), 0, 0, false);
        setChanged();
        return true;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == SmithyPanels.BUTTON_BELLOWS) pump();
        else if (id == SmithyPanels.BUTTON_TAKE && !take())
            player.displayClientMessage(ManufacturingText.msg("bloomery.not_ready"), true);
    }

    private void clientTick() {
        MachineFx fx = MachineFx.of(level, worldPosition, getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING));
        if (fx == null || !getBlockState().getValue(BlockStateProperties.LIT)) return;
        if (fx.chance(4)) fx.flame(fx.at(8, 15, 8), 0.12);
        if (fx.chance(2)) fx.smoke(fx.at(8, 16, 8), false, 0.12);
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return isOre(stack) || isCharcoal(stack);
    }

    @Override
    public InteractionResult use(Player player) {
        player.displayClientMessage(ManufacturingText.msg("bloomery.status", Math.round(furnace.tempK() - 273.15),
                Math.round(furnace.oreKg()), Math.round(furnace.ironKg() * 10) / 10.0), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public dev.denfry.promptcritical.mod.panel.PanelLayout panelLayout() {
        return SmithyPanels.BLOOMERY;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[SmithyPanels.B_TEMP_C] = (int) Math.round(furnace.tempK() - 273.15);
        out[SmithyPanels.B_BLAST_PCT] = (int) Math.round(blast * 100);
        out[SmithyPanels.B_COAL_KG10] = (int) Math.round(furnace.charcoalKg() * 10);
        out[SmithyPanels.B_ORE_KG10] = (int) Math.round(furnace.oreKg() * 10);
        out[SmithyPanels.B_IRON_KG10] = (int) Math.round(furnace.ironKg() * 10);
        out[SmithyPanels.B_OVERHEAT_S] = (int) Math.round(furnace.overheatS());
        int f = 0;
        if (furnace.charcoalKg() > 0 && furnace.tempK() > 773.15) f |= 1 << SmithyPanels.BF_LIT;
        if (furnace.done()) f |= 1 << SmithyPanels.BF_DONE;
        if (furnace.castIron()) f |= 1 << SmithyPanels.BF_CAST;
        out[SmithyPanels.FLAGS] = f;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("ore", furnace.oreKg());
        tag.putDouble("coal", furnace.charcoalKg());
        tag.putDouble("t", furnace.tempK());
        tag.putDouble("iron", furnace.ironKg());
        tag.putDouble("over", furnace.overheatS());
        tag.putBoolean("cast", furnace.castIron());
        tag.putDouble("blast", blast);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("t")) {
            furnace = new Bloomery(Math.max(0, tag.getDouble("ore")), ORE_FE_FRAC, Math.max(0, tag.getDouble("coal")),
                    Math.max(1, tag.getDouble("t")), Math.max(0, tag.getDouble("iron")), Math.max(0, tag.getDouble("over")),
                    tag.getBoolean("cast"));
        }
        blast = Math.max(0, Math.min(1, tag.getDouble("blast")));
    }
}
