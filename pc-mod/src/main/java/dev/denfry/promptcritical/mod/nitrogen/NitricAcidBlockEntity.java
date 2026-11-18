package dev.denfry.promptcritical.mod.nitrogen;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlockEntity;
import dev.denfry.promptcritical.mod.gas.PortHandler;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.consequence.BlastScaling;
import dev.denfry.promptcritical.sim.consequence.DamageBudgetConfig;
import dev.denfry.promptcritical.sim.consequence.DamageKind;
import dev.denfry.promptcritical.sim.consequence.DamageRequest;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import dev.denfry.promptcritical.sim.nitrogen.AcidConcentrator;
import dev.denfry.promptcritical.sim.nitrogen.NitricAcidPlant;
import dev.denfry.promptcritical.sim.nitrogen.SynthesisLoop;
import java.util.Arrays;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

/**
 * Агрегат неконцентрированной азотной кислоты ({@link NitricAcidPlant}) 3×1×1: воздушный компрессор с газовой турбиной
 * на хвостовых газах на валу Create (вал сзади), контактный аппарат с платинородиевыми сетками, котёл-утилизатор и
 * абсорбционная колонна. Жидкий аммиак и вода (питательная — как конденсат) — трубой к любой грани; кислота 56 %
 * (1 мБ = 1 л, 1,345 кг) — из той же грани. Сетки и катализатор очистки хвостовых газов кладут с руки. Аммиака в
 * воздухе выше нижнего предела взрываемости — смесь вспыхивает в аппарате (бюджет разрушений мира), сетки рвутся.
 * Хвостовые газы с NOx больше 1500 ppm видны над агрегатом бурым «лисьим хвостом».
 */
public final class NitricAcidBlockEntity extends KineticMachineBlockEntity<ShaftInput> implements PanelHost {
    public static final double NOMINAL_RPM = 64, RATED_W = 250e3, AMMONIA_KG_L = 0.682, ACID_KG_L = 1.345,
            HNO3_KG_MOL = 0.063013, NH3_KG_MOL = 0.017031, WATER_L_MOL = 0.018015, FLASH_HOLDUP_S = 5;
    public static final int MB = 8_000;
    private static final DustParticleOptions FOX = new DustParticleOptions(new Vector3f(0.65f, 0.33f, 0.12f), 1.5f);

    private final ShaftInput shaft = new ShaftInput();
    private final FeedTank ammonia = new FeedTank(PcFluids.AMMONIA.source.get(), MB);
    private final FeedTank water = new FeedTank(PcFluids.BOILER_FEEDWATER.source.get(), MB);
    private final ProductTank acid = new ProductTank(PcFluids.NITRIC_ACID.source.get(), 2 * MB);
    private final IFluidHandler port = new PortHandler(acid, ammonia, water);
    private final NitricAcidPlant plant = new NitricAcidPlant();
    private long tick;
    private double loadW, totalKg;
    private boolean flashed;
    private static long requestSeq;

    public NitricAcidBlockEntity(BlockPos pos, BlockState state) {
        super(NitrogenBlockEntities.NITRIC_ACID_UNIT.get(), pos, state);
    }

    public NitricAcidPlant plant() {
        return plant;
    }

    public FeedTank ammonia() {
        return ammonia;
    }

    public FeedTank water() {
        return water;
    }

    public ProductTank acid() {
        return acid;
    }

    public boolean flashed() {
        return flashed;
    }

    public double totalKg() {
        return totalKg;
    }

    private double availableW() {
        return RATED_W * Math.min(1, shaft.rpm(tick) / NOMINAL_RPM);
    }

    @Override
    protected ShaftInput unit(SimLevelBinding binding) {
        return shaft;
    }

    @Override
    protected ShaftInput shaft(ShaftInput u) {
        return u;
    }

    @Override
    protected double powerW(ShaftInput u) {
        return u.rpm(tick) > 0 ? loadW : 0;
    }

    @Override
    protected double ratedW() {
        return RATED_W;
    }

    @Override
    protected double nominalRpm() {
        return NOMINAL_RPM;
    }

