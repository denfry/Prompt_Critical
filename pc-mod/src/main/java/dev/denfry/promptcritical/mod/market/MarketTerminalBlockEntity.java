package dev.denfry.promptcritical.mod.market;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.ironworks.IronworksBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.economy.Account;
import dev.denfry.promptcritical.sim.economy.Commodity;
import dev.denfry.promptcritical.sim.economy.Fill;
import dev.denfry.promptcritical.sim.economy.FillStatus;
import dev.denfry.promptcritical.sim.economy.NpcMarket;
import dev.denfry.promptcritical.sim.economy.Order;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
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
 * Биржевой терминал {@code market_terminal} (14 §14.6, §14.10): товар из белого списка, положенный в слот (рукой,
 * воронкой или поездом Create), продаётся NPC-рынку по эластичному спросу §14.6.3, выручка в CR зачисляется на счёт
 * владельца терминала — первого игрока, открывшего его щит. Дневной лимит выручки (§14.8.2) останавливает продажу,
 * остаток партии ждёт в слоте следующих суток.
 */
public final class MarketTerminalBlockEntity extends IronworksBlockEntity implements PanelHost {
    /** Терминал и модем, Вт. Малая нагрузка 0,4 кВ: розетка, щит или ИБП рядом. */
    public static final double POWER_W = 300;
    public static final int PERIOD = 20;

    private final ItemStackHandler goods = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isTradeable(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    @Nullable
    private UUID owner;
    private long lastSaleCents;
    private FillStatus lastStatus = FillStatus.FILLED;
    /** Снимок для щита, обновляется в серверном тике. */
    private long balanceCents, todayCents, bidCents;

    public MarketTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(MarketBlockEntities.MARKET_TERMINAL.get(), pos, state);
    }

    public static boolean isTradeable(ItemStack s) {
        return MarketCommodities.of(s) != null;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
        if (!(level instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        if (Math.floorMod(now + worldPosition.hashCode(), PERIOD) != 0 || !dev.denfry.promptcritical.mod.grid.MachinePower.powered(sl, worldPosition, POWER_W)) return;
        MarketData data = MarketData.get(sl.getServer());
        NpcMarket market = data.market(now);
        ItemStack st = goods.getStackInSlot(0);
        Commodity c = MarketCommodities.of(st);
        bidCents = c != null ? market.quote(c.id()).bidCents() : 0;
        PlantAccounting acct = PlantAccounting.get(sl.getServer());
        if (owner != null && c != null) {
            // сначала — договор поставки (оплата в конце недели по цене договора), остаток — на рынок
            int toContract = acct.deliver(owner, c.id(), st.getCount());
            if (toContract > 0) {
                goods.extractItem(0, toContract, false);
                st = goods.getStackInSlot(0);
                setChanged();
            }
        }
        if (owner != null && c != null && !st.isEmpty()) {
            Fill f = market.execute(owner, new Order(c.id(), -st.getCount(), 0));
            lastStatus = f.status();
            if (f.executed()) {
                int sold = (int) Math.round(-f.qty());
                goods.extractItem(0, sold, false);
                lastSaleCents = f.totalCents();
                data.setDirty();
                setChanged();
            }
        }
        if (owner != null) {
            acct.repay(market);
            Account a = market.account(owner);
            balanceCents = a.balanceCents();
            todayCents = a.salesTodayCents();
        }
    }

    // --- руки ---

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return goods;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return isTradeable(stack);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (!isTradeable(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        claim(player);
        ItemStack rest = goods.insertItem(0, stack.copy(), false);
        if (rest.getCount() == stack.getCount()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        stack.setCount(rest.getCount());
        return ItemInteractionResult.SUCCESS;
    }

    /** Shift + ПКМ: показать счёт. */
    @Override
    public InteractionResult use(Player player) {
        claim(player);
        if (!player.level().isClientSide) {
            player.displayClientMessage(MarketText.msg("balance", MarketText.cr(balanceCents), MarketText.cr(todayCents)), true);
        }
        return InteractionResult.SUCCESS;
    }

    private void claim(Player player) {
        if (owner == null && !player.level().isClientSide) {
            owner = player.getUUID();
            setChanged();
        }
        if (owner != null && player.level() instanceof ServerLevel sl) PlantAccounting.get(sl.getServer()).claimOperator(owner);
    }

    // --- щит ---

    @Override
    public boolean panelValid(Player player) {
        boolean ok = super.panelValid(player);
        if (ok) claim(player);
        return ok;
    }

    @Override
    public PanelLayout panelLayout() {
        return MarketPanels.TERMINAL;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return goods;
    }

    @Override
    public void readPanel(int[] out) {
        out[MarketPanels.T_BALANCE_CR] = clampCr(balanceCents);
        out[MarketPanels.T_TODAY_CR] = clampCr(todayCents);
        out[MarketPanels.T_BID_CENTS] = (int) Math.min(Integer.MAX_VALUE, bidCents);
        out[MarketPanels.T_LAST_CENTS] = (int) Math.min(Integer.MAX_VALUE, lastSaleCents);
        int f = 0;
        if (owner != null) f |= 1 << MarketPanels.TF_OWNED;
        if (!goods.getStackInSlot(0).isEmpty()) f |= 1 << MarketPanels.TF_GOODS;
        if (lastStatus == FillStatus.REJECTED_CAP || lastStatus == FillStatus.PARTIAL) f |= 1 << MarketPanels.TF_CAP;
        out[MarketPanels.T_FLAGS] = f;
        if (level instanceof ServerLevel sl) {
            PlantAccounting acct = PlantAccounting.get(sl.getServer());
            out[MarketPanels.T_QUOTA_T] = acct.quota() == null ? 0 : (int) Math.round(acct.quota().heldT());
            out[MarketPanels.T_DEBT_CR] = clampCr(acct.debtCents());
            for (PlantAccounting.Contract k : acct.contracts())
                if (owner != null && k.terms().seller().equals(owner)) {
                    out[MarketPanels.T_CONTRACT_DONE] = (int) k.delivered();
                    out[MarketPanels.T_CONTRACT_QTY] = (int) k.terms().qtyPerPeriod();
                }
        }
    }

    @Override
    public void onPanelButton(net.minecraft.server.level.ServerPlayer player, int id) {
        if (id != MarketPanels.B_CONTRACT || !(level instanceof ServerLevel sl) || owner == null) return;
        Commodity c = MarketCommodities.of(goods.getStackInSlot(0));
        MarketData data = MarketData.get(sl.getServer());
        NpcMarket market = data.market(sl.getGameTime());
        var t = c == null ? null : PlantAccounting.get(sl.getServer()).sign(market, owner, c.id(), market.quote(c.id()).bidCents());
        player.displayClientMessage(t == null ? MarketText.msg("contract_refused").withStyle(net.minecraft.ChatFormatting.RED)
                : MarketText.msg("contract_signed", (int) t.qtyPerPeriod(), c.id(), MarketText.cr(t.priceCents()), MarketText.cr(t.depositCents())), false);
        data.setDirty();
    }

    private static int clampCr(long cents) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, cents / 100));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("goods", goods.serializeNBT(registries));
        if (owner != null) tag.putUUID("owner", owner);
        tag.putLong("last_sale", lastSaleCents);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("goods")) goods.deserializeNBT(registries, tag.getCompound("goods"));
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        lastSaleCents = tag.getLong("last_sale");
    }
}
