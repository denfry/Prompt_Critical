A plant makes more than it eats. Sell the surplus through the [exchange terminal](item:promptcritical:market_terminal): put goods into its slot by hand, hopper or Create train, and once a second the terminal sells whatever is in the slot to the NPC market. The credits (CR) go to the terminal owner's account. The owner is the first player who opens the terminal's panel. Sneak + use shows the account.

Only plant products on the exchange list are accepted: coke, steel ingots and plates, bronze, polyethylene, cobalt, iridium. The exchange has no use for cobblestone or mob loot.

## Prices drift

Every commodity has its own price, and it moves. The log of the price is pulled back to a trend like a spring while random noise keeps pushing it (the Schwartz model). The usual swing is about 10 % of the trend, and it takes a few days to come back. The noise follows the world seed, so price history is repeatable.

## The market saturates

The more you sold recently, the lower the bid: the market remembers the accumulated sales volume, which fades over about two days. The price falls with constant elasticity. Bulk raw materials and metal have high elasticity (2–3), so the market takes a big batch, only cheaper. Rare products (cobalt, iridium) have 0.5, and revenue from any batch, however large, is capped: at a price of 100 CR and a market depth of 10 units you never get more than 1000 CR. Ten units at once bring 500 CR, not 1000.

So sell a little and often rather than hoarding a warehouse.

## Daily limit

One account earns at most 50 000 CR a day. When the limit is reached the red lamp on the panel lights up, and the rest of the batch waits in the slot for the next day.

## Allowances, fees and contracts

Whoever claims a market terminal first is the plant operator: the emission bills go to their account. The books close every 7 game days.

- **CO₂ allowances.** The first week is the base: its CO₂-equivalent emission becomes the baseline, with no fine. After that, each week's allocation is 5 % smaller. At the end of the week allowances are surrendered for the whole emission in the ledger (a calculated emission counts ×1.2, one measured by a gas analyser ×1.0). Short of allowances, you pay a fine of 100 CR a tonne, and the shortfall is added to next week's obligation. A surplus carries over. Nuclear, hydro and wind plants use no allowances.
- **Pollution fees:** SO₂ and NOx 0.5 CR per kg, dust 1 CR per kg.
- **Supply contract.** Put goods in the slot and press "Contract": an NPC buyer takes 64 units a week at today's price. The deposit is 20 % of a week's value. Goods from the slot go to the contract first; the rest goes to the market. At the end of the week you are paid for what was delivered. A shortfall costs a penalty of 20 % of the price of what is missing, no more than the deposit. Three weeks in a row below half is a default, and the buyer keeps the deposit.

If the account cannot cover a charge, it becomes a debt paid off from the terminal's sales.
