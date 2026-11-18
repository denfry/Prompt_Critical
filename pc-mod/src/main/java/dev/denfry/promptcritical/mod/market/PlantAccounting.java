package dev.denfry.promptcritical.mod.market;

import dev.denfry.promptcritical.mod.environment.AirQualityService;
import dev.denfry.promptcritical.sim.economy.ContractState;
import dev.denfry.promptcritical.sim.economy.NpcMarket;
import dev.denfry.promptcritical.sim.economy.PenaltyClause;
import dev.denfry.promptcritical.sim.economy.PollutionFee;
import dev.denfry.promptcritical.sim.economy.QuotaAccount;
import dev.denfry.promptcritical.sim.economy.QuotaLedger;
import dev.denfry.promptcritical.sim.economy.SupplyContract;
import dev.denfry.promptcritical.sim.environment.EmissionLedger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Хозяйственный учёт завода (14 §14.6.6, §14.7): квоты CO₂, экологические платы и договоры поставки. Команд и эпох в
 * моде нет, поэтому учёт ведётся на одного оператора — владельца биржевого терминала, который первым его занял.
 *
 * <ul>
 * <li><b>Квоты.</b> Период — 7 игровых суток. Первый полный период — базовый: его выброс CO₂-экв. становится A_base,
 * штрафа нет. Дальше выделяется A_n = A_base(1 − r)^n, r = 0,05; в конце периода сдаются квоты на выброс журнала
 * (с множителем метода учёта), дефицит — штраф 100 CR/т и долг на следующий период, излишек переходит
 * ({@link QuotaLedger}).</li>
 * <li><b>Платы</b> за SO₂, NOx и PM периода по ставкам §14.7.3 ({@link PollutionFee}).</li>
 * <li><b>Договоры поставки</b> с NPC-покупателем: товар из слота терминала идёт сначала в договор (по цене договора),
 * остаток — на рынок; в конце периода оплата поставленного, неустойка за недопоставку, дефолт после трёх
 * периодов подряд ниже 50 % ({@link SupplyContract}).</li>
 * </ul>
 * Списания, на которые не хватает денег, копятся долгом оператора и списываются с первой выручки.
 */
public final class PlantAccounting extends SavedData {
    private static final String NAME = "promptcritical_accounting";
    /** Период учёта — 7 игровых суток (14 §14.7.1). */
    public static final long PERIOD_TICKS = 7 * 24000L;
    /** Убывание выделения r = 0,05 и штраф 100 CR/т (14 §14.7.1) [BALANCE]. */
    public static final double DECLINE = 0.05;
    public static final long PENALTY_CENTS_PER_T = 100_00;
    /** Договор: поставка за период, единиц; число периодов. */
    public static final double CONTRACT_QTY = 64;
    public static final int CONTRACT_PERIODS = 4;
    public static final UUID NPC_BUYER = new UUID(0x5043L, 0x4e5043L);

    @Nullable
    private UUID operator;
    private long periodStart = -1;
    private double baseT = Double.NaN;
    @Nullable
    private QuotaAccount quota;
    private long debtCents, lastFineCents, lastFeesCents, lastContractCents;
    private double lastCo2eT;
    private long nextContractId = 1;
    private final List<Contract> contracts = new ArrayList<>();

    /** Договор с поставленным за период и серией недопоставок. */
    public static final class Contract {
        SupplyContract terms;
        double delivered;
        int shortStreak;

        Contract(SupplyContract terms) {
            this.terms = terms;
        }

        public SupplyContract terms() {
            return terms;
        }

        public double delivered() {
            return delivered;
        }
    }

