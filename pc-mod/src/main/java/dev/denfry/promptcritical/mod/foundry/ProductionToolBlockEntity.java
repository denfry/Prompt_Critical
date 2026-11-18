package dev.denfry.promptcritical.mod.foundry;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import dev.denfry.promptcritical.sim.metallurgy.CastAlloy;
import dev.denfry.promptcritical.sim.metallurgy.CastingDefect;
import dev.denfry.promptcritical.sim.metallurgy.CastingSpec;
import dev.denfry.promptcritical.sim.metallurgy.FoundrySubsystem;
import dev.denfry.promptcritical.sim.metallurgy.MachinedPart;
import dev.denfry.promptcritical.sim.metallurgy.Machining.WorkMaterial;
import dev.denfry.promptcritical.sim.metallurgy.ProductionToolUnit;
import dev.denfry.promptcritical.sim.metallurgy.ProductionToolUnit.Kind;
import dev.denfry.promptcritical.sim.metallurgy.ProductionToolUnit.Result;
import dev.denfry.promptcritical.sim.metallurgy.ProductionToolUnit.Workpiece;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/**
 * Станок станочного парка — {@code milling_machine}, {@code shaping_machine} или {@code surface_grinder}: заготовка на
 * входе, инструмент (фреза, резец, круг) в служебном слоте — его износ или засаливание зеркалит прочность предмета,
 * готовая деталь с компонентом {@code machined_finish} (Ra, квалитет) или лом на выходе. Ступень подачи переключает
 * кнопка щита. Круг, разогнанный выше 1,5 рабочей скорости, разрывает — осколки ранят стоящих рядом.
 */
public final class ProductionToolBlockEntity extends KineticMachineBlockEntity<ProductionToolUnit> implements PanelHost {
    private static final MachineInventory.Role[] ROLES = {MachineInventory.Role.INPUT, MachineInventory.Role.SERVICE,
            MachineInventory.Role.OUTPUT};
    /** Осколки круга: радиус поражения, блоки, и урон. */
    private static final double BURST_RADIUS = 3;
    private static final float BURST_DAMAGE = 6;

    private final Kind kind;
    private final MachineInventory inv = new MachineInventory(ROLES, this::valid, this::setChanged);
    /** Последняя снятая деталь — для щита (не сохраняется). */
    private int lastReject = -1, lastRa100 = -1, lastIt = -1;

    private ProductionToolBlockEntity(BlockEntityType<?> type, Kind kind, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.kind = kind;
    }

    public static ProductionToolBlockEntity milling(BlockPos pos, BlockState state) {
        return new ProductionToolBlockEntity(FoundryBlockEntities.MILLING_MACHINE.get(), Kind.MILLING, pos, state);
    }

    public static ProductionToolBlockEntity shaper(BlockPos pos, BlockState state) {
        return new ProductionToolBlockEntity(FoundryBlockEntities.SHAPING_MACHINE.get(), Kind.SHAPER, pos, state);
    }

    public static ProductionToolBlockEntity grinder(BlockPos pos, BlockState state) {
        return new ProductionToolBlockEntity(FoundryBlockEntities.SURFACE_GRINDER.get(), Kind.GRINDER, pos, state);
    }

    /** Инструмент станка: фреза, строгальный резец, шлифовальный круг. */
    public static Item toolItem(Kind kind) {
        return switch (kind) {
            case MILLING -> PcItems.MILLING_CUTTER.get();
            case SHAPER -> PcItems.CUTTING_TOOL.get();
            case GRINDER -> PcItems.GRINDING_WHEEL.get();
        };
    }

    /** Предмет детали. */
    public static Item partItem(MachinedPart p) {
        if (p == MachinedPart.SPUR_GEAR) return PcItems.SPUR_GEAR.get();
        if (p == MachinedPart.KEYED_SHAFT) return PcItems.KEYED_SHAFT.get();
        if (p == MachinedPart.MACHINE_SLIDEWAY) return PcItems.MACHINE_SLIDEWAY.get();
        if (p == MachinedPart.PLANED_PLATE) return PcItems.PLANED_PLATE.get();
        return PcItems.GROUND_PLATE.get();
    }

