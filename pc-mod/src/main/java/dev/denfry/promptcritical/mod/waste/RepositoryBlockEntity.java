package dev.denfry.promptcritical.mod.waste;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.environment.ActivityData;
import dev.denfry.promptcritical.mod.environment.DosimeterItem;
import dev.denfry.promptcritical.mod.environment.RadiationService;
import dev.denfry.promptcritical.mod.environment.RiverService;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.environment.RiverCatchment;
import dev.denfry.promptcritical.sim.radiation.PointSource;
import dev.denfry.promptcritical.sim.radiation.ShieldMaterial;
import dev.denfry.promptcritical.sim.waste.Repository;
import dev.denfry.promptcritical.sim.waste.WasteClassification;
import dev.denfry.promptcritical.sim.waste.WasteInventory;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Пункт захоронения ({@link Repository}): блок — управление одного отсека (модуль приповерхностного хранилища или
 * штрек ПГЗРО). Упаковки — рукой или воронкой в приёмный слот; принятая исчезает в отсеке (её состав — в инвентаре
 * пункта), отвергнутая остаётся в слоте, а щит говорит почему. Медные контейнеры для стекла (только глубинный) — во
 * второй слот, по одному на стеклоблок.
 *
 * <p>Инвентарь распадается по игровому времени ({@value ActivityData#GAME_S_PER_TICK} с за тик, как у предметов).
 * Тепло — сумма остаточного тепловыделения. Наружу приповерхностный отсек светит через {@value #COVER_CM} см бетона и
 * грунта покрытия; глубинный — под сотнями метров породы, на поверхности его не видно. Оценка дозы после закрытия —
 * разбавление в реке местного водосбора ({@link RiverService}, средний сток {@link RiverCatchment#MEAN_RUNOFF_MM_DAY}
 * мм/сут); пересчёт — раз в {@value #PEAK_TICKS} тиков.
 */
public final class RepositoryBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    public static final int INTAKE = 0, OVERPACK = 1, PEAK_TICKS = 200;
    public static final double COVER_CM = 200, YEAR_S = 365.25 * 86_400;
    /** Имена нуклидов для щита: индекс в канале — позиция в этом списке. */
    public static final String[] NUCLIDES = names();

    private final Repository repo;
    private final ItemStackHandler slots = new ItemStackHandler(2) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == INTAKE ? WastePackage.isPackage(stack) : deep() && stack.is(PcItems.COPPER_CANISTER.get());
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private long lastTick = -1;
    private Repository.Peak peak = new Repository.Peak(0, 0, "");
    private Repository.Verdict last;
    private double dilutionM3Y;

    private RepositoryBlockEntity(Repository.Type type, BlockPos pos, BlockState state) {
        super(type == Repository.Type.NEAR_SURFACE ? WasteBlockEntities.NEAR_SURFACE_REPOSITORY.get() : WasteBlockEntities.DEEP_REPOSITORY.get(),
                pos, state);
        this.repo = new Repository(type);
    }

    public static RepositoryBlockEntity nearSurface(BlockPos pos, BlockState state) {
        return new RepositoryBlockEntity(Repository.Type.NEAR_SURFACE, pos, state);
    }

    public static RepositoryBlockEntity deep(BlockPos pos, BlockState state) {
        return new RepositoryBlockEntity(Repository.Type.DEEP_GEOLOGICAL, pos, state);
    }

    private static String[] names() {
        var e = dev.denfry.promptcritical.sim.radiation.Emitter.values();
        var l = dev.denfry.promptcritical.sim.waste.LongLivedNuclide.values();
        String[] n = new String[e.length + l.length];
        for (int i = 0; i < e.length; i++) n[i] = e[i].name();
        for (int i = 0; i < l.length; i++) n[e.length + i] = l[i].name();
        return n;
    }

    public boolean deep() {
        return repo.type() == Repository.Type.DEEP_GEOLOGICAL;
    }

    public Repository repository() {
        return repo;
    }

    public ItemStackHandler slots() {
        return slots;
    }

    public Repository.Peak peak() {
        return peak;
    }

    @Nullable
    public Repository.Verdict lastVerdict() {
        return last;
    }

    /** Годовой сток реки водосбора у пункта, м³/год. */
    public double dilutionM3Y() {
        return dilutionM3Y;
    }

    /** Предложить упаковку: при приёме её состав уходит в отсек, медный контейнер (если нужен) — со склада. */
    public Repository.Verdict offer(ItemStack s, long now) {
        WastePackage p = WastePackage.of(s);
        ActivityData a = s.get(ActivityData.ACTIVITY.get());
        if (p == null || a == null) return last = Repository.Verdict.EMPTY;
        catchUp(now);
        WasteInventory inv = WastePackage.inventory(s, now);
        boolean overpack = deep() && p.wasteForm().glass() && slots.getStackInSlot(OVERPACK).is(PcItems.COPPER_CANISTER.get());
        double surface = a.inventory(now).doseRateSvH(0.3);
        Repository.Verdict v = repo.accept(new Repository.Package(p.wasteForm(), inv, p.compliant(), surface, overpack));
        if (v == Repository.Verdict.ACCEPTED && overpack) slots.extractItem(OVERPACK, 1, false);
        last = v;
        setChanged();
        return v;
    }

    /** Сообщение о решении для игрока. */
    public net.minecraft.network.chat.MutableComponent describe(Repository.Verdict v, ItemStack s, long now) {
        WasteInventory inv = WastePackage.inventory(s, now);
        WasteClassification.Result c = WasteClassification.classify(inv, false);
        String cat = WasteText.msg("cat." + c.category().name().toLowerCase(Locale.ROOT)).getString();
        ActivityData a = s.get(ActivityData.ACTIVITY.get());
        double rate = a != null ? a.inventory(now).doseRateSvH(0.3) : 0;
        WastePackage p = WastePackage.of(s);
        return switch (v) {
            case ACCEPTED -> WasteText.verdict(v, WasteText.msg("package", cat, c.rfClass(), DosimeterItem.rate(rate),
                    WasteText.msg(p != null && p.compliant() ? "compliant" : "non_compliant")));
            case WRONG_CLASS -> WasteText.verdict(v, cat, c.rfClass());
            case DOSE_RATE -> WasteText.verdict(v, DosimeterItem.rate(rate));
            case TOO_HOT -> WasteText.verdict(v, Math.round(inv.heatW()));
            default -> WasteText.verdict(v);
        };
    }

    /** Распад инвентаря до тика {@code now}. */
    private void catchUp(long now) {
        if (lastTick >= 0 && now > lastTick) repo.decay((now - lastTick) * ActivityData.GAME_S_PER_TICK);
        lastTick = now;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel s)) return;
        long now = level.getGameTime();
        if (now % 10 == 0) {
            ItemStack in = slots.getStackInSlot(INTAKE);
            if (!in.isEmpty() && (last == null || last == Repository.Verdict.ACCEPTED || now % 100 == 0)) {
                if (offer(in, now) == Repository.Verdict.ACCEPTED) slots.extractItem(INTAKE, 1, false);
            }
        }
        if (now % 20 == 0) {
            catchUp(now);
            if (!deep() && repo.totalBq() > 0) RadiationService.source(s, worldPosition.getCenter(), behindCover(repo.total().emitters().source()));
        }
        if (now % PEAK_TICKS == 0) {
            dilutionM3Y = RiverCatchment.dischargeM3s(RiverService.catchmentAreaKm2(s, worldPosition), RiverCatchment.MEAN_RUNOFF_MM_DAY) * YEAR_S;
            peak = repo.peak(dilutionM3Y);
            setChanged();
        }
    }

    /** Источник за покрытием: бетон и грунт, γ с накоплением, нейтроны — выведением. */
    static PointSource behindCover(PointSource src) {
        double[] p = src.photons().clone();
        for (int i = 0; i < p.length; i++) {
            double mfp = ShieldMaterial.CONCRETE.mu(src.energyMeV()[i], ShieldMaterial.CONCRETE.density) * COVER_CM;
            p[i] *= ShieldMaterial.CONCRETE.buildup(mfp) * Math.exp(-mfp);
        }
        double n = src.neutrons() * Math.exp(-ShieldMaterial.CONCRETE.removal(ShieldMaterial.CONCRETE.density) * COVER_CM);
        return new PointSource(src.energyMeV(), p, n);
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return slots;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return slots;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return WastePackage.isPackage(stack) || deep() && stack.is(PcItems.COPPER_CANISTER.get());
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (!accepts(stack) || level == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (stack.is(PcItems.COPPER_CANISTER.get())) {
            ItemStack left = slots.insertItem(OVERPACK, stack.copy(), false);
            int moved = stack.getCount() - left.getCount();
            if (moved == 0) return ItemInteractionResult.FAIL;
            if (!player.isCreative()) stack.shrink(moved);
            return ItemInteractionResult.SUCCESS;
        }
        long now = level.getGameTime();
        Repository.Verdict v = offer(stack, now);
        player.displayClientMessage(describe(v, stack, now), true);
        if (v != Repository.Verdict.ACCEPTED) return ItemInteractionResult.FAIL;
        level.playSound(null, worldPosition, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 0.7f);
        if (!player.isCreative()) stack.shrink(1);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        return InteractionResult.PASS;
    }

    @Override
    public PanelLayout panelLayout() {
        return deep() ? WastePanels.DEEP : WastePanels.NEAR_SURFACE;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        v[WastePanels.R_PACKAGES] = repo.packages();
        v[WastePanels.R_ACT_GBQ] = (int) Math.min(2e9, Math.round(repo.totalBq() / 1e9));
        v[WastePanels.R_HEAT_W] = (int) Math.min(2e9, Math.round(repo.heatW()));
        v[WastePanels.R_DOSE_LOG] = peak.svPerY() > 0 ? (int) Math.round(Math.log10(peak.svPerY()) * 100) : 0;
        v[WastePanels.R_YEARS_LOG] = peak.years() > 0 ? (int) Math.round(Math.log10(peak.years()) * 100) : 0;
        v[WastePanels.R_NUCLIDE] = Arrays.asList(NUCLIDES).indexOf(peak.nuclide());
        v[WastePanels.R_VERDICT] = last == null ? -1 : last.ordinal();
        v[WastePanels.R_FLAGS] = (peak.svPerY() > 0 ? 1 << WastePanels.RF_RELEASE : 0)
                | (peak.svPerY() > Repository.DOSE_CONSTRAINT_SV_Y ? 1 << WastePanels.RF_OVER : 0)
                | (last != null && last != Repository.Verdict.ACCEPTED ? 1 << WastePanels.RF_REJECT : 0);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("slots", slots.serializeNBT(registries));
        MutableStateTree t = new MutableStateTree();
        repo.save(new TreeStateWriter(t));
        tag.put("repository", NbtStateTrees.toNbt(t));
        tag.putLong("last_tick", lastTick);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("slots")) slots.deserializeNBT(registries, tag.getCompound("slots"));
        if (tag.contains("repository")) repo.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("repository"))));
        lastTick = tag.contains("last_tick") ? tag.getLong("last_tick") : -1;
    }
}
