package dev.denfry.promptcritical.sim.economy;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * NPC-рынок сервера (14 §14.6): цена каждого товара — тренд плюс OU-шум с зерном мира (§14.6.2), продажа и покупка по
 * эластичному спросу (§14.6.3) со спредом и зеркальным влиянием покупок, скоропортящиеся партии (§14.6.4), счета команд
 * в CR и дневной лимит выручки (§14.8.1–14.8.2). Состояние полностью сериализуемо: время, номер тикета, по товару
 * {@link CommodityState}, счета.
 */
public final class NpcMarket implements Market {
    /** Состояние товара: отклонение логарифма цены x, накопленные объёмы продаж V и покупок V_b, номер шага шума. */
    public record CommodityState(double x, double salesVolume, double buyVolume, long stepIndex) {}

    private final long seed;
    private final MarketParams params;
    private final ToIntFunction<UUID> epochOf;
    private final Map<String, Commodity> commodities = new LinkedHashMap<>();
    private final Map<String, CommodityState> states = new LinkedHashMap<>();
    private final Map<UUID, Account> accounts = new LinkedHashMap<>();
    private double tPhysS;
    private long nextTicket = 1;

    /** @param epochOf достигнутая командой эпоха (маршрут 11) — для {@code minEpoch} */
    public NpcMarket(long seed, MarketParams params, Collection<Commodity> list, ToIntFunction<UUID> epochOf) {
        this.seed = seed;
        this.params = Objects.requireNonNull(params);
        this.epochOf = Objects.requireNonNull(epochOf);
        for (Commodity c : list) {
            commodities.put(c.id(), c);
            states.put(c.id(), new CommodityState(0, 0, 0, 0));
        }
    }

    public Map<String, Commodity> commodities() {
        return Map.copyOf(commodities);
    }

    public double tPhysS() {
        return tPhysS;
    }

    public long nextTicket() {
        return nextTicket;
    }

    /** Восстановление часов после загрузки мира. */
    public void restoreClock(double tPhysS, long nextTicket) {
        this.tPhysS = tPhysS;
        this.nextTicket = Math.max(1, nextTicket);
    }

    public CommodityState state(String id) {
        return states.get(id);
    }

    public void restore(String id, CommodityState s) {
        if (commodities.containsKey(id)) states.put(id, s);
    }

    public Collection<Account> accounts() {
        return accounts.values();
    }

    public Account account(UUID team) {
        return accounts.getOrDefault(team, new Account(team, 0, 0, 0));
    }

    public void putAccount(Account a) {
        accounts.put(a.team(), a);
    }

    /** Зачисление (cents > 0) или списание (cents < 0) вне торговли: неустойки, платы, компенсации. */
    public boolean transfer(UUID team, long cents) {
        Account a = account(team);
        if (a.balanceCents() + cents < 0) return false;
        accounts.put(team, new Account(team, a.balanceCents() + cents, a.reservedCents(), a.salesTodayCents()));
        return true;
    }

    /** Текущая цена p_t, центы за единицу (тренд × e^x). */
    public double price(String id) {
        Commodity c = require(id);
        return PriceProcess.trendPrice(c.basePriceCents(), c.trend(), tPhysS, params.quotaPeriodS())
                * Math.exp(states.get(id).x());
    }

    @Override
    public Quote quote(String id) {
        Commodity c = require(id);
        CommodityState s = states.get(id);
        double pt = price(id);
        long bid = (long) Math.floor(ElasticDemand.marginalSellPrice(pt, c.depth(), c.elasticity(), s.salesVolume()));
        long ask = (long) Math.ceil(ElasticDemand.marginalBuyPrice(pt, c.depth(), c.elasticity(), s.buyVolume())
                * (1 + params.spread()));
        return new Quote(bid, ask, Math.round(pt), c.trend(), c.sigma() * params.sigmaMultiplier(), s.salesVolume(),
                c.depth(), tPhysS);
    }

    /** R(ΔV) без исполнения; {@code qty} — объём продажи (знак не важен). */
    @Override
    public long estimateSaleCents(String id, double qty) {
        return estimateSaleCents(id, qty, 0);
    }

    /** То же для партии возраста {@code agePhysS} (скоропортящиеся товары, §14.6.4). */
    public long estimateSaleCents(String id, double qty, double agePhysS) {
        Commodity c = commodities.get(id);
        if (c == null || !c.perishable().accepts(agePhysS)) return 0;
        double f = c.perishable().valueFraction(agePhysS);
        return (long) Math.floor(f * ElasticDemand.saleRevenue(price(id), c.depth(), c.elasticity(),
                states.get(id).salesVolume(), Math.abs(qty)));
    }

    @Override
    public Fill execute(UUID team, Order order) {
        return execute(team, order, 0);
    }

