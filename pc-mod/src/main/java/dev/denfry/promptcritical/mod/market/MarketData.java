package dev.denfry.promptcritical.mod.market;

import dev.denfry.promptcritical.mod.environment.ActivityData;
import dev.denfry.promptcritical.sim.economy.Account;
import dev.denfry.promptcritical.sim.economy.Commodity;
import dev.denfry.promptcritical.sim.economy.MarketParams;
import dev.denfry.promptcritical.sim.economy.NpcMarket;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Биржа сервера — одна на мир, {@code SavedData} {@code promptcritical:market} в верхнем мире (14 §14.8.1): цены,
 * накопленные объёмы и счета в CR. Счёт пока личный (UUID игрока): команд станций 04d в моде ещё нет. Цены шагают
 * лениво при обращении — шагами по игровому часу (интервал расчёта §14.6.5), долгий простой — одним точным шагом.
 */
public final class MarketData extends SavedData {
    private static final String NAME = "promptcritical_market";
    /** Игровой час, тиков. */
    private static final long STEP_TICKS = 1000;
    /** Больше недели простоя — один шаг: OU-шаг точен на любом Δt. */
    private static final long MAX_STEPS = 168;

    private final NpcMarket market;
    private long lastTick = -1;

    private MarketData(long seed) {
        // эпох (маршрут 11) в моде нет: NPC продаёт только товары с minEpoch = 0
        market = new NpcMarket(seed, MarketParams.DEFAULTS, MarketCommodities.all(), team -> 0);
    }

    public static MarketData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        long seed = overworld.getSeed();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(() -> new MarketData(seed), (tag, reg) -> load(seed, tag)), NAME);
    }

    /** Рынок, продвинутый до игрового времени {@code gameTime}. */
    public NpcMarket market(long gameTime) {
        if (lastTick < 0) lastTick = gameTime;
        long steps = (gameTime - lastTick) / STEP_TICKS;
        if (steps > 0) {
            double dt = STEP_TICKS * ActivityData.GAME_S_PER_TICK;
            if (steps > MAX_STEPS) market.step(steps * dt);
            else for (long i = 0; i < steps; i++) market.step(dt);
            lastTick += steps * STEP_TICKS;
            setDirty();
        }
        return market;
    }

    private static MarketData load(long seed, CompoundTag tag) {
        MarketData d = new MarketData(seed);
        d.lastTick = tag.getLong("last_tick");
        d.market.restoreClock(tag.getDouble("t"), tag.getLong("ticket"));
        CompoundTag goods = tag.getCompound("goods");
        for (Commodity c : MarketCommodities.all()) {
            if (!goods.contains(c.id())) continue;
            CompoundTag g = goods.getCompound(c.id());
            d.market.restore(c.id(), new NpcMarket.CommodityState(g.getDouble("x"), g.getDouble("v"), g.getDouble("vb"),
                    g.getLong("n")));
        }
        for (Tag t : tag.getList("accounts", Tag.TAG_COMPOUND)) {
            CompoundTag a = (CompoundTag) t;
            d.market.putAccount(new Account(a.getUUID("team"), a.getLong("balance"), a.getLong("reserved"),
                    a.getLong("today")));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("last_tick", lastTick);
        tag.putDouble("t", market.tPhysS());
        tag.putLong("ticket", market.nextTicket());
        CompoundTag goods = new CompoundTag();
        for (Commodity c : MarketCommodities.all()) {
            NpcMarket.CommodityState s = market.state(c.id());
            CompoundTag g = new CompoundTag();
            g.putDouble("x", s.x());
            g.putDouble("v", s.salesVolume());
            g.putDouble("vb", s.buyVolume());
            g.putLong("n", s.stepIndex());
            goods.put(c.id(), g);
        }
        tag.put("goods", goods);
        ListTag accounts = new ListTag();
        for (Account a : market.accounts()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("team", a.team());
            t.putLong("balance", a.balanceCents());
            t.putLong("reserved", a.reservedCents());
            t.putLong("today", a.salesTodayCents());
            accounts.add(t);
        }
        tag.put("accounts", accounts);
        return tag;
    }

    public Account account(UUID team) {
        return market.account(team);
    }
}
