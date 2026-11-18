package dev.denfry.promptcritical.mod.scada.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.denfry.promptcritical.mod.market.MarketData;
import dev.denfry.promptcritical.mod.market.MarketTerminalBlockEntity;
import dev.denfry.promptcritical.sim.economy.Account;
import dev.denfry.promptcritical.sim.economy.Commodity;
import dev.denfry.promptcritical.sim.economy.NpcMarket;
import dev.denfry.promptcritical.sim.economy.Quote;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Периферия {@code pc_market} биржевого терминала (14 §14.10), функции только чтения: список товаров, котировка,
 * оценка выручки R(ΔV) без исполнения, счёт владельца. Цены в CR (не в центах).
 *
 * <pre>
 * local m = peripheral.find("pc_market")
 * local q = m.getQuote("steel_ingot")      -- q.bid, q.ask, q.mid, q.volume, q.depth
 * local e = m.estimateSale("steel_ingot", 64) -- e.revenue, e.avgPrice, e.marginalPrice
 * print(m.getAccount().balance)
 * </pre>
 */
public final class MarketPeripheral implements IPeripheral {
    private final MarketTerminalBlockEntity terminal;

    public MarketPeripheral(MarketTerminalBlockEntity terminal) {
        this.terminal = terminal;
    }

    @Override
    public String getType() {
        return "pc_market";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof MarketPeripheral m && m.terminal == terminal;
    }

    private NpcMarket market() throws LuaException {
        if (!(terminal.getLevel() instanceof ServerLevel sl)) throw new LuaException("terminal is not loaded");
        return MarketData.get(sl.getServer()).market(sl.getGameTime());
    }

    private static double cr(long cents) {
        return cents / 100.0;
    }

    private static void requireKnown(NpcMarket m, String id) throws LuaException {
        if (!m.commodities().containsKey(id)) throw new LuaException("unknown commodity " + id);
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> listCommodities() throws LuaException {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Commodity c : market().commodities().values()) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("id", c.id());
            e.put("unit", c.unit());
            e.put("basePrice", cr(c.basePriceCents()));
            e.put("depth", c.depth());
            e.put("elasticity", c.elasticity());
            out.add(e);
        }
        return out;
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getQuote(String id) throws LuaException {
        NpcMarket m = market();
        requireKnown(m, id);
        Quote q = m.quote(id);
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("bid", cr(q.bidCents()));
        e.put("ask", cr(q.askCents()));
        e.put("mid", cr(q.midCents()));
        e.put("volume", q.salesVolume());
        e.put("depth", q.depth());
        e.put("sigma", q.sigma());
        e.put("t", q.tPhysS());
        return e;
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> estimateSale(String id, double qty) throws LuaException {
        if (!(qty > 0) || !Double.isFinite(qty)) throw new LuaException("qty must be > 0");
        NpcMarket m = market();
        requireKnown(m, id);
        long revenue = m.estimateSaleCents(id, qty);
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("revenue", cr(revenue));
        e.put("avgPrice", revenue / qty / 100.0);
        e.put("marginalPrice", cr(m.quote(id).bidCents()));
        return e;
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getAccount() throws LuaException {
        NpcMarket m = market();
        UUID owner = terminal.owner();
        Map<String, Object> e = new LinkedHashMap<>();
        if (owner == null) {
            e.put("owner", false);
            return e;
        }
        Account a = m.account(owner);
        e.put("owner", true);
        e.put("balance", cr(a.balanceCents()));
        e.put("salesToday", cr(a.salesTodayCents()));
        return e;
    }
}