    /** Исполнение с возрастом партии для продажи скоропортящихся товаров. */
    public Fill execute(UUID team, Order order, double agePhysS) {
        Commodity c = commodities.get(order.commodityId());
        if (c == null || order.qty() == 0 || !Double.isFinite(order.qty())) return reject(FillStatus.REJECTED_UNKNOWN);
        if (c.restricted()) return reject(FillStatus.REJECTED_RESTRICTED);
        return order.qty() < 0 ? sell(team, c, -order.qty(), order.limitAvgCents(), agePhysS)
                : buy(team, c, order.qty(), order.limitAvgCents());
    }

    private Fill sell(UUID team, Commodity c, double qty, long limitAvg, double age) {
        if (!c.npcBuy()) return reject(FillStatus.REJECTED_RESTRICTED);
        if (!c.perishable().accepts(age)) return reject(FillStatus.REJECTED_PERISHED);
        CommodityState s = states.get(c.id());
        Account a = account(team);
        double pt = price(c.id()) * c.perishable().valueFraction(age);
        long room = params.maxSalesPerDayCents() - a.salesTodayCents();
        if (room <= 0) return reject(FillStatus.REJECTED_CAP);
        double dv = qty;
        FillStatus status = FillStatus.FILLED;
        if (ElasticDemand.saleRevenue(pt, c.depth(), c.elasticity(), s.salesVolume(), dv) > room) {
            dv = Math.min(dv, ElasticDemand.volumeForRevenue(pt, c.depth(), c.elasticity(), s.salesVolume(), room));
            if (qty == Math.rint(qty)) dv = Math.floor(dv); // штучный товар делится только на целые единицы
            if (dv <= 0) return reject(FillStatus.REJECTED_CAP);
            status = FillStatus.PARTIAL;
        }
        long revenue = (long) Math.floor(ElasticDemand.saleRevenue(pt, c.depth(), c.elasticity(), s.salesVolume(), dv));
        long avg = Math.round(revenue / dv);
        if (limitAvg > 0 && avg < limitAvg) return reject(FillStatus.REJECTED_LIMIT);
        states.put(c.id(), new CommodityState(s.x(), s.salesVolume() + dv, s.buyVolume(), s.stepIndex()));
        accounts.put(team, new Account(team, a.balanceCents() + revenue, a.reservedCents(),
                a.salesTodayCents() + revenue));
        return new Fill(nextTicket++, -dv, avg, revenue, status);
    }

    private Fill buy(UUID team, Commodity c, double qty, long limitAvg) {
        if (!c.npcSell()) return reject(FillStatus.REJECTED_RESTRICTED);
        if (epochOf.applyAsInt(team) < c.minEpoch()) return reject(FillStatus.REJECTED_EPOCH);
        CommodityState s = states.get(c.id());
        double cost = ElasticDemand.purchaseCost(price(c.id()), c.depth(), c.elasticity(), s.buyVolume(), qty)
                * (1 + params.spread());
        long total = (long) Math.ceil(cost);
        long avg = Math.round(total / qty);
        if (limitAvg > 0 && avg > limitAvg) return reject(FillStatus.REJECTED_LIMIT);
        Account a = account(team);
        if (a.balanceCents() - a.reservedCents() < total) return reject(FillStatus.REJECTED_FUNDS);
        states.put(c.id(), new CommodityState(s.x(), s.salesVolume(), s.buyVolume() + qty, s.stepIndex()));
        accounts.put(team, new Account(team, a.balanceCents() - total, a.reservedCents(), a.salesTodayCents()));
        return new Fill(nextTicket++, qty, avg, total, FillStatus.FILLED);
    }

    private static Fill reject(FillStatus status) {
        return new Fill(0, 0, 0, 0, status);
    }

    /** OU-шаг цен, рассасывание V и V_b, сброс дневной выручки на границе суток. */
    @Override
    public void step(double dtPhysS) {
        if (!(dtPhysS > 0)) return;
        for (Map.Entry<String, Commodity> e : commodities.entrySet()) {
            Commodity c = e.getValue();
            CommodityState s = states.get(e.getKey());
            double xi = PriceProcess.gaussian(seed, c.id(), s.stepIndex());
            double x = PriceProcess.step(s.x(), c.theta(), c.sigma() * params.sigmaMultiplier(), dtPhysS, xi);
            states.put(e.getKey(), new CommodityState(x,
                    ElasticDemand.recover(s.salesVolume(), dtPhysS, params.recoveryS()),
                    ElasticDemand.recover(s.buyVolume(), dtPhysS, params.recoveryS()), s.stepIndex() + 1));
        }
        long dayBefore = (long) Math.floor(tPhysS / Commodity.DAY_S);
        tPhysS += dtPhysS;
        if ((long) Math.floor(tPhysS / Commodity.DAY_S) != dayBefore) {
            accounts.replaceAll((k, a) -> new Account(k, a.balanceCents(), a.reservedCents(), 0));
        }
    }

    private Commodity require(String id) {
        Commodity c = commodities.get(id);
        if (c == null) throw new IllegalArgumentException("unknown commodity " + id);
        return c;
    }
}
