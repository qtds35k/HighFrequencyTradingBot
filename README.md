# High-Frequency Trading Bot
A web bot that automatically trades NYSE stocks and ETFs, implementing 3 high-frequency trading strategies:
1. Stable oscillation buying
    - Place buy orders when current quote price is lower than short/long-term average by some value.
2. Up-trend buying
    - Place buy orders when the price of our target is temporarily up-trending
3. Pseudo-market-maker
    - Keep a buy order tracing the current bid price. If bought, place a sell at some higher price

Run **Main.java** to start trading. A First-trade account is required. <br>
<i>STOP_LOSS</i> and <i>CAPITAL</i> quota can be adjusted in **Manager.java**.

## Reference
- [High-Frequency Trading](https://www.investopedia.com/ask/answers/09/high-frequency-trading.asp)
- [Market Making Strategy](https://www.quantinsti.com/blog/automated-market-making-overview)
