/**
 * Transaction-related types
 */

export type TransactionType =
  | 'INCOME'
  | 'EXPENSE'
  | 'TRANSFER';

/**
 * Payment method used for a transaction
 */
export type PaymentMethod =
  | 'CASH'
  | 'CHEQUE'
  | 'CREDIT_CARD'
  | 'DEBIT_CARD'
  | 'BANK_TRANSFER'
  | 'DEPOSIT'
  | 'STANDING_ORDER'
  | 'DIRECT_DEBIT'
  | 'ONLINE'
  | 'OTHER';

export interface Category {
  id: number;
  userId: number;
  name: string;
  type: TransactionType;
  parentId?: number;
  icon?: string;
  color?: string;
  mccCode?: string;
  isSystem?: boolean;
  subcategoryCount?: number;
}

/**
 * Category tree node for hierarchical display
 */
export interface CategoryTreeNode {
  id: number;
  name: string;
  type: TransactionType;
  icon?: string;
  color?: string;
  mccCode?: string;
  parentId?: number | null;
  subcategories: CategoryTreeNode[];
  transactionCount?: number;
  totalAmount?: number;
  isSystem?: boolean;
}

/**
 * Request payload for a single transaction split line.
 * Requirement REQ-SPL-3.1, REQ-SPL-3.2
 */
export interface TransactionSplitRequest {
  /** Optional category to assign to this split */
  categoryId?: number;
  /** Amount for this split — must be > 0 */
  amount: number;
  /** Optional description for this split (max 255 chars) */
  description?: string;
}

/**
 * Response for a single transaction split line, with denormalized category data.
 * Requirement REQ-SPL-4.3
 */
export interface TransactionSplitResponse {
  id: number;
  transactionId: number;
  categoryId?: number;
  /** Denormalized category name */
  categoryName?: string;
  /** Denormalized category icon */
  categoryIcon?: string;
  /** Denormalized category color */
  categoryColor?: string;
  amount: number;
  description?: string;
}

export interface Transaction {
  id: number;
  userId: number;
  accountId: number;
  toAccountId?: number;
  type: TransactionType;
  amount: number;
  currency: string;
  categoryId?: number;
  date: string;
  description?: string;
  notes?: string;
  payee?: string;
  tags?: string[];
  paymentMethod?: PaymentMethod;
  isReconciled: boolean;
  createdAt: string;
  updatedAt?: string;
  // Requirement 3.1: Optional link to a liability for loan payment tracking
  liabilityId?: number;
  // Original (pre-conversion) values — set only when the transaction was entered in a currency
  // different from its account currency. Used by the edit form to restore what the user typed.
  /** Pre-conversion amount in originalCurrency (e.g. 100) */
  originalAmount?: number;
  /** Currency originally entered (e.g. "USD") */
  originalCurrency?: string;
  /** Applied rate: 1 originalCurrency = conversionRate * currency (e.g. 0.9) */
  conversionRate?: number;
  
  // Denormalized fields from backend
  accountName?: string;
  toAccountName?: string;
  categoryName?: string;
  categoryIcon?: string;
  categoryColor?: string;
  transferId?: string;
  isDeleted?: boolean;
  
  // Populated fields (for local use)
  account?: { id: number; name: string };
  toAccount?: { id: number; name: string };
  category?: Category;

  // Split transaction fields — Requirement REQ-SPL-4.1, REQ-SPL-4.2
  /** True if this transaction has one or more split lines */
  hasSplits?: boolean;
  /** Split lines; populated when hasSplits is true */
  splits?: TransactionSplitResponse[];

  // Currency conversion fields — Requirement REQ-9.1
  /** Transaction amount converted to the user's base currency */
  amountInBaseCurrency?: number;
  /** The user's base currency at the time the response was built */
  baseCurrency?: string;
  /** Exchange rate used: 1 unit of currency = exchangeRate units of baseCurrency */
  exchangeRate?: number;
  /** True when currency differs from baseCurrency and conversion succeeded */
  isConverted?: boolean;
}

export interface TransactionRequest {
  accountId: number;
  toAccountId?: number;
  type: TransactionType;
  amount: number;
  currency: string;
  categoryId?: number;
  date: string;
  description?: string;
  notes?: string;
  payee?: string;
  tags?: string[];
  paymentMethod?: PaymentMethod;
  // Requirement 3.1: Optional link to a liability for EXPENSE transactions
  liabilityId?: number;
  // Original (pre-conversion) values, submitted together when a conversion was applied.
  originalAmount?: number;
  originalCurrency?: string;
  conversionRate?: number;
  // Requirement REQ-SPL-2.1, REQ-SPL-2.2: Split lines for INCOME/EXPENSE transactions
  splits?: TransactionSplitRequest[];
}

/**
 * Request type for updating an existing transfer transaction.
 * Both sides of the transfer (source EXPENSE and destination INCOME) are updated atomically.
 */
export interface TransferUpdateRequest {
  fromAccountId: number;
  toAccountId: number;
  amount: number;
  currency: string;
  date: string;
  description?: string;
  notes?: string;
  payee?: string;
  tags?: string[];
  paymentMethod?: PaymentMethod;
  isReconciled: boolean;
}

export interface TransactionFilters {
  accountId?: number;
  type?: TransactionType;
  categoryId?: number;
  /** When true, filter to show only transactions without a category assigned */
  noCategory?: boolean;
  payee?: string;
  /** When true, filter to show only transactions without a payee assigned */
  noPayee?: boolean;
  dateFrom?: string;
  dateTo?: string;
  keyword?: string;
  tag?: string;
  minAmount?: number;
  maxAmount?: number;
  page?: number;
  size?: number;
  sort?: string; // e.g., "date,desc" or "amount,asc"
}
