package dev.denfry.promptcritical.mod.market;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import java.util.List;
import net.minecraft.network.chat.Component;

/** Щит биржевого терминала (14 §14.6): счёт, выручка за сутки против лимита, цена спроса на товар в слоте. */
public final class MarketPanels {
    public static final int T_BALANCE_CR = 0, T_TODAY_CR = 1, T_BID_CENTS = 2, T_LAST_CENTS = 3, T_FLAGS = 4, T_CHANNELS = 5;
    public static final int TF_OWNED = 0, TF_GOODS = 1, TF_CAP = 2;

    private static final Tr OWNED = PanelText.tr("market.owned", "Account", "Счёт"),
            GOODS = PanelText.tr("market.goods", "Goods", "Товар"),
            CAP = PanelText.tr("market.cap", "Day limit", "Лимит суток"),
            SLOT = PanelText.tr("market.slot", "Goods to sell: plant products from the exchange list",
                    "Товар на продажу: продукция заводов из списка биржи"),
            BALANCE = PanelText.tr("market.balance", "Balance %s CR · sold today %s CR",
                    "Счёт %s CR · продано за сутки %s CR"),
            QUOTE = PanelText.tr("market.quote", "Bid %s CR per unit · last sale %s CR",
                    "Спрос %s CR за ед. · последняя продажа %s CR");

    public static final PanelLayout TERMINAL = PanelLayout.builder("market_terminal", 220, 100, T_CHANNELS)
            .lamp(8, 24, T_FLAGS, TF_OWNED, LampColor.GREEN, OWNED.get())
            .lamp(64, 24, T_FLAGS, TF_GOODS, LampColor.AMBER, GOODS.get())
            .lamp(120, 24, T_FLAGS, TF_CAP, LampColor.RED, CAP.get())
            .slot(180, 24, 0, MarketTerminalBlockEntity::isTradeable, SLOT.get())
            .readout(8, 52, 204, MarketPanels::balance, null)
            .readout(8, 70, 204, MarketPanels::quote, null)
            .build();

    private MarketPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(TERMINAL);
    }

    private static Component balance(PanelValues v) {
        return BALANCE.get(v.raw(T_BALANCE_CR), v.raw(T_TODAY_CR));
    }

    private static Component quote(PanelValues v) {
        return QUOTE.get(v.num(T_BID_CENTS, 100, 2), v.num(T_LAST_CENTS, 100, 2));
    }
}
