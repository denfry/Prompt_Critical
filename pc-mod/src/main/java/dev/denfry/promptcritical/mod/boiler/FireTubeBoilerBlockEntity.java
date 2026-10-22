package dev.denfry.promptcritical.mod.boiler;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.ChimneyBlock;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.ops.OpsActions;
import dev.denfry.promptcritical.mod.ops.OpsMachine;
import dev.denfry.promptcritical.mod.ops.OpsText;
import dev.denfry.promptcritical.mod.ops.OpsWorld;
import dev.denfry.promptcritical.mod.ops.WaterTreatmentBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.combustion.BoilerOutputs;
import dev.denfry.promptcritical.sim.combustion.BoilerRupture;
import dev.denfry.promptcritical.sim.core.CommandSource;
import dev.denfry.promptcritical.sim.core.SimCommand;
import dev.denfry.promptcritical.sim.ops.Baseline;
import dev.denfry.promptcritical.sim.ops.CommissioningDefectKind;
import dev.denfry.promptcritical.sim.ops.FaultSource;
import dev.denfry.promptcritical.sim.ops.MaintenanceFaultKind;
import dev.denfry.promptcritical.sim.ops.OpsSubsystem;
import dev.denfry.promptcritical.sim.ops.UnitKind;
import dev.denfry.promptcritical.sim.plant.BoilerUnit;
import dev.denfry.promptcritical.sim.plant.SteamPlant;
import dev.denfry.promptcritical.sim.utilities.WaterGrade;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * BE жаротрубного котла (07 §7.9.4) на контроллере. Двойник — {@link BoilerUnit} подсистемы {@link SteamPlant}: BE
 * каждый тик отдаёт ему запас питательного бака и высоту трубы, списывает взятую воду, выгружает золу шлаком и
 * показывает состояние (топка, свист ПК, струя пара из выплавленной пробки, дым из трубы, водоуказательное стекло).
 *
 * <p>Питательный бак 4 м³ наполняют трубы Create (любая грань любой клетки) или ведро; вода — {@code #minecraft:water},
 * {@code raw_water} и вода водоподготовки: жёсткость сорта ({@link WaterGrade}) растит накипь (13 §13.6.7).
 * Пусконаладка и обслуживание — {@link OpsMachine}: смещённый манометр, засорённое стекло, подрыв ПК и шов по
 * дефектам монтажа, розжиг через разрешение пуска, пробный пуск. Топливо — руками в топку или цепной решёткой; зола выдаётся шлаком {@code boiler_slag}
 * (по {@link #SLAG_ASH_KG} кг на предмет) — воронками или пустой рукой.
 */
public class FireTubeBoilerBlockEntity extends DeviceBlockEntity implements PressureSource, PanelHost, OpsMachine {
    public static final int FEED_TANK_MB = 4000;
    /** Температура наружного воздуха, К (климат биомов 07 §7.13.1 — позже). */
    public static final double AMBIENT_K = 288.15;
    /** Зола на предмет шлака, кг. */
    public static final double SLAG_ASH_KG = 10;
    /** Сколько угля помещается на решётке, кг. */
    public static final double MAX_BED_KG = 500;
    /** Шкала водоуказательного стекла: от потолка жаровой трубы − 0,10 м до + 0,45 м. */
    public static final double GLASS_BOTTOM_M = 0.35, GLASS_SPAN_M = 0.55;
    private static final int SYNC_PERIOD = 4, CHIMNEY_PERIOD = 20, WHISTLE_PERIOD = 30;
    /** Проектная толщина накипи — 100 % строки HX_FOULING щита обслуживания, м (13 §13.6.7). */
    public static final double DESIGN_SCALE_M = 1e-3;
    /** Шкала манометра, Па: смещение нуля — в её долях. */
    public static final double GAUGE_SCALE_PA = 1.6e6;
    private static final double TICK_S = 0.05;

    private final FluidTank feed = new FluidTank(FEED_TANK_MB,
            fs -> fs.is(FluidTags.WATER) || WaterTreatmentBlockEntity.grade(fs.getFluid()) != null) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final IFluidHandler feedInput = new FillOnly(feed);
    private final ItemStackHandler slag = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final IItemHandler slagOutput = new ExtractOnly(slag);
    /** Совок щита: топливо, которое кнопка «Забросить» кидает на решётку. */
    private final ItemStackHandler shovel = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return BoilerFuels.key(stack) != null;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final IItemHandlerModifiable panelItems = new CombinedInvWrapper(shovel, slag);
    private double feedDebtKg;
    private int ticks, whistleCooldown;
    private boolean plugWasMelted;
    // видимое клиенту
    private float pressurePa, glass;
    private int chimneyBlocks;
    private boolean lit, reliefOpen, plugMelted, venting, smoky;
    private boolean unaccepted, lowShown;
    private float sentP = -1, sentGlass = -1;
    private int sentFlags = -1;

    public FireTubeBoilerBlockEntity(BlockPos pos, BlockState state) {
        super(BoilerBlockEntities.FIRE_TUBE_BOILER.get(), pos, state);
    }

    public IFluidHandler feedInput() {
        return feedInput;
    }

    public IItemHandler slagOutput() {
        return slagOutput;
    }

    public FluidTank feedTank() {
        return feed;
    }

    /** Двойник в домене (сервер); создаётся при первом обращении. */
    @Nullable
    public BoilerUnit unit() {
        SimLevelBinding b = level instanceof ServerLevel s ? SimBindings.get(s) : null;
        if (b == null || simId().isNone() || !b.world().topology().exists(simId())) return null;
        SteamPlant plant = b.world().subsystem(SteamPlant.class);
        return plant == null ? null : plant.addBoiler(simId(), AMBIENT_K);
    }

    @Override
    public double gaugePressurePa() {
        return Math.max(0, pressurePa - SteamPlant.ATMOSPHERE_PA);
    }

    /** Уровень по стеклу, доля шкалы [0, 1]. */
    public float glassFraction() {
        return glass;
    }

    public boolean venting() {
        return venting;
    }

    public boolean reliefOpen() {
        return reliefOpen;
    }

    public Direction facing() {
        return getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
    }

    // --- тик ---

    public void tick() {
        ticks++;
        if (level.isClientSide) {
            clientTick();
            return;
        }
        BoilerUnit u = unit();
        if (u == null) return;
        feedDebtKg += u.takeFeedUsed();
        int mb = (int) Math.min(feedDebtKg, feed.getFluidAmount());
        if (mb > 0) {
            feed.drain(mb, IFluidHandler.FluidAction.EXECUTE);
            feedDebtKg -= mb;
        }
        if (feed.isEmpty()) feedDebtKg = 0;
        u.setFeedSupply(Math.max(0, feed.getFluidAmount() - feedDebtKg), feedTempK());
        WaterGrade grade = feed.isEmpty() ? null : WaterTreatmentBlockEntity.grade(feed.getFluid().getFluid());
        u.setFeedHardness(grade == null ? BoilerUnit.RAW_HARDNESS : grade.quality().hardnessMolEqM3());
        OpsSubsystem ops = OpsWorld.ops(level);
        if (ops != null && (ops.unit(simId()) == null || ticks % CHIMNEY_PERIOD == 1)) commissioning(u);
        if (ticks % CHIMNEY_PERIOD == 1) {
            chimneyBlocks = countChimney();
            u.setChimneyHeight(SteamPlant.UPTAKE_HEIGHT_M + chimneyBlocks);
        }
        while (u.ashKg() >= SLAG_ASH_KG) {
            if (!slag.insertItem(0, new ItemStack(PcItems.BOILER_SLAG.get()), false).isEmpty()) break;
            u.removeAsh(SLAG_ASH_KG);
        }

        double gauge = Double.isNaN(u.pressurePa()) ? 0 : u.pressurePa() - SteamPlant.ATMOSPHERE_PA;
        boolean glassBlocked = false;
        if (ops != null && ops.unit(simId()) != null) {
            u.setCommissioning(ops.reliefLiftPa(simId(), u.spec().reliefSetPressure()),
                    ops.burstFactor(simId(), u.spec().burstPressureFactor()));
            gauge = ops.commissioning().gaugeReading(simId(), gauge, GAUGE_SCALE_PA);
            glassBlocked = ops.commissioning().has(simId(), CommissioningDefectKind.GAUGE_GLASS_BLOCKED);
            unaccepted = !ops.canStart(simId()).accepted();
            boolean tripped = u.plugMelted() || u.rupture() != BoilerRupture.NONE;
            double load = u.outputs().fuelBurnKgS / u.spec().nominalFuelKgS();
            ops.observeTrial(simId(), TICK_S, u.lit(), Double.isFinite(load) ? load : 0, tripped, opsCurrent());
            if (ticks % 10 == 0 && ops.seamWeeping(simId(), gauge) && level instanceof ServerLevel sl) {
                Vec3 p = BoilerGeometry.world(worldPosition, facing(), 8, 16, 30);
                sl.sendParticles(ParticleTypes.DRIPPING_WATER, p.x, p.y, p.z, 2, 0.7, 0.3, 0.7, 0);
                sl.sendParticles(ParticleTypes.CLOUD, p.x, p.y + 0.6, p.z, 1, 0.5, 0.1, 0.5, 0.01);
            }
        }
        pressurePa = (float) (SteamPlant.ATMOSPHERE_PA + gauge);
        float realGlass = (float) Math.max(0, Math.min(1, (u.gaugeGlassLevelM() - GLASS_BOTTOM_M) / GLASS_SPAN_M));
        // засорённое стекло не показывает падения уровня
        glass = glassBlocked ? Math.max(glass, realGlass) : realGlass;
        lit = u.lit();
        smoky = u.smoky();
        reliefOpen = u.reliefOpen();
        plugMelted = u.plugMelted();
        venting = plugMelted && u.ventFlowKgS() > 0.02;
        boolean exploded = u.rupture() != BoilerRupture.NONE;
        lowShown = !exploded && u.gaugeGlassLevelM() < GLASS_BOTTOM_M && glass <= 0;
        boolean lowWater = lowShown;
        BlockState s = getBlockState();
        BlockState t = s.setValue(BlockStateProperties.LIT, lit && !exploded).setValue(PcProperties.LOW_WATER, lowWater)
                .setValue(PcProperties.EXPLODED, exploded);
        if (t != s) level.setBlock(worldPosition, t, Block.UPDATE_CLIENTS);
        if (exploded != s.getValue(PcProperties.EXPLODED)) {
            BlockPos rear = BoilerGeometry.offset(worldPosition, facing(), 0, 0, 3);
            BlockState rs = level.getBlockState(rear);
            if (rs.is(s.getBlock())) level.setBlock(rear, rs.setValue(PcProperties.EXPLODED, exploded), Block.UPDATE_CLIENTS);
        }
        sounds();
        if (ticks % SYNC_PERIOD == 0) syncIfChanged();
    }

    /** Установка в пусконаладке и накипь строкой щита обслуживания. */
    private void commissioning(BoilerUnit u) {
        OpsSubsystem.Unit ou = OpsWorld.ensure(level, simId(), UnitKind.FIRE_TUBE_BOILER, u.spec().workingPressurePa(), false);
        OpsSubsystem ops = OpsWorld.ops(level);
        if (ou != null && ops != null) ops.attach(simId(), FaultSource.of(MaintenanceFaultKind.HX_FOULING,
                () -> u.boiler().scaleM() / DESIGN_SCALE_M, () -> u.boiler().setScale(0)));
    }

    private double feedTempK() {
        FluidStack f = feed.getFluid();
        return f.isEmpty() ? AMBIENT_K : Math.min(370, f.getFluidType().getTemperature(f));
    }

    /** Звенья трубы над дымоходом (рядом с задним сегментом, над газоходом). */
    private int countChimney() {
        BlockPos p = BoilerGeometry.offset(worldPosition, facing(), 0, 2, 4);
        int n = 0;
        while (n < 128 && level.getBlockState(p.above(n)).getBlock() instanceof ChimneyBlock) n++;
        return n;
    }

    private void sounds() {
        if (whistleCooldown > 0) whistleCooldown--;
        if (reliefOpen && whistleCooldown == 0) {
            playAt(valvePos(), BoilerSounds.whistle(), 2.0f, 1.0f);
            whistleCooldown = WHISTLE_PERIOD;
        }
        if (plugMelted && !plugWasMelted) {
            playAt(doorPos(), BoilerSounds.whistleLow(), 2.0f, 1.0f);
            playAt(doorPos(), SoundEvents.FIRE_EXTINGUISH, 1.5f, 0.6f);
        } else if (venting && ticks % 40 == 0) {
            playAt(doorPos(), BoilerSounds.steam(), 1.0f, 1.0f);
        }
        plugWasMelted = plugMelted;
    }

    private void playAt(Vec3 p, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        level.playSound(null, p.x, p.y, p.z, sound, SoundSource.BLOCKS, volume, pitch);
    }

    Vec3 valvePos() {
        return BoilerGeometry.world(worldPosition, facing(), 8, 28, 6);
    }

    Vec3 doorPos() {
        return BoilerGeometry.world(worldPosition, facing(), 8, -4, -17);
    }

    private Vec3 smokePos() {
        if (chimneyBlocks > 0) {
            BlockPos top = BoilerGeometry.offset(worldPosition, facing(), 0, 2, 4).above(chimneyBlocks);
            return Vec3.atBottomCenterOf(top);
        }
        return BoilerGeometry.world(worldPosition, facing(), 8, 32, 77);
    }

    private void clientTick() {
        var r = level.random;
        MachineFx fx = MachineFx.of(level, worldPosition, facing());
        if (fx != null && lit) {
            if (fx.chance(3)) fx.flame(fx.at(0.5, 0.5, -16.3), 0.15);
            if (fx.chance(3)) fx.flame(fx.at(15.5, 0.5, -16.3), 0.15);
            if (fx.chance(0.5)) fx.sound(doorPos(), SoundEvents.FIRE_AMBIENT, 0.9f, 0.9f);
        }
        if (lit && ticks % 3 == 0) {
            // чёрный дым — недожог: воздуха не хватает на подачу
            Vec3 p = smokePos();
            level.addAlwaysVisibleParticle(smoky ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CAMPFIRE_COSY_SMOKE, true,
                    p.x + (r.nextDouble() - 0.5) * 0.3, p.y, p.z + (r.nextDouble() - 0.5) * 0.3, 0, 0.07, 0);
            if (smoky) level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, p.x, p.y, p.z, 0, 0.05, 0);
        }
        if (reliefOpen) {
            Vec3 p = valvePos();
            for (int i = 0; i < 3; i++) level.addParticle(ParticleTypes.CLOUD, p.x, p.y, p.z,
                    (r.nextDouble() - 0.5) * 0.1, 0.35 + r.nextDouble() * 0.15, (r.nextDouble() - 0.5) * 0.1);
        }
        if (venting) {
            Vec3 p = doorPos();
            Vec3 out = Vec3.atLowerCornerOf(facing().getNormal()).scale(0.25);
            for (int i = 0; i < 2; i++) level.addParticle(ParticleTypes.CLOUD, p.x, p.y, p.z,
                    out.x + (r.nextDouble() - 0.5) * 0.05, 0.05, out.z + (r.nextDouble() - 0.5) * 0.05);
        }
    }

    // --- игрок ---

    /** Предмет, с которым котёл что-то делает (топливо, огниво, пробка, ёмкость с водой); проверяется и на клиенте. */
    public static boolean accepts(ItemStack stack) {
        return BoilerFuels.key(stack) != null || stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE)
                || stack.is(Items.TORCH) || stack.is(PcItems.FUSIBLE_PLUG.get())
                || FluidUtil.getFluidHandler(stack).isPresent();
    }

    /** Предмет в руке игрока у котла; {@code PASS_TO_DEFAULT} — не наш. Только сервер. */
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        BoilerUnit u = unit();
        SimLevelBinding b = SimBindings.get((ServerLevel) level);
        if (u == null || b == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (FluidUtil.interactWithFluidHandler(player, hand, feedInput)) return ItemInteractionResult.SUCCESS;
        CommandSource source = new CommandSource.Panel(player.getUUID(), worldPosition.asLong());
        if (u.rupture() != BoilerRupture.NONE) {
            player.displayClientMessage(Component.translatable(BoilerText.RUPTURED), true);
            return ItemInteractionResult.CONSUME;
        }
        if (BoilerFuels.key(stack) != null) {
            if (charge(player, stack, b, u)) stack.consume(1, player);
            return ItemInteractionResult.SUCCESS;
        }
        if (stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE) || stack.is(Items.TORCH)) {
            if (!u.lit() && !OpsActions.startAllowed(player, level, this)) return ItemInteractionResult.CONSUME;
            b.submit(new SimCommand.Operate(simId(), SteamPlant.IGNITE), source);
            if (stack.is(Items.FLINT_AND_STEEL)) stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            else if (stack.is(Items.FIRE_CHARGE)) stack.consume(1, player);
            playAt(doorPos(), SoundEvents.FLINTANDSTEEL_USE, 1.0f, 1.0f);
            return ItemInteractionResult.SUCCESS;
        }
        if (stack.is(PcItems.FUSIBLE_PLUG.get())) {
            if (!u.plugMelted() && u.boiler().plugFitted()) {
                player.displayClientMessage(Component.translatable(BoilerText.PLUG_NOT_NEEDED), true);
            } else if (!(u.pressurePa() < SteamPlant.OPEN_PRESSURE_PA)) {
                player.displayClientMessage(Component.translatable(BoilerText.PLUG_UNDER_PRESSURE), true);
            } else {
                b.submit(new SimCommand.Operate(simId(), SteamPlant.FIT_FUSIBLE_PLUG), source);
                stack.consume(1, player);
                player.displayClientMessage(Component.translatable(BoilerText.PLUG_FITTED), true);
            }
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Забросить одну порцию топлива {@code stack} на решётку; {@code false} — некуда или котёл разрушен. Сервер. */
    private boolean charge(Player player, ItemStack stack, SimLevelBinding b, BoilerUnit u) {
        String fuel = BoilerFuels.key(stack);
        if (fuel == null) return false;
        if (u.rupture() != BoilerRupture.NONE) {
            player.displayClientMessage(Component.translatable(BoilerText.RUPTURED), true);
            return false;
        }
        double kg = BoilerFuels.itemMassKg();
        if (u.boiler().bedKg() + kg > MAX_BED_KG) {
            player.displayClientMessage(Component.translatable(BoilerText.BED_FULL), true);
            return false;
        }
        b.submit(new SimCommand.ChargeFuel(simId(), fuel, kg), new CommandSource.Panel(player.getUUID(), worldPosition.asLong()));
        player.displayClientMessage(Component.translatable(BoilerText.CHARGED, String.format(Locale.ROOT, "%.0f", kg)), true);
        playAt(doorPos(), SoundEvents.GRAVEL_PLACE, 0.8f, 0.8f);
        return true;
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return BoilerPanels.BOILER;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return panelItems;
    }

    @Override
    public void readPanel(int[] out) {
        BoilerUnit u = unit();
        Arrays.fill(out, 0);
        out[BoilerPanels.B_FEED_L] = feed.getFluidAmount();
        if (u == null) return;
        BoilerOutputs o = u.outputs();
        out[BoilerPanels.B_P_KPA] = (int) Math.round(gaugePressurePa() / 1000);
        out[BoilerPanels.B_GLASS_PERMIL] = Math.round(glass * 1000);
        out[BoilerPanels.B_BED_KG10] = (int) Math.round(u.boiler().bedKg() * 10);
        out[BoilerPanels.B_FLUE_C10] = celsius10(o.fluegasExitK);
        out[BoilerPanels.B_DRAFT_PA10] = finite(o.draftPa * 10);
        out[BoilerPanels.B_WATER_C10] = celsius10(u.waterTempK());
        out[BoilerPanels.B_CROWN_C10] = celsius10(o.crownTempK);
        out[BoilerPanels.B_EFF_PERMIL] = Double.isNaN(o.efficiency) ? -1 : (int) Math.round(o.efficiency * 1000);
        out[BoilerPanels.B_RELIEF_KPA] = (int) Math.round((u.spec().reliefSetPressure() - SteamPlant.ATMOSPHERE_PA) / 1000);
        boolean ruptured = u.rupture() != BoilerRupture.NONE;
        out[BoilerPanels.B_FLAGS] = (u.lit() && !ruptured ? 1 << BoilerPanels.F_LIT : 0)
                | (u.reliefOpen() ? 1 << BoilerPanels.F_RELIEF : 0)
                | (u.plugMelted() ? 1 << BoilerPanels.F_PLUG : 0)
                | (u.smoky() ? 1 << BoilerPanels.F_SMOKY : 0)
                | (!ruptured && lowShown ? 1 << BoilerPanels.F_LOW_WATER : 0)
                | (ruptured ? 1 << BoilerPanels.F_RUPTURED : 0)
                | (unaccepted ? 1 << BoilerPanels.F_UNACCEPTED : 0)
                | (u.draining() ? 1 << BoilerPanels.F_DRAIN : 0);
    }

    static int celsius10(double kelvin) {
        return finite((kelvin - 273.15) * 10);
    }

    static int finite(double v) {
        return Double.isFinite(v) ? (int) Math.round(v) : 0;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        BoilerUnit u = unit();
        SimLevelBinding b = SimBindings.get((ServerLevel) level);
        if (id == BoilerPanels.BUTTON_DRAIN && u != null && b != null) {
            b.submit(new SimCommand.Operate(simId(), SteamPlant.DRAIN), new CommandSource.Panel(player.getUUID(), worldPosition.asLong()));
            player.displayClientMessage(OpsText.msg(u.draining() ? "drain_closed" : "drain_open"), true);
            playAt(doorPos(), SoundEvents.BUCKET_EMPTY, 0.8f, 0.7f);
            return;
        }
        if (id != BoilerPanels.BUTTON_CHARGE) return;
        ItemStack s = shovel.getStackInSlot(0);
        if (u == null || b == null || s.isEmpty()) return;
        if (charge(player, s, b, u)) shovel.extractItem(0, 1, false);
    }

    /** Пустая рука с Shift: забрать шлак, показать слой, бак, топку и пробку. Только сервер. */
    public InteractionResult use(Player player) {
        BoilerUnit u = unit();
        if (u == null) return InteractionResult.PASS;
        ItemStack s = slag.extractItem(0, 64, false);
        if (!s.isEmpty() && !player.addItem(s)) player.drop(s, false);
        player.displayClientMessage(Component.translatable(BoilerText.BOILER_STATUS,
                String.format(Locale.ROOT, "%.0f", u.boiler().bedKg()), feed.getFluidAmount(),
                Component.translatable(u.lit() ? BoilerText.FURNACE_LIT : BoilerText.FURNACE_OUT),
                Component.translatable(u.plugMelted() ? BoilerText.PLUG_MELTED : BoilerText.PLUG_OK)), true);
        return InteractionResult.SUCCESS;
    }

    // --- сохранение и клиент ---

    private int flags() {
        return (lit ? 1 : 0) | (reliefOpen ? 2 : 0) | (plugMelted ? 4 : 0) | (venting ? 8 : 0) | (smoky ? 16 : 0)
                | chimneyBlocks << 5;
    }

    private void syncIfChanged() {
        int f = flags();
        if (f == sentFlags && Math.abs(pressurePa - sentP) < 5_000 && Math.abs(glass - sentGlass) < 0.01f) return;
        sentFlags = f;
        sentP = pressurePa;
        sentGlass = glass;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("feed", feed.writeToNBT(registries, new CompoundTag()));
        tag.put("slag", slag.serializeNBT(registries));
        tag.put("shovel", shovel.serializeNBT(registries));
        tag.putDouble("feed_debt", feedDebtKg);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("feed")) feed.readFromNBT(registries, tag.getCompound("feed"));
        if (tag.contains("slag")) slag.deserializeNBT(registries, tag.getCompound("slag"));
        if (tag.contains("shovel")) shovel.deserializeNBT(registries, tag.getCompound("shovel"));
        feedDebtKg = tag.getDouble("feed_debt");
        if (tag.contains("view_p")) {
            pressurePa = tag.getFloat("view_p");
            glass = tag.getFloat("view_glass");
            int f = tag.getInt("view_flags");
            lit = (f & 1) != 0;
            reliefOpen = (f & 2) != 0;
            plugMelted = (f & 4) != 0;
            venting = (f & 8) != 0;
            smoky = (f & 16) != 0;
            chimneyBlocks = f >>> 5;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("view_p", pressurePa);
        tag.putFloat("view_glass", glass);
        tag.putInt("view_flags", flags());
        tag.put("feed", feed.writeToNBT(registries, new CompoundTag()));
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // --- эксплуатация ---

    @Override
    public UnitKind opsKind() {
        return UnitKind.FIRE_TUBE_BOILER;
    }

    @Override
    public boolean opsStopped() {
        BoilerUnit u = unit();
        return u == null || !u.lit();
    }

    @Override
    public Vec3 opsBearingPos() {
        return doorPos();
    }

    @Override
    public Baseline opsCurrent() {
        BoilerUnit u = unit();
        if (u == null) return Baseline.NONE;
        BoilerOutputs o = u.outputs();
        return new Baseline(o.efficiency, o.fluegasExitK, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
    }

    /** Котёл по клетке конструкции. */
    @Nullable
    public static FireTubeBoilerBlockEntity at(net.minecraft.world.level.Level level, BlockPos cell) {
        BlockPos c = BoilerGeometry.controllerOf(level, cell);
        return c != null && level.getBlockEntity(c) instanceof FireTubeBoilerBlockEntity be ? be : null;
    }

    /** Бак только на налив: трубы Create не высасывают воду из котла. */
    private record FillOnly(FluidTank tank) implements IFluidHandler {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int t) {
            return tank.getFluid();
        }

        @Override
        public int getTankCapacity(int t) {
            return tank.getCapacity();
        }

        @Override
        public boolean isFluidValid(int t, FluidStack stack) {
            return tank.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return tank.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }

    /** Шлак только на выдачу. */
    private record ExtractOnly(ItemStackHandler inv) implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inv.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return inv.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return inv.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }
}