    public static PlantAccounting get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PlantAccounting::new, (tag, reg) -> load(tag)), NAME);
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel s && s == s.getServer().overworld() && s.getGameTime() % 200 == 0)
                get(s.getServer()).tick(s.getServer());
        });
    }

    @Nullable
    public UUID operator() {
        return operator;
    }

    /** Оператор завода — первый владелец биржевого терминала. */
    public void claimOperator(UUID player) {
        if (operator == null) {
            operator = player;
            setDirty();
        }
    }

    @Nullable
    public QuotaAccount quota() {
        return quota;
    }

    public double baseT() {
        return baseT;
    }

    public long debtCents() {
        return debtCents;
    }

    public List<Contract> contracts() {
        return contracts;
    }

    // ------------------------------------------------------------------ договоры

    /** Подписать договор на поставку товара {@code commodityId} по цене {@code priceCents} за единицу; депозит 20 %. */
    @Nullable
    public SupplyContract sign(NpcMarket market, UUID seller, String commodityId, long priceCents) {
        for (Contract c : contracts)
            if (c.terms.seller().equals(seller) && c.terms.commodityId().equals(commodityId) && c.terms.state() == ContractState.ACTIVE)
                return null;
        SupplyContract t = SupplyContract.propose(nextContractId++, seller, NPC_BUYER, commodityId, CONTRACT_QTY, Math.max(1, priceCents),
                CONTRACT_PERIODS);
        if (!market.transfer(seller, -t.depositCents())) return null;
        t = t.accept();
        contracts.add(new Contract(t));
        setDirty();
        return t;
    }

    /**
     * Расторгнуть договоры продавца по согласию (14 §14.6.6): ACTIVE → CLOSED, депозит возвращается; поставленное
     * в этом периоде оплачивается.
     *
     * @return расторгнуто договоров
     */
    public int cancel(NpcMarket market, UUID seller) {
        int n = 0;
        for (Contract c : contracts) {
            if (!c.terms.seller().equals(seller) || c.terms.state() != ContractState.ACTIVE) continue;
            market.transfer(seller, Math.round(Math.min(c.delivered, c.terms.qtyPerPeriod()) * c.terms.priceCents()) + c.terms.depositCents());
            c.terms = c.terms.close();
            n++;
        }
        contracts.removeIf(c -> c.terms.state() == ContractState.CLOSED);
        if (n > 0) setDirty();
        return n;
    }

    /** Сколько единиц товара из партии уйдёт в договоры продавца в этом периоде (и записать поставку). */
    public int deliver(UUID seller, String commodityId, int qty) {
        int taken = 0;
        for (Contract c : contracts) {
            if (c.terms.state() != ContractState.ACTIVE || !c.terms.seller().equals(seller) || !c.terms.commodityId().equals(commodityId))
                continue;
            int room = (int) Math.floor(c.terms.qtyPerPeriod() - c.delivered);
            int n = Math.max(0, Math.min(room, qty - taken));
            c.delivered += n;
            taken += n;
        }
        if (taken > 0) setDirty();
        return taken;
    }

    // ------------------------------------------------------------------ период

    void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (periodStart < 0) {
            periodStart = now;
            setDirty();
            return;
        }
        if (now - periodStart < PERIOD_TICKS) return;
        periodStart = now;
        settle(server);
        setDirty();
    }

    /** Закрытие периода: квоты, платы, договоры; отчёт — игрокам. */
    public void settle(MinecraftServer server) {
        NpcMarket market = MarketData.get(server).market(server.overworld().getGameTime());
        double co2e = 0, so2 = 0, nox = 0, pm = 0;
        for (ServerLevel l : server.getAllLevels()) {
            EmissionLedger led = AirQualityService.ledger(l);
            co2e += led.reportedCo2eT();
            so2 += led.reportedKg(EmissionLedger.Pollutant.SO2);
            nox += led.reportedKg(EmissionLedger.Pollutant.NOX);
            pm += led.reportedKg(EmissionLedger.Pollutant.PM);
            led.rollPeriod();
        }
        lastCo2eT = co2e;
        lastFineCents = 0;
        lastFeesCents = PollutionFee.SO2.feeCents(so2, 0, 1) + PollutionFee.NOX.feeCents(nox, 0, 1) + PollutionFee.PM.feeCents(pm, 0, 1);
        if (operator != null) {
            if (quota == null) {
                // базовый период: выброс — основа выделения, штрафа нет
                baseT = co2e;
                double a1 = QuotaLedger.allocation(baseT, DECLINE, 1);
                quota = new QuotaAccount(operator, a1, a1, 0, 1);
            } else {
                QuotaLedger.Settlement s = QuotaLedger.settle(quota, co2e, 1, PENALTY_CENTS_PER_T,
                        QuotaLedger.allocation(baseT, DECLINE, quota.period() + 1));
                lastFineCents = s.fineCents();
                quota = s.next();
            }
            charge(market, lastFineCents + lastFeesCents);
        }
        // договоры
        lastContractCents = 0;
        for (Contract c : contracts) {
            if (c.terms.state() != ContractState.ACTIVE) continue;
            SupplyContract.Settlement s = c.terms.settlePeriod(c.delivered, c.shortStreak, 0);
            market.transfer(c.terms.seller(), s.paymentCents());
            charge(market, s.penaltyCents());
            lastContractCents += s.paymentCents() - s.penaltyCents();
            c.shortStreak = s.shortStreak();
            c.terms = c.terms.withState(s.state());
            c.delivered = 0;
        }
        contracts.removeIf(c -> c.terms.state() != ContractState.ACTIVE && c.terms.state() != ContractState.SUSPENDED);
        MarketData.get(server).setDirty();
        Component report = MarketText.msg("period_report", String.format(java.util.Locale.ROOT, "%.1f", co2e),
                quota == null ? "—" : String.format(java.util.Locale.ROOT, "%.1f", quota.heldT()), MarketText.cr(lastFineCents),
                MarketText.cr(lastFeesCents), MarketText.cr(lastContractCents)).withStyle(ChatFormatting.GOLD);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) p.displayClientMessage(report, false);
    }

    /** Списать с оператора; чего не хватает — в долг. */
    private void charge(NpcMarket market, long cents) {
        if (operator == null || cents <= 0) return;
        long bal = market.account(operator).balanceCents();
        long now = Math.min(bal, cents);
        if (now > 0) market.transfer(operator, -now);
        debtCents += cents - now;
    }

    /** Погасить долг из выручки. */
    public void repay(NpcMarket market) {
        if (operator == null || debtCents <= 0) return;
        long bal = market.account(operator).balanceCents();
        long now = Math.min(bal, debtCents);
        if (now > 0 && market.transfer(operator, -now)) {
            debtCents -= now;
            setDirty();
        }
    }

    // ------------------------------------------------------------------ сохранение

    private static PlantAccounting load(CompoundTag t) {
        PlantAccounting a = new PlantAccounting();
        if (t.hasUUID("operator")) a.operator = t.getUUID("operator");
        a.periodStart = t.getLong("period_start");
        a.baseT = t.contains("base_t") ? t.getDouble("base_t") : Double.NaN;
        if (t.contains("quota") && a.operator != null) {
            CompoundTag q = t.getCompound("quota");
            a.quota = new QuotaAccount(a.operator, q.getDouble("allocated"), q.getDouble("held"), q.getDouble("debt"), q.getInt("period"));
        }
        a.debtCents = t.getLong("debt_cents");
        a.lastFineCents = t.getLong("last_fine");
        a.lastFeesCents = t.getLong("last_fees");
        a.lastContractCents = t.getLong("last_contract");
        a.lastCo2eT = t.getDouble("last_co2e");
        a.nextContractId = Math.max(1, t.getLong("next_contract"));
        for (Tag e : t.getList("contracts", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) e;
            SupplyContract terms = new SupplyContract(c.getLong("id"), c.getUUID("seller"), NPC_BUYER, c.getString("commodity"),
                    c.getDouble("qty"), c.getLong("price"), c.getInt("periods"), c.getLong("deposit"),
                    PenaltyClause.standard(c.getLong("deposit")), ContractState.valueOf(c.getString("state")));
            Contract k = new Contract(terms);
            k.delivered = c.getDouble("delivered");
            k.shortStreak = c.getInt("streak");
            a.contracts.add(k);
        }
        return a;
    }

    @Override
    public CompoundTag save(CompoundTag t, HolderLookup.Provider registries) {
        if (operator != null) t.putUUID("operator", operator);
        t.putLong("period_start", periodStart);
        if (!Double.isNaN(baseT)) t.putDouble("base_t", baseT);
        if (quota != null) {
            CompoundTag q = new CompoundTag();
            q.putDouble("allocated", quota.allocatedT());
            q.putDouble("held", quota.heldT());
            q.putDouble("debt", quota.carriedDebtT());
            q.putInt("period", quota.period());
            t.put("quota", q);
        }
        t.putLong("debt_cents", debtCents);
        t.putLong("last_fine", lastFineCents);
        t.putLong("last_fees", lastFeesCents);
        t.putLong("last_contract", lastContractCents);
        t.putDouble("last_co2e", lastCo2eT);
        t.putLong("next_contract", nextContractId);
        ListTag list = new ListTag();
        for (Contract k : contracts) {
            CompoundTag c = new CompoundTag();
            SupplyContract s = k.terms;
            c.putLong("id", s.id());
            c.putUUID("seller", s.seller());
            c.putString("commodity", s.commodityId());
            c.putDouble("qty", s.qtyPerPeriod());
            c.putLong("price", s.priceCents());
            c.putInt("periods", s.periods());
            c.putLong("deposit", s.depositCents());
            c.putString("state", s.state().name());
            c.putDouble("delivered", k.delivered);
            c.putInt("streak", k.shortStreak);
            list.add(c);
        }
        t.put("contracts", list);
        return t;
    }
}
