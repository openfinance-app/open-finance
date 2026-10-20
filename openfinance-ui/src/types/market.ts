/**
 * Market data types
 * Task 5.4: Market data integration types
 */

export interface MarketQuote {
  symbol: string;
  price: number;
  change: number;
  changePercent: number;
  volume?: number;
  marketCap?: number;
  dayHigh?: number;
  dayLow?: number;
  yearHigh?: number;
  yearLow?: number;
  lastUpdated: string;
}

export interface HistoricalPrice {
  currency: string | null;
  date: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export interface SymbolSearchResult {
  symbol: string;
  name: string;
  type: string;
  exchange?: string;
}

export interface UpdatePriceResponse {
  success: boolean;
  message: string;
  asset?: {
    id: number;
    symbol: string;
    oldPrice: number;
    newPrice: number;
    lastUpdated: string;
  };
}
