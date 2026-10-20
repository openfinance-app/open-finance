# Asset Management

← [Wiki Home](HOME.md)

---

## Overview

The Asset module tracks your investment portfolio — stocks, ETFs, mutual funds, bonds, cryptocurrencies, commodities, real estate investments, vehicles, jewellery, and collectibles. Live market prices are fetched automatically for assets with a ticker symbol.

![Investment Assets Portfolio](screenshots/assets.png)

---

## Asset Types

| Type        | Examples                            |
| ----------- | ----------------------------------- |
| Stock       | AAPL, MSFT, GOOGL                   |
| ETF         | SPY, QQQ, VTI                       |
| Mutual Fund | VFIAX, FXAIX                        |
| Bond        | US Treasury bonds, corporate bonds  |
| Crypto      | BTC, ETH                            |
| Commodity   | Gold, Silver, Oil                   |
| Real Estate | REITs, rental property shares       |
| Vehicle     | Cars, motorcycles, boats            |
| Jewelry     | Watches, rings, gemstones           |
| Collectible | Art, antiques, coins, trading cards |
| Other       | Anything else                       |

---

## Asset Condition (Physical Assets)

For physical assets (vehicles, jewellery, collectibles), you can record the item’s condition:

| Condition | Description                    |
| --------- | ------------------------------ |
| New       | Brand new, unused              |
| Excellent | Near-perfect, light use        |
| Good      | Normal wear                    |
| Fair      | Visible wear, fully functional |
| Poor      | Significant wear or damage     |

---

## Key Fields

| Field          | Notes                                          |
| -------------- | ---------------------------------------------- |
| Name           | A label for this asset                         |
| Symbol         | Ticker (e.g., AAPL) for market-tracked assets  |
| Type           | Choose from the asset types above              |
| Quantity       | Number of units or shares                      |
| Purchase Price | Cost per unit when you bought it               |
| Current Price  | Current price per unit; refreshed for assets with a symbol |
| Currency       | Currency of both prices (e.g. EUR for a BTC purchase priced in euros) |
| Purchase Date  | Used to calculate gain/loss and holding period |
| Notes          | Optional free-text notes                       |

---

## Live Price Updates

For assets with a ticker symbol, the shipped configuration refreshes current market prices hourly, including weekends. You can also trigger an immediate refresh using **Refresh Prices** on the Assets page. The administrator can change the schedule; encrypted accounts require a cached encryption key from a recent login.

The detail chart shows daily prices per unit in its displayed currency, converted with the exchange rate for each date. A historical closing price can differ from the latest quote, and today's daily candle can still change. Missing market data or exchange rates are shown as unavailable.

See [Market Data](market-data.md) for details on supported symbols and price sources.

---

## Portfolio Metrics

The dashboard provides several portfolio views:

- **Asset Allocation** — breakdown by asset type (pie chart) with total portfolio value and unrealised gain/loss per asset.
- **Portfolio Performance** — time-weighted and money-weighted returns over a selected period.

---

## Related Pages

- [Market Data](market-data.md)
- [Real Estate](real-estate.md)
- [Dashboard](dashboard.md)