    /**
     * Заготовка из предмета: отливка (точёная заготовка шестерни, станина, плита), пруток и лист проката, строганая
     * плита под шлифование; {@code null} — станкам парка не нужна.
     */
    @Nullable
    public static Workpiece workpiece(ItemStack s) {
        CastingSpec c = s.get(FoundryComponents.CASTING_SPEC.get());
        if (c != null) {
            MachinedPart p = MachinedPart.fromCasting(c);
            CastAlloy a = CastAlloy.byId(c.alloyId());
            return p == null || a == null ? null : new Workpiece(p, WorkMaterial.of(a), c.defect() == CastingDefect.NONE);
        }
        if (s.is(PcItems.STEEL_ROD.get())) return new Workpiece(MachinedPart.KEYED_SHAFT, WorkMaterial.STEEL_45, true);
        if (s.is(PcItems.STEEL_PLATE.get())) return new Workpiece(MachinedPart.PLANED_PLATE, WorkMaterial.STEEL_45, true);
        if (s.is(PcItems.PLANED_PLATE.get())) {
            FoundryComponents.Finish f = s.get(FoundryComponents.MACHINED_FINISH.get());
            return new Workpiece(MachinedPart.GROUND_PLATE, f == null ? WorkMaterial.CAST_IRON : f.workMaterial(), true);
        }
        return null;
    }

    /** Что выдаёт станок: годная деталь с Ra и квалитетом или лом. */
    public static ItemStack product(Result r) {
        if (!r.accepted()) return new ItemStack(PcItems.STEEL_SCRAP.get());
        ItemStack s = new ItemStack(partItem(r.part()));
        s.set(FoundryComponents.MACHINED_FINISH.get(), new FoundryComponents.Finish(r.material().name().toLowerCase(Locale.ROOT),
                (float) r.raUm(), r.itGrade()));
        return s;
    }

    public Kind kind() {
        return kind;
    }

    private boolean valid(int slot, ItemStack s) {
        if (slot == 1) return s.is(toolItem(kind));
        Workpiece w = workpiece(s);
        return w != null && w.part().machine() == kind;
    }

    @Override
    @Nullable
    protected ProductionToolUnit unit(SimLevelBinding b) {
        return b.world().subsystem(FoundrySubsystem.class).unit(b.world().topology(), simId()) instanceof ProductionToolUnit u ? u : null;
    }

    @Override
    protected ShaftInput shaft(ProductionToolUnit u) {
        return u.shaft;
    }

    @Override
    protected double powerW(ProductionToolUnit u) {
        return u.powerW();
    }

    @Override
    protected double ratedW() {
        return kind.ratedW;
    }

    @Override
    protected double nominalRpm() {
        return ProductionToolUnit.NOMINAL_SHAFT_RPM;
    }

    @Override
    protected void serverTick(SimLevelBinding b, ProductionToolUnit u) {
        if (u.takeWheelBurst()) burst();
        syncTool(u);
        ItemStack in = inv.getStackInSlot(0);
        Workpiece w = in.isEmpty() ? null : workpiece(in);
        if (u.work() == null && w != null && u.load(w)) in.shrink(1);
        Result r = u.peek();
        if (r != null && inv.outputFits(product(r))) {
            u.unload();
            inv.output(product(r));
            lastReject = r.reject().ordinal();
            lastRa100 = (int) Math.round(r.raUm() * 100);
            lastIt = r.itGrade();
            if (!r.accepted() && level != null)
                level.playSound(null, worldPosition, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.3f, 1.4f);
        }
        setStateFlag(PcProperties.RUNNING, u.running());
    }

    /** Разрыв круга: круг пропадает, осколки ранят игроков рядом. */
    private void burst() {
        inv.setStackInSlot(1, ItemStack.EMPTY);
        if (level == null) return;
        level.playSound(null, worldPosition, SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 1.5f, 0.6f);
        level.playSound(null, worldPosition, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.4f, 1.8f);
        AABB box = new AABB(worldPosition).inflate(BURST_RADIUS);
        for (Player p : level.getEntitiesOfClass(Player.class, box)) {
            p.hurt(level.damageSources().generic(), BURST_DAMAGE);
            p.displayClientMessage(FoundryText.msg("wheel_burst"), true);
        }
    }

    /** Предмет инструмента ↔ инструмент домена, как у токарного ({@link MachineToolBlockEntity}). */
    private void syncTool(ProductionToolUnit u) {
        ItemStack tool = inv.getStackInSlot(1);
        boolean inUnit = !Double.isNaN(u.toolWear());
        if (!tool.isEmpty() && !inUnit) {
            u.insertTool(tool.getMaxDamage() > 0 ? (double) tool.getDamageValue() / tool.getMaxDamage() : 0);
        } else if (tool.isEmpty() && inUnit) {
            u.removeTool();
        } else if (inUnit && tool.getMaxDamage() > 0) {
            int damage = (int) Math.round(u.toolWear() * tool.getMaxDamage());
            if (damage != tool.getDamageValue()) {
                tool.setDamageValue(Math.min(damage, tool.getMaxDamage() - 1));
                setChanged();
            }
        }
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return inv.external();
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return valid(0, stack) || valid(1, stack);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (!valid(0, stack) && !valid(1, stack)) {
            if (stack.has(FoundryComponents.CASTING_SPEC.get()) || workpiece(stack) != null)
                player.displayClientMessage(FoundryText.msg("not_for_this_machine"), true);
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        player.setItemInHand(hand, inv.insertFromPlayer(stack));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        inv.giveOutputs(player);
        ProductionToolUnit u = unit();
        if (u == null) return InteractionResult.SUCCESS;
        Object wear = Double.isNaN(u.toolWear()) ? FoundryText.msg("no_tool") : FoundryText.num(u.toolWear() * 100);
        player.displayClientMessage(u.work() == null
                ? FoundryText.msg("shop_idle", FoundryText.num(u.speed()), FoundryText.num(u.feed()), wear)
                : FoundryText.msg("shop", FoundryText.part(u.work().part()), FoundryText.num(u.progress() * 100),
                        FoundryText.num(u.speed()), FoundryText.num(u.feed()), wear), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        ProductionToolUnit u = unit();
        if (id != FoundryPanels.BUTTON_FEED || u == null) return;
        player.displayClientMessage(FoundryText.msg("feed_set", FoundryText.num(u.cycleFeed())), true);
    }

    @Override
    protected void clientTick() {
        MachineFx fx = fx();
        if (fx == null || !runningState()) return;
        switch (kind) {
            case MILLING -> {
                if (fx.chance(14)) fx.chips(fx.at(8, 14.5, 4.8), 1);
            }
            case SHAPER -> {
                // резец скребёт по металлу на каждом рабочем ходе: двойной ход — два оборота вала (×0,5)
                int period = Math.max(4, Math.round(1200f / Math.max(1f, Math.abs(getSpeed()) * (float) kind.gear)));
                if (level.getGameTime() % period == 0) {
                    fx.chips(fx.at(25, 13, 8), 2);
                    fx.sound(fx.at(22, 10, 8), dev.denfry.promptcritical.mod.sound.PcSfx.SHAPER_STROKE.get(), 0.6f, 1.0f);
                }
            }
            case GRINDER -> {
                if (fx.chance(30)) fx.sparks(fx.at(13, 10.5, 8), 2);
            }
        }
    }

    @Override
    public PanelLayout panelLayout() {
        return switch (kind) {
            case MILLING -> FoundryPanels.MILLING;
            case SHAPER -> FoundryPanels.SHAPER;
            case GRINDER -> FoundryPanels.GRINDER;
        };
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return inv;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[FoundryPanels.SHAFT_RPM10] = shaftRpm10();
        out[FoundryPanels.P_WEAR_PERMIL] = -1;
        out[FoundryPanels.P_PART] = -1;
        out[FoundryPanels.P_LAST_REJECT] = lastReject;
        out[FoundryPanels.P_LAST_RA100] = lastRa100;
        out[FoundryPanels.P_LAST_IT] = lastIt;
        ProductionToolUnit u = unit();
        if (u == null) return;
        out[FoundryPanels.FLAGS] = (u.running() ? 1 << FoundryPanels.F_RUNNING : 0) | (u.burnt() ? 1 << FoundryPanels.F_HELD : 0);
        out[FoundryPanels.P_SPEED] = (int) Math.round(u.speed());
        out[FoundryPanels.P_PROGRESS_PERMIL] = (int) Math.round(u.progress() * 1000);
        out[FoundryPanels.P_FEED_UM] = (int) Math.round(u.feed() * 1000);
        out[FoundryPanels.P_RA100] = Double.isNaN(u.raUm()) ? -1 : (int) Math.round(u.raUm() * 100);
        out[FoundryPanels.P_POWER_W] = (int) Math.round(u.powerW());
        out[FoundryPanels.P_VC10] = (int) Math.round(u.cuttingSpeed() * 10);
        if (!Double.isNaN(u.toolWear())) out[FoundryPanels.P_WEAR_PERMIL] = (int) Math.round(u.toolWear() * 1000);
        if (u.work() != null) out[FoundryPanels.P_PART] = Arrays.asList(MachinedPart.values()).indexOf(u.work().part());
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!clientPacket) tag.put("inventory", inv.save(registries));
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (!clientPacket && tag.contains("inventory")) inv.load(registries, tag.getCompound("inventory"));
    }
}
