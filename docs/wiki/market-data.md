# Market Data

← [Wiki Home](HOME.md)

---

## Overview

Open-Finance integrates with Yahoo Finance to fetch real-time and historical price data for your investment assets. Currency conversion uses stored exchange rates and can fetch missing pairs from Yahoo Finance.

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

The shipped schedule is hourly, including nights and weekends, so crypto prices can refresh around the clock. Quotes are converted into the asset's price currency; portfolio totals can then be displayed in your base currency. Planned acquisitions and assets without a symbol are skipped.

Administrators can configure `application.scheduled.market-data.mode`. `EVERY_HOUR` runs at the top of every hour; `DEFAULT` restricts updates to weekday market hours (9 AM–4 PM America/New_York unless configured otherwise). Encrypted deployments require each user's cached encryption key from a recent login. Users without a cached key are skipped until they log in again.

---

## Manual Price Refresh

To get an immediate price update without waiting for the next scheduled run, click **Refresh Prices** on the Assets page.

---

## Historical Price Charts

The asset detail page shows daily prices per unit in the chart's displayed currency. The API retains the provider's quote currency and converts each point with its dated exchange rate. Closing prices can differ from the latest quote, and today's candle can still change. If currency metadata or exchange rates are unavailable, the chart shows an error instead of labeling unconverted prices with a different currency.

You can select different time ranges:

| Range | Description      |
| ----- | ---------------- |
| 1M    | Last month       |
| 3M    | Last 3 months    |
| 6M    | Last 6 months    |
| 1Y    | Last 12 months   |

---

## Symbol Search

When adding a new asset, use the symbol search field to find and verify the correct ticker before saving. Results include the security name, exchange, and asset type.

---

## Exchange Rates

Historical conversions use the latest available exchange rate on or before the requested date. Current valuations use the latest available rate. The exchange-rate refresh schedule is configured independently through `application.scheduled.exchange-rates`.

---

## Offline Mode

If you prefer to run Open-Finance without any outbound market data connections, your administrator can disable the market data integration. In this mode, prices must be updated manually.

---

## Related Pages

- [Assets](assets.md)
- [Financial News](news.md)
- [Dashboard](dashboard.md)