    @Override
    protected void serverTick(SimLevelBinding binding, ShaftInput u) {
        tick = binding.world().clock().tick();
        double dt = 0.05 * binding.world().clock().kSlow();
        // кислота не влезает в бак — колонну не сливают, аммиак не подают
        double nh3Mol = acid.room() > 1 ? ammonia.amount() * AMMONIA_KG_L / NH3_KG_MOL : 0;
        NitricAcidPlant.Result r = plant.step(dt, nh3Mol, water.amount() / WATER_L_MOL, availableW());
        if (r.ignited()) {
            flash((ServerLevel) level, binding, r.nh3MolS());
            return;
        }
        ammonia.take(r.nh3MolS() * dt * NH3_KG_MOL / AMMONIA_KG_L);
        water.take(r.waterMolS() * dt * WATER_L_MOL);
        double hno3Kg = r.hno3MolS() * dt * HNO3_KG_MOL;
        acid.add(hno3Kg / AcidConcentrator.WEAK / ACID_KG_L);
        totalKg += hno3Kg;
        loadW = r.shaftW();
        if (level.getGameTime() % 10 == 0) setStateFlag(PcProperties.RUNNING, r.hno3MolS() > 0);
        if (r.tailNoxPpm() > 1500 && level.getGameTime() % 10 == 0)
            ((ServerLevel) level).sendParticles(FOX, worldPosition.getX() + 0.5, worldPosition.getY() + 1.6, worldPosition.getZ() + 0.5,
                    4, 0.15, 0.3, 0.15, 0.01);
        if (level.getGameTime() % 100 == 0) setChanged();
    }

