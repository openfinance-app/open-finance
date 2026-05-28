/**
 * MSW (Mock Service Worker) Request Handlers
 * 
 * Defines mock API handlers for testing page-level components
 * and integration tests without hitting the real backend.
 */

import { http, HttpResponse } from 'msw';

// Use full URL to match axios requests that go to http://localhost:8080
const API_BASE_URL = '/api/v1';

// Mock authentication token
const MOCK_TOKEN = 'mock-jwt-token-12345';
const MOCK_ENCRYPTION_KEY = 'mock-encryption-key-67890';

// Mock user data
const mockUser = {
  id: 1,
  username: 'testuser',
  email: 'test@example.com',
  baseCurrency: 'USD',
  createdAt: '2026-01-01T00:00:00Z',
};

// Mock user settings
const mockUserSettings = {
  id: 1,
  userId: 1,
  theme: 'light' as const,
  dateFormat: 'MM/DD/YYYY' as const,
  numberFormat: '1,234.56' as const,
  language: 'en',
  timezone: 'America/New_York',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

// Mock accounts
const mockAccounts = [
  {
    id: 1,
    name: 'Checking Account',
    type: 'CHECKING',
    currency: 'USD',
    balance: 5000.0,
    isActive: true,
    description: 'Main checking account',
    createdAt: '2026-01-01T00:00:00Z',
  },
  {
    id: 2,
    name: 'Savings Account',
    type: 'SAVINGS',
    currency: 'USD',
    balance: 15000.0,
    isActive: true,
    description: 'Emergency fund',
    createdAt: '2026-01-01T00:00:00Z',
  },
];

// Mock transactions
const mockTransactions = {
  content: [
    {
      id: 1,
      accountId: 1,
      accountName: 'Checking Account',
      type: 'EXPENSE',
      amount: 50.75,
      currency: 'USD',
      categoryId: 54,
      categoryName: 'Groceries',
      categoryIcon: '🛒',
      categoryColor: '#10b981',
      date: '2026-02-03',
      description: 'Weekly groceries',
      notes: null,
      tags: ['groceries', 'food'],
      isReconciled: false,
      createdAt: '2026-02-03T10:30:00Z',
    },
    {
      id: 2,
      accountId: 1,
      accountName: 'Checking Account',
      type: 'INCOME',
      amount: 3000.0,
      currency: 'USD',
      categoryId: 1,
      categoryName: 'Salary',
      categoryIcon: '💼',
      categoryColor: '#3b82f6',
      date: '2026-02-01',
      description: 'Monthly salary',
      notes: null,
      tags: ['salary', 'income'],
      isReconciled: true,
      createdAt: '2026-02-01T08:00:00Z',
    },
  ],
  totalElements: 2,
  totalPages: 1,
  size: 20,
  number: 0,
};

// Mock categories
const mockCategories = [
  {
    id: 1,
    name: 'Salary',
    type: 'INCOME',
    icon: '💼',
    color: '#3b82f6',
    isSystem: true,
  },
  {
    id: 54,
    name: 'Groceries',
    type: 'EXPENSE',
    icon: '🛒',
    color: '#10b981',
    isSystem: true,
  },
];

// Mock payees
const mockPayees = [
  {
    id: 1,
    name: 'Walmart',
    lastUsedCategoryId: 54,
    usageCount: 5,
  },
  {
    id: 2,
    name: 'Acme Corp',
    lastUsedCategoryId: 1,
    usageCount: 12,
  },
];

// Mock institutions
const mockInstitutions = [
  {
    id: 1,
    name: 'Chase Bank',
  },
  {
    id: 2,
    name: 'Bank of America',
  },
];

// Mock currencies
const mockCurrencies = ['USD', 'EUR', 'GBP', 'CAD', 'JPY'];

// Mock liabilities
const mockLiabilities = [
  {
    id: 1,
    name: 'Mortgage',
    type: 'MORTGAGE',
    principalAmount: 250000.0,
    currentBalance: 235000.0,
    interestRate: 3.5,
    currency: 'USD',
    startDate: '2020-01-01',
  },
];

// Mock dashboard summary
const mockDashboardSummary = {
  netWorth: {
    netWorth: 20000.0,
    totalAssets: 20000.0,
    totalLiabilities: 0.0,
    currency: 'USD',
    date: '2026-02-04',
    monthlyChangeAmount: 2000.0,
    monthlyChangePercentage: 11.11,
  },
  accounts: mockAccounts,
  recentTransactions: mockTransactions.content.slice(0, 5),
  snapshotDate: '2026-02-04T00:00:00Z',
  totalAccounts: 2,
  totalTransactions: 2,
  baseCurrency: 'USD',
};

// Mock cash flow data
const mockCashFlow = {
  income: 6000.0,
  expenses: 2700.0,
  netCashFlow: 3300.0,
};

// Mock assets
const mockAssets = [
  {
    id: 1,
    accountId: 2,
    accountName: 'Savings Account',
    name: 'Apple Stock',
    type: 'STOCK',
    symbol: 'AAPL',
    quantity: 10,
    purchasePrice: 150.0,
    currentPrice: 180.0,
    currency: 'USD',
    purchaseDate: '2025-06-01',
    totalValue: 1800.0,
    totalCost: 1500.0,
    unrealizedGain: 300.0,
    gainPercentage: 20.0,
  },
];

/**
 * Request handlers for authentication
 */
export const authHandlers = [
  // Login
  http.post(`${API_BASE_URL}/auth/login`, async ({ request }) => {
    const body = await request.json() as any;
    if (body.username === 'testuser' && body.password === 'password123') {
      return HttpResponse.json({
        token: MOCK_TOKEN,
        userId: mockUser.id,
        username: mockUser.username,
        encryptionKey: MOCK_ENCRYPTION_KEY,
      });
    }
    return HttpResponse.json(
      { message: 'Invalid credentials' },
      { status: 401 }
    );
  }),

  // Register
  http.post(`${API_BASE_URL}/auth/register`, async () => {
    return HttpResponse.json(
      {
        token: MOCK_TOKEN,
        userId: mockUser.id,
        username: mockUser.username,
        encryptionKey: MOCK_ENCRYPTION_KEY,
      },
      { status: 201 }
    );
  }),

  // Get user settings
  http.get(`${API_BASE_URL}/users/me/settings`, () => {
    return HttpResponse.json(mockUserSettings);
  }),

  // Update user settings
  http.put(`${API_BASE_URL}/users/me/settings`, async ({ request }) => {
    const body = await request.json() as any;
    return HttpResponse.json({
      ...mockUserSettings,
      ...body,
      updatedAt: new Date().toISOString(),
    });
  }),
];

/**
 * Request handlers for accounts
 */
export const accountHandlers = [
  // Search accounts (with pagination) - must come before /accounts to match first
  http.get(`${API_BASE_URL}/accounts/search`, ({ request }) => {
    const url = new URL(request.url);
    const page = parseInt(url.searchParams.get('page') || '0');
    const size = parseInt(url.searchParams.get('size') || '20');

    // Return paginated response
    return HttpResponse.json({
      content: mockAccounts,
      totalElements: mockAccounts.length,
      totalPages: 1,
      number: page,
      size: size,
    });
  }),

  // Get all accounts
  http.get(`${API_BASE_URL}/accounts`, () => {
    return HttpResponse.json(mockAccounts);
  }),

  // Get account by ID
  http.get(`${API_BASE_URL}/accounts/:id`, ({ params }) => {
    const account = mockAccounts.find((a) => a.id === Number(params.id));
    if (account) {
      return HttpResponse.json(account);
    }
    return HttpResponse.json({ message: 'Account not found' }, { status: 404 });
  }),

  // Create account
  http.post(`${API_BASE_URL}/accounts`, async ({ request }) => {
    const body = await request.json() as any;
    const newAccount = {
      id: mockAccounts.length + 1,
      ...body,
      balance: body.initialBalance || 0,
      isActive: true,
      createdAt: new Date().toISOString(),
    };
    return HttpResponse.json(newAccount, { status: 201 });
  }),

  // Update account
  http.put(`${API_BASE_URL}/accounts/:id`, async ({ params, request }) => {
    const body = await request.json() as any;
    const account = mockAccounts.find((a) => a.id === Number(params.id));
    if (account) {
      const updated = { ...account, ...body };
      return HttpResponse.json(updated);
    }
    return HttpResponse.json({ message: 'Account not found' }, { status: 404 });
  }),

  // Delete account
  http.delete(`${API_BASE_URL}/accounts/:id`, ({ params }) => {
    const account = mockAccounts.find((a) => a.id === Number(params.id));
    if (account) {
      return new HttpResponse(null, { status: 204 });
    }
    return HttpResponse.json({ message: 'Account not found' }, { status: 404 });
  }),
];

/**
 * Request handlers for transactions
 */
export const transactionHandlers = [
  // Search transactions (with pagination)
  http.get(`${API_BASE_URL}/transactions/search`, () => {
    return HttpResponse.json(mockTransactions);
  }),

  // Get transaction by ID
  http.get(`${API_BASE_URL}/transactions/:id`, ({ params }) => {
    const transaction = mockTransactions.content.find(
      (t) => t.id === Number(params.id)
    );
    if (transaction) {
      return HttpResponse.json(transaction);
    }
    return HttpResponse.json(
      { message: 'Transaction not found' },
      { status: 404 }
    );
  }),

  // Create transaction
  http.post(`${API_BASE_URL}/transactions`, async ({ request }) => {
    const body = await request.json() as any;
    const newTransaction = {
      id: mockTransactions.content.length + 1,
      ...body,
      accountName: 'Checking Account',
      categoryName: 'Groceries',
      categoryIcon: '🛒',
      categoryColor: '#10b981',
      isReconciled: false,
      createdAt: new Date().toISOString(),
    };
    return HttpResponse.json(newTransaction, { status: 201 });
  }),

  // Get popular tags
  http.get(`${API_BASE_URL}/transactions/tags/popular`, () => {
    return HttpResponse.json(['groceries', 'food', 'salary', 'income', 'utilities']);
  }),
];

/**
 * Request handlers for categories
 */
export const categoryHandlers = [
  // Get all categories
  http.get(`${API_BASE_URL}/categories`, () => {
    return HttpResponse.json(mockCategories);
  }),

  // Get category tree
  http.get(`${API_BASE_URL}/categories/tree`, () => {
    return HttpResponse.json(mockCategories);
  }),
];

/**
 * Request handlers for payees
 */
export const payeeHandlers = [
  // Get active payees
  http.get(`${API_BASE_URL}/payees/active`, () => {
    return HttpResponse.json(mockPayees);
  }),
];

/**
 * Request handlers for institutions
 */
export const institutionHandlers = [
  // Get all institutions
  http.get(`${API_BASE_URL}/institutions`, () => {
    return HttpResponse.json(mockInstitutions);
  }),
];

/**
 * Request handlers for currencies
 */
export const currencyHandlers = [
  // Get supported currencies
  http.get(`${API_BASE_URL}/currencies`, () => {
    return HttpResponse.json(mockCurrencies);
  }),
];

/**
 * Request handlers for liabilities
 */
export const liabilityHandlers = [
  // Get all liabilities
  http.get(`${API_BASE_URL}/liabilities`, () => {
    return HttpResponse.json(mockLiabilities);
  }),
];

/**
 * Request handlers for dashboard
 */
export const dashboardHandlers = [
  // Get dashboard summary (main endpoint)
  http.get(`${API_BASE_URL}/dashboard`, () => {
    return HttpResponse.json(mockDashboardSummary);
  }),

  // Get dashboard summary (alternate endpoint)
  http.get(`${API_BASE_URL}/dashboard/summary`, () => {
    return HttpResponse.json(mockDashboardSummary);
  }),

  // Get account summaries
  http.get(`${API_BASE_URL}/dashboard/accounts`, () => {
    return HttpResponse.json(mockAccounts);
  }),

  // Get cash flow
  http.get(`${API_BASE_URL}/dashboard/cashflow`, () => {
    return HttpResponse.json(mockCashFlow);
  }),

  // Get net worth history
  http.get(`${API_BASE_URL}/dashboard/networth-history`, () => {
    return HttpResponse.json([
      { date: '2026-01-01', netWorth: 18000.0 },
      { date: '2026-02-01', netWorth: 19000.0 },
      { date: '2026-02-04', netWorth: 20000.0 },
    ]);
  }),

  // Get asset allocation
  http.get(`${API_BASE_URL}/dashboard/asset-allocation`, () => {
    return HttpResponse.json([
      { assetType: 'Cash', value: 20000.0, percentage: 40.0 },
      { assetType: 'Stocks', value: 25000.0, percentage: 50.0 },
      { assetType: 'Real Estate', value: 5000.0, percentage: 10.0 },
    ]);
  }),

  // Get portfolio performance
  http.get(`${API_BASE_URL}/dashboard/portfolio-performance`, () => {
    return HttpResponse.json({
      totalValue: 50000.0,
      totalGainLoss: 5000.0,
      percentageChange: 11.11,
      performance: [
        { date: '2026-01-01', value: 45000.0 },
        { date: '2026-01-15', value: 47000.0 },
        { date: '2026-02-01', value: 48000.0 },
        { date: '2026-02-04', value: 50000.0 },
      ],
    });
  }),

  // Get net worth allocation
  http.get(`${API_BASE_URL}/dashboard/networth-allocation`, () => {
    return HttpResponse.json([
      { category: 'Real Estate', value: 200000.0, percentage: 60.0 },
      { category: 'Equities', value: 100000.0, percentage: 30.0 },
      { category: 'Cash', value: 33333.33, percentage: 10.0 }
    ]);
  }),

  // Get borrowing capacity
  http.get(`${API_BASE_URL}/dashboard/borrowing-capacity`, () => {
    return HttpResponse.json({
      estimatedCapacity: 50000.0,
      monthlyIncome: 6000.0,
      monthlyDebtObligations: 1000.0,
      debtToIncomeRatio: 16.67,
      isHealthy: true
    });
  }),

  // Get daily cash flow
  http.get(`${API_BASE_URL}/dashboard/daily-cashflow`, ({ request }) => {
    const url = new URL(request.url);
    const year = parseInt(url.searchParams.get('year') || new Date().getFullYear().toString());
    const month = parseInt(url.searchParams.get('month') || (new Date().getMonth() + 1).toString());

    // Generate some mock daily data
    const daysInMonth = new Date(year, month, 0).getDate();
    const mockDailyData = [];

    for (let i = 1; i <= daysInMonth; i++) {
      const dateStr = `${year}-${String(month).padStart(2, '0')}-${String(i).padStart(2, '0')}`;

      // Add some random activity to make it look realistic
      let income = 0;
      let expense = 0;

      if (i === 1 || i === 15) {
        income = 3000; // Salary
      } else if (i % 3 === 0) {
        expense = 50 + (i * 10); // Groceries etc
      } else if (i === 5) {
        expense = 1200; // Rent
      }

      mockDailyData.push({
        date: dateStr,
        income,
        expense
      });
    }

    return HttpResponse.json(mockDailyData);
  }),

  // Get insights
  http.get(`${API_BASE_URL}/insights/top/3`, () => {
    return HttpResponse.json([
      { id: 1, type: 'SPENDING_ANOMALY', title: 'High spending in groceries', description: 'You spent 20% more on groceries this month.', priority: 'HIGH', dismissed: false, createdAt: new Date().toISOString() },
      { id: 2, type: 'SAVINGS_OPPORTUNITY', title: 'Good saving rate', description: 'You saved 30% of your income this month.', priority: 'MEDIUM', dismissed: false, createdAt: new Date().toISOString() },
    ]);
  }),

  // Get estimated interest
  http.get(`${API_BASE_URL}/dashboard/estimated-interest`, () => {
    return HttpResponse.json({
      estimatedInterest: 500.0,
      period: '1M',
    });
  }),

  // Get budgets summary
  http.get(`${API_BASE_URL}/budgets/summary`, () => {
    return HttpResponse.json({
      period: 'MONTHLY',
      totalBudgets: 3,
      activeBudgets: 3,
      totalBudgeted: 5000.0,
      totalSpent: 3500.0,
      totalRemaining: 1500.0,
      averageSpentPercentage: 70.0,
      currency: 'USD',
      budgets: [
        {
          budgetId: 1,
          categoryName: 'Groceries',
          budgeted: 600.0,
          spent: 450.0,
          remaining: 150.0,
          percentageSpent: 75.0,
          currency: 'USD',
          period: 'MONTHLY',
          startDate: '2024-01-01',
          endDate: '2024-01-31',
          daysRemaining: 15,
          status: 'WARNING',
        },
        {
          budgetId: 2,
          categoryName: 'Transportation',
          budgeted: 300.0,
          spent: 200.0,
          remaining: 100.0,
          percentageSpent: 66.7,
          currency: 'USD',
          period: 'MONTHLY',
          startDate: '2024-01-01',
          endDate: '2024-01-31',
          daysRemaining: 15,
          status: 'ON_TRACK',
        },
        {
          budgetId: 3,
          categoryName: 'Entertainment',
          budgeted: 200.0,
          spent: 250.0,
          remaining: -50.0,
          percentageSpent: 125.0,
          currency: 'USD',
          period: 'MONTHLY',
          startDate: '2024-01-01',
          endDate: '2024-01-31',
          daysRemaining: 15,
          status: 'EXCEEDED',
        },
      ],
    });
  }),

  // Get cashflow sankey data
  http.get(`${API_BASE_URL}/dashboard/cashflow-sankey`, () => {
    return HttpResponse.json({
      totalIncome: 5000.0,
      totalExpenses: 3500.0,
      surplus: 1500.0,
      period: 30,
      incomeSources: [
        { name: 'Salary', amount: 4000.0, color: '#10b981', icon: null },
        { name: 'Freelance', amount: 800.0, color: '#34d399', icon: null },
        { name: 'Investments', amount: 200.0, color: '#6ee7b7', icon: null },
      ],
      expenseCategories: [
        { name: 'Groceries', amount: 600.0, color: '#ec4899', icon: null },
        { name: 'Rent', amount: 1200.0, color: '#14b8a6', icon: null },
        { name: 'Transportation', amount: 400.0, color: '#3b82f6', icon: null },
        { name: 'Entertainment', amount: 300.0, color: '#f59e0b', icon: null },
        { name: 'Utilities', amount: 200.0, color: '#8b5cf6', icon: null },
        { name: 'Healthcare', amount: 150.0, color: '#ef4444', icon: null },
        { name: 'Dining Out', amount: 250.0, color: '#6b7280', icon: null },
        { name: 'Other', amount: 400.0, color: '#f97316', icon: null },
      ],
    });
  }),
];

/**
 * Request handlers for assets
 */
export const assetHandlers = [
  // Get all assets
  http.get(`${API_BASE_URL}/assets`, () => {
    return HttpResponse.json(mockAssets);
  }),
];

/**
 * Mock compound interest result
 */
export const mockCompoundInterestResult = {
  finalBalance: 20097.57,
  principal: 10000.00,
  totalContributions: 2400.00,
  totalInterest: 7697.57,
  totalInvested: 12400.00,
  effectiveAnnualRate: 7.2290,
  yearlyBreakdown: [
    {
      year: 1,
      startingBalance: 10000.00,
      contributions: 2400.00,
      interestEarned: 891.49,
      endingBalance: 13291.49,
      cumulativeInterest: 891.49,
      cumulativePrincipal: 12400.00,
    },
  ],
};

/**
 * Request handlers for compound interest calculator
 */
export const compoundInterestHandlers = [
  http.post(`${API_BASE_URL}/calculator/compound-interest/calculate`, () => {
    return HttpResponse.json(mockCompoundInterestResult);
  }),
];

/**
 * Request handlers for asset search (paginated)
 */
export const assetSearchHandlers = [
  http.get(`${API_BASE_URL}/assets/search`, () => {
    return HttpResponse.json({
      content: mockAssets,
      totalElements: mockAssets.length,
      totalPages: 1,
      number: 0,
      size: 20,
    });
  }),

  http.post(`${API_BASE_URL}/assets`, async ({ request }) => {
    const body = await request.json() as any;
    const newAsset = { id: mockAssets.length + 1, ...body };
    return HttpResponse.json(newAsset, { status: 201 });
  }),

  http.put(`${API_BASE_URL}/assets/:id`, async ({ params, request }) => {
    const body = await request.json() as any;
    const asset = mockAssets.find((a) => a.id === Number(params.id));
    return HttpResponse.json({ ...asset, ...body });
  }),

  http.delete(`${API_BASE_URL}/assets/:id`, () => {
    return new HttpResponse(null, { status: 204 });
  }),

  http.get(`${API_BASE_URL}/assets/:id`, ({ params }) => {
    const asset = mockAssets.find((a) => a.id === Number(params.id));
    if (asset) return HttpResponse.json(asset);
    return HttpResponse.json({ message: 'Asset not found' }, { status: 404 });
  }),

  http.post(`${API_BASE_URL}/assets/update-prices`, () => {
    return HttpResponse.json({ updatedCount: 1 });
  }),
];

/**
 * Request handlers for liabilities (paginated)
 */
export const liabilityPagedHandlers = [
  http.get(`${API_BASE_URL}/liabilities/paged`, () => {
    return HttpResponse.json({
      content: mockLiabilities,
      totalElements: mockLiabilities.length,
      totalPages: 1,
      number: 0,
      size: 20,
    });
  }),

  http.post(`${API_BASE_URL}/liabilities`, async ({ request }) => {
    const body = await request.json() as any;
    const newLiability = { id: mockLiabilities.length + 1, ...body };
    return HttpResponse.json(newLiability, { status: 201 });
  }),

  http.put(`${API_BASE_URL}/liabilities/:id`, async ({ params, request }) => {
    const body = await request.json() as any;
    const liability = mockLiabilities.find((l) => l.id === Number(params.id));
    return HttpResponse.json({ ...liability, ...body });
  }),

  http.delete(`${API_BASE_URL}/liabilities/:id`, () => {
    return new HttpResponse(null, { status: 204 });
  }),
];

/**
 * Request handlers for operation history
 */
export const historyHandlers = [
  http.get(`${API_BASE_URL}/history`, () => {
    return HttpResponse.json({
      content: [
        {
          id: 1,
          entityType: 'TRANSACTION',
          entityId: 1,
          operationType: 'CREATE',
          description: 'Created transaction: Weekly groceries',
          createdAt: '2026-02-03T10:30:00Z',
          canUndo: true,
          canRedo: false,
        },
        {
          id: 2,
          entityType: 'ACCOUNT',
          entityId: 1,
          operationType: 'UPDATE',
          description: 'Updated account: Checking Account',
          createdAt: '2026-02-02T09:00:00Z',
          canUndo: false,
          canRedo: false,
        },
      ],
      totalElements: 2,
      totalPages: 1,
      number: 0,
      size: 20,
    });
  }),

  http.post(`${API_BASE_URL}/history/:id/undo`, () => {
    return HttpResponse.json({ id: 1, operationType: 'CREATE', canUndo: false, canRedo: true });
  }),

  http.post(`${API_BASE_URL}/history/:id/redo`, () => {
    return HttpResponse.json({ id: 1, operationType: 'CREATE', canUndo: true, canRedo: false });
  }),
];

/**
 * Request handlers for budget alerts
 */
export const budgetAlertHandlers = [
  http.get(`${API_BASE_URL}/budgets/alerts/unread`, () => {
    return HttpResponse.json([]);
  }),

  http.get(`${API_BASE_URL}/budgets/alerts/unread/count`, () => {
    return HttpResponse.json(0);
  }),

  http.get(`${API_BASE_URL}/budgets/alerts/:budgetId`, () => {
    return HttpResponse.json([]);
  }),

  http.post(`${API_BASE_URL}/budgets/alerts`, async ({ request }) => {
    const body = await request.json() as any;
    return HttpResponse.json({ id: 'new-alert', ...body }, { status: 201 });
  }),

  http.put(`${API_BASE_URL}/budgets/alerts/:id`, async ({ params, request }) => {
    const body = await request.json() as any;
    return HttpResponse.json({ id: params.id, ...body });
  }),

  http.delete(`${API_BASE_URL}/budgets/alerts/:id`, () => {
    return new HttpResponse(null, { status: 204 });
  }),

  http.put(`${API_BASE_URL}/budgets/alerts/:id/read`, () => {
    return new HttpResponse(null, { status: 204 });
  }),

  http.put(`${API_BASE_URL}/budgets/alerts/read-all`, () => {
    return HttpResponse.json(0);
  }),
];

/**
 * Request handlers for notifications
 */
export const notificationHandlers = [
  http.get(`${API_BASE_URL}/notifications`, () => {
    return HttpResponse.json([]);
  }),

  http.get(`${API_BASE_URL}/notifications/count`, () => {
    return HttpResponse.json(0);
  }),
];

/**
 * Request handlers for search
 */
export const searchHandlers = [
  http.get(`${API_BASE_URL}/search/global`, () => {
    return HttpResponse.json({
      results: [],
      totalResults: 0,
      query: '',
    });
  }),

  http.post(`${API_BASE_URL}/search/advanced`, () => {
    return HttpResponse.json({
      results: [],
      totalResults: 0,
      query: '',
    });
  }),

  http.get(`${API_BASE_URL}/search/saved`, () => {
    return HttpResponse.json([]);
  }),
];

/**
 * Request handlers for market data
 */
export const marketDataHandlers = [
  http.post(`${API_BASE_URL}/market-data/update-all`, () => {
    return HttpResponse.json({ updatedCount: 0 });
  }),
];

/**
 * All handlers combined
 */
export const handlers = [
  ...authHandlers,
  ...accountHandlers,
  ...transactionHandlers,
  ...categoryHandlers,
  ...payeeHandlers,
  ...institutionHandlers,
  ...currencyHandlers,
  ...liabilityHandlers,
  ...liabilityPagedHandlers,
  ...dashboardHandlers,
  ...assetHandlers,
  ...assetSearchHandlers,
  ...compoundInterestHandlers,
  ...historyHandlers,
  ...budgetAlertHandlers,
  ...notificationHandlers,
  ...searchHandlers,
  ...marketDataHandlers,
];
