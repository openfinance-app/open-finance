# Market Data

← [Wiki Home](HOME.md)

---

## Overview

Open-Finance integrates with Yahoo Finance to fetch real-time and historical price data for your investment assets. Exchange rates for multi-currency support are sourced from the European Central Bank (ECB).

There is no dedicated Market Data page: prices, charts, and symbol search live on the [Assets](assets.md) pages, and updates run via a background scheduler.

---

## Supported Asset Types for Live Prices

| Asset Type  | Symbol Format      | Example           |
| ----------- | ------------------ | ----------------- |
| Stock       | Exchange ticker    | AAPL, MSFT, MC.PA |
| ETF         | Exchange ticker    | SPY, IWDA.AS      |
| Mutual Fund | Fund ticker        | VFIAX             |
| Crypto      | {SYMBOL}-USD       | BTC-USD, ETH-USD  |
| Commodity   | Yahoo Finance code | GC=F (Gold)       |
| REIT        | Exchange ticker    | VNQ               |

---

## Automatic Price Updates

Prices are fetched automatically once a day at market close. Open-Finance updates the current price for every asset in your portfolio that has a ticker symbol, then converts prices to your base currency using the latest exchange rates.

---

## Manual Price Refresh

To get an immediate price update without waiting for the next scheduled run, open any asset’s detail page and click **Refresh Price**.

---

## Historical Price Charts

The asset detail page shows a price history chart. You can select different time ranges:

| Range | Description      |
| ----- | ---------------- |
| 1D    | Last trading day |
| 5D    | Last 5 days      |
| 1M    | Last month       |
| 3M    | Last 3 months    |
| 6M    | Last 6 months    |
| 1Y    | Last 12 months   |
| 2Y    | Last 2 years     |
| 5Y    | Last 5 years     |

---

## Symbol Search

When adding a new asset, use the symbol search field to find and verify the correct ticker before saving. Results include the security name, exchange, and asset type.

---

## Exchange Rates

Currency conversion uses daily rates published by the European Central Bank. Rates are refreshed once a day in the late afternoon (ECB publication time). All dashboard totals and portfolio values are converted to your base currency using these rates.

Approximately 30 major currencies are supported.

---

## Offline Mode

If you prefer to run Open-Finance without any outbound market data connections, your administrator can disable the market data integration. In this mode, prices must be updated manually.

---

## Related Pages

- [Assets](assets.md)
- [Financial News](news.md)
- [Dashboard](dashboard.md)