    /** Вспышка аммиачно-воздушной смеси: горит аммиак, накопившийся в аппарате за несколько секунд. */
    private void flash(ServerLevel sl, SimLevelBinding b, double nh3MolS) {
        double energy = nh3MolS * FLASH_HOLDUP_S * SynthesisLoop.LHV_NH3;
        GridPos o = new GridPos(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
        b.damage().submit(new DamageRequest(requestSeq++, simId(), null, DamageKind.BLAST, o, energy, 3, 2));
        DamageBudgetConfig dc = b.damage().config();
        double power = Math.max(1, Math.min(3, BlastScaling.power(energy, dc.tntReferenceKg(), dc.maxExplosionPower())));
        sl.explode(null, worldPosition.getX() + 0.5, worldPosition.getY() + 1.2, worldPosition.getZ() + 0.5, (float) power,
                false, Level.ExplosionInteraction.NONE);
        for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(worldPosition.getCenter()) < 64 * 64))
            p.displayClientMessage(NitrogenText.msg("nitric.flash").withStyle(ChatFormatting.RED), false);
        plant.setRunning(false);
        plant.tearGauze();
        flashed = true;
        loadW = 0;
        setStateFlag(PcProperties.RUNNING, false);
        setChanged();
    }

    // --- руки ---

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return port;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.is(PcItems.PT_RH_GAUZE.get()) || stack.is(PcItems.CATALYST_DENOX.get());
    }

    /** Пакет сеток — когда платины меньше половины; катализатор очистки — когда выработан. */
    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        String key;
        if (stack.is(PcItems.PT_RH_GAUZE.get()) && plant.gauzePtG() < NitricAcidPlant.GAUZE_PT_G / 2) {
            plant.loadGauze();
            flashed = false;
            key = "nitric.gauze";
        } else if (stack.is(PcItems.CATALYST_DENOX.get()) && plant.denox() < 0.5) {
            plant.loadDenox();
            key = "nitric.denox";
        } else if (accepts(stack)) {
            player.displayClientMessage(NitrogenText.msg("catalyst.full"), true);
            return ItemInteractionResult.CONSUME;
        } else {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        level.playSound(null, worldPosition, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8f, 1.0f);
        player.displayClientMessage(NitrogenText.msg(key), true);
        setChanged();
        return ItemInteractionResult.SUCCESS;
    }

    /** Shift + ПКМ: пуск или останов. */
    @Override
    public InteractionResult use(Player player) {
        toggle(player);
        return InteractionResult.SUCCESS;
    }

    private void toggle(Player player) {
        if (!plant.running() && plant.gauzePtG() <= 0) {
            player.displayClientMessage(NitrogenText.msg("nitric.no_gauze"), true);
            return;
        }
        plant.setRunning(!plant.running());
        level.playSound(null, worldPosition, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.6f, 1.2f);
        player.displayClientMessage(NitrogenText.msg(plant.running() ? "nitric.start" : "nitric.stop"), true);
        setChanged();
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return NitrogenPanels.NITRIC;
    }

    @Override
    public Component panelTitle() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public BlockPos panelPos() {
        return worldPosition;
    }

    @Override
    public boolean panelValid(Player player) {
        return PanelHost.near(this, player);
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        NitricAcidPlant.Result r = plant.last();
        v[NitrogenPanels.N_GAUZE_C] = r.gauzeK() > 0 ? (int) Math.round(r.gauzeK() - 273.15) : 0;
        v[NitrogenPanels.N_NOX_PPM] = (int) Math.round(Math.min(r.tailNoxPpm(), 99_999));
        v[NitrogenPanels.N_NH3] = (int) Math.round(plant.ammoniaPercent() * 100);
        v[NitrogenPanels.N_LEL] = (int) Math.round(NitricAcidPlant.lowerLimitPercent(NitricAcidPlant.mixK(plant.pressureMpa()),
                plant.pressureMpa()) * 100);
        v[NitrogenPanels.N_SEL] = (int) Math.round(r.selectivity() * 1000);
        v[NitrogenPanels.N_STRENGTH] = (int) Math.round((r.strength() > 0 ? r.strength() : plant.strengthSet()) * 1000);
        v[NitrogenPanels.N_HNO3_KGH] = (int) Math.round(r.hno3MolS() * HNO3_KG_MOL * 3600);
        v[NitrogenPanels.N_PT_G] = (int) Math.round(plant.gauzePtG());
        v[NitrogenPanels.N_SHAFT_KW] = (int) Math.round(loadW / 1e3);
        v[NitrogenPanels.N_PRESSURE] = plant.pressureIndex();
        v[NitrogenPanels.N_ACID_TANK] = (int) Math.round(acid.fill() * 1000);
        v[NitrogenPanels.N_ABSORB] = (int) Math.round(r.absorption() * 1000);
        v[NitrogenPanels.N_FLAGS] = (r.hno3MolS() > 0 ? 1 << NitrogenPanels.NF_RUN : 0) | (flashed ? 1 << NitrogenPanels.NF_FLASH : 0)
                | (plant.gauzePtG() <= 0 ? 1 << NitrogenPanels.NF_NO_GAUZE : 0) | (plant.denox() > 0 ? 1 << NitrogenPanels.NF_DENOX : 0)
                | (r.tailNoxPpm() > 1500 ? 1 << NitrogenPanels.NF_FOX : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case NitrogenPanels.NB_RUN -> toggle(player);
            case NitrogenPanels.NB_PRESSURE -> {
                if (!plant.running()) plant.setPressureIndex(plant.pressureIndex() + 1);
            }
            case NitrogenPanels.NB_NH3_DOWN -> plant.setAmmoniaPercent(plant.ammoniaPercent() - 0.25);
            case NitrogenPanels.NB_NH3_UP -> plant.setAmmoniaPercent(plant.ammoniaPercent() + 0.25);
            case NitrogenPanels.NB_STR_DOWN -> plant.setStrengthSet(plant.strengthSet() - 0.01);
            case NitrogenPanels.NB_STR_UP -> plant.setStrengthSet(plant.strengthSet() + 0.01);
            default -> {
            }
        }
        setChanged();
    }

    // --- сохранение ---

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) return;
        ammonia.save(tag, "ammonia");
        water.save(tag, "water");
        acid.save(tag, "acid");
        MutableStateTree tree = new MutableStateTree();
        plant.save(new TreeStateWriter(tree));
        tag.put("plant", NbtStateTrees.toNbt(tree));
        tag.putDouble("total", totalKg);
        tag.putBoolean("flashed", flashed);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) return;
        ammonia.load(tag, "ammonia");
        water.load(tag, "water");
        acid.load(tag, "acid");
        if (tag.contains("plant")) plant.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("plant"))));
        totalKg = tag.getDouble("total");
        flashed = tag.getBoolean("flashed");
    }
}
