Yes, **both CoinGecko and CoinMarketCap offer free APIs** to list cryptocurrencies. Both also allow **testing without signing up**, though the limits and endpoints differ.

Here is a clear comparison of their free offerings:

| Feature | CoinGecko | CoinMarketCap |
| :--- | :--- | :--- |
| **Free Access Method** | **Keyless Public API** (no registration required) | **Trial Pro API** (no key required for testing) OR **Free Basic Plan** (requires registration) |
| **Endpoint to List Coins** | `GET /api/v3/coins/list` | `GET /v1/cryptocurrency/listings/latest` |
| **Free Call Limits** | ~**10–30 requests per minute** (IP-based, dynamic) | **15,000 requests per month** + **50 requests per minute** (on the Basic Plan) |
| **Data Coverage (Free)** | 50+ data endpoints | 30+ data endpoints |

---

### 🪙 CoinGecko Free API
CoinGecko provides a very convenient **Keyless Public API**, meaning you can call it immediately without an API key.

- **To get the coin list**: Call the `GET /api/v3/coins/list` endpoint. It returns a full list of all coin IDs, symbols, and names.
- **Rate Limits**: The free public tier is limited to about **10–30 calls per minute** per IP. They also offer a free "Demo" plan (with registration) that gives you **10,000 calls/month** at a rate of 100 calls/minute.
- **Caveats**: The free keyless API is **not suitable for production apps** or high-frequency updates. Also, the free tier cannot access data for "inactive" coins.

### 📈 CoinMarketCap Free API
CoinMarketCap also offers a free API, with two ways to access it:

- **No registration (Trial)**: You can test several endpoints immediately by using the `https://pro-api.coinmarketcap.com/trial-pro-api` path **without an API key**.
- **Registered Free Plan (Recommended)**: Sign up for the free **Basic Plan** to get a dedicated API key. This gives you a quota of **15,000 calls per month** and a rate limit of **50 calls per minute**.
- **To get the coin list**: The recommended endpoint is `GET /v1/cryptocurrency/listings/latest`, which returns the top 100 cryptocurrencies sorted by market cap, along with their detailed price and volume data.

---

### 💎 Summary & Recommendations
1.  **For quick testing**: Both are excellent and work without registration. Just use their keyless/trial endpoints.
2.  **For personal/small projects**: **CoinMarketCap’s free plan (15,000 calls/month)** generally offers a higher monthly quota than CoinGecko's Demo plan (10,000 calls/month), making it better for regular polling.
3.  **For production environments**: The free tiers for *both* platforms are usually insufficient for live products. You will likely need to upgrade to a paid plan for higher rate limits, historical data, and dedicated support.