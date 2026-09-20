import { test, expect, type Page } from '@playwright/test';

test.use({ timezoneId: 'Europe/Paris' });
const today = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Paris' }).format(new Date());
const displayedToday = today.split('-').reverse().join('/');

async function api(page: Page, path: string, body?: unknown, method = 'GET') {
  const result = await page.evaluate(
    async ({ path, body, method }) => {
      const response = await fetch(`/api/v1${path}`, {
        method,
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${localStorage.getItem('auth_token') || sessionStorage.getItem('auth_token')}`,
          'X-Encryption-Session': sessionStorage.getItem('encryption_session') || '',
        },
        body: body === undefined ? undefined : JSON.stringify(body),
      });
      return { status: response.status, text: await response.text() };
    },
    { path, body, method }
  );
  expect(result.status, result.text).toBeLessThan(300);
  return result.text ? JSON.parse(result.text) : null;
}

test.beforeEach(async ({ page, request }, testInfo) => {
  const username = `ux_${Date.now()}`;
  const credentials = { username, password: 'Password123!', masterPassword: 'RealMaster123!' };
  const registration = await request.post('/api/v1/auth/register', {
    data: {
      ...credentials,
      email: `${username}@example.invalid`,
      skipSeeding: !testInfo.title.includes('French seeded'),
    },
  });
  expect(registration.status(), await registration.text()).toBe(201);
  await page.goto('/login');
  await page.getByLabel(/username/i).fill(username);
  await page.locator('#password').fill(credentials.password);
  await page.locator('#masterPassword').fill(credentials.masterPassword);
  await page.getByRole('button', { name: /sign in|log in/i }).click();
  await page.waitForURL('**/onboarding');
});

async function completeOnboarding(page: Page) {
  await api(
    page,
    '/users/me/onboarding',
    {
      country: 'FR',
      baseCurrency: 'EUR',
      secondaryCurrency: '',
      language: 'en',
      dateFormat: 'DD/MM/YYYY',
      numberFormat: '1,234.56',
      amountDisplayMode: 'base',
    },
    'POST'
  );
  await page.goto('/dashboard');
  await expect(page.getByRole('heading', { name: 'Dashboard', exact: true })).toBeVisible();
}

async function accountFixture(page: Page) {
  return api(
    page,
    '/accounts',
    {
      name: 'UX checking',
      type: 'CHECKING',
      currency: 'EUR',
      initialBalance: 1000,
      openingDate: '2026-01-01',
    },
    'POST'
  );
}

test('onboarding renders labels and saves the header language across reloads and settings changes', async ({
  page,
}) => {
  await expect(page.getByText('country.label', { exact: true })).toHaveCount(0);
  await expect(page.getByText('Country', { exact: true })).toBeVisible();
  await page.getByRole('combobox', { name: 'Language', exact: true }).click();
  await page.getByRole('option', { name: 'Français' }).click();
  await page.getByRole('button', { name: 'DD/MM/YYYY', exact: true }).click();
  await page.getByRole('button', { name: /commencer/i }).click();
  await page.waitForURL('**/dashboard');
  expect((await api(page, '/users/me/settings')).language).toBe('fr');
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Tableau de bord', exact: true })).toBeVisible();
  await expect(page.locator('html')).toHaveAttribute('lang', 'fr');
  await expect(page.getByText(new RegExp(`au ${displayedToday}`))).toBeVisible();
  await page.goto('/settings');
  await page.getByRole('button', { name: 'Affichage', exact: true }).click();
  await page.getByRole('combobox', { name: 'Langue', exact: true }).click();
  const persisted = page.waitForResponse(
    response =>
      response.url().endsWith('/users/me/settings') && response.request().method() === 'PUT'
  );
  await page.getByRole('option', { name: /English|Anglais/ }).click();
  expect((await persisted).ok()).toBeTruthy();
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Settings', exact: true })).toBeVisible();
  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  expect((await api(page, '/users/me/settings')).language).toBe('en');
});

test('expense editing, payee propagation, recurring processing, and history preserve exact balances', async ({
  page,
}) => {
  await completeOnboarding(page);
  const account = await accountFixture(page);
  const payee = await api(page, '/payees', { name: 'Old UX merchant' }, 'POST');
  const transaction = await api(
    page,
    '/transactions',
    {
      accountId: account.id,
      type: 'EXPENSE',
      amount: 10.99,
      currency: 'EUR',
      date: today,
      payeeId: payee.id,
      payee: payee.name,
      description: 'Editable UX expense',
    },
    'POST'
  );
  expect(transaction.movementType).toBeNull();
  expect(transaction.createdAt).toMatch(/(?:Z|[+-]\d{2}:\d{2})$/);
  await page.goto('/transactions');
  await page.getByRole('button', { name: 'Edit transaction', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.locator('#amount').fill('12.49');
  await dialog.getByRole('button', { name: /update transaction/i }).click();
  await expect(dialog).not.toBeVisible();
  expect((await api(page, `/accounts/${account.id}`)).balance).toBe(987.51);
  await api(page, `/payees/${payee.id}`, { name: 'Renamed UX merchant' }, 'PUT');
  expect((await api(page, `/transactions/${transaction.id}`)).payee).toBe('Renamed UX merchant');
  const category = await api(
    page,
    '/categories',
    { name: 'UX subscriptions', type: 'EXPENSE', color: '#123456', icon: 'book' },
    'POST'
  );
  const recurring = await api(
    page,
    '/recurring-transactions',
    {
      accountId: account.id,
      type: 'EXPENSE',
      amount: 35,
      currency: 'EUR',
      description: 'UX monthly payment',
      frequency: 'MONTHLY',
      nextOccurrence: today,
      categoryId: category.id,
      active: true,
    },
    'POST'
  );
  await page.goto('/recurring-transactions');
  await page.getByRole('button', { name: 'Process due now' }).click();
  await expect(
    page.getByRole('status').filter({ hasText: 'Processed: 1. Failed: 0.' })
  ).toBeVisible();
  expect((await api(page, `/accounts/${account.id}`)).balance).toBe(952.51);
  expect((await api(page, '/recurring-transactions/process', {}, 'POST')).processedCount).toBe(0);
  const history = await api(page, '/history?size=100');
  for (const [type, id] of [
    ['PAYEE', payee.id],
    ['CATEGORY', category.id],
    ['RECURRING_TRANSACTION', recurring.id],
  ]) {
    expect(
      history.content.some(
        (entry: { entityType: string; entityId: number }) =>
          entry.entityType === type && entry.entityId === id
      )
    ).toBeTruthy();
  }
  await page.goto('/accounts');
  await expect(page.getByText(/952\.51/).first()).toBeVisible();
  await page
    .getByRole('button', { name: /add account/i })
    .first()
    .click();
  await page.locator('#initialBalance').fill('2000');
  await page.locator('#name').fill('UX savings');
  await page
    .getByRole('dialog')
    .getByRole('button', { name: /create account/i })
    .click();
  await expect(page.getByRole('dialog')).not.toBeVisible();
  await expect(page.getByText('UX savings', { exact: true })).toBeVisible();
  await expect(page.getByText(/2,952\.51/).first()).toBeVisible();
});

test('canceling a reviewed import resets the wizard and cancels its server session', async ({
  page,
}) => {
  await completeOnboarding(page);
  await accountFixture(page);
  await page.goto('/import');
  await page.locator('input[type="file"]').setInputFiles({
    name: 'ux-decimals.csv',
    mimeType: 'text/csv',
    buffer: Buffer.from(`Date;Payee;Amount\n${today};UX cancellation;-10,99\n`),
  });
  await page.getByRole('button', { name: /^upload file$/i }).click();
  await page.getByRole('button', { name: /next/i }).click();
  await expect(
    page.getByRole('heading', { name: 'Review Transactions', exact: true })
  ).toBeVisible();
  await page.getByRole('button', { name: /^cancel$/i }).click();
  const cancelled = page.waitForResponse(response =>
    /\/import\/sessions\/\d+\/cancel/.test(response.url())
  );
  await page.getByRole('button', { name: /leave anyway/i }).click();
  expect((await cancelled).ok()).toBeTruthy();
  await expect(page.getByRole('button', { name: /browse files/i })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Review Transactions', exact: true })).toHaveCount(
    0
  );
});

test('CSV validation preserves decimal amounts and completed imports appear in history', async ({
  page,
}) => {
  await completeOnboarding(page);
  const account = await accountFixture(page);
  await page.goto('/import');
  await page.locator('input[type="file"]').setInputFiles({
    name: 'ux-ambiguous.csv',
    mimeType: 'text/csv',
    buffer: Buffer.from(`Date,Payee,Amount\n${today},UX malformed,-10,99\n`),
  });
  await page.getByRole('button', { name: /^upload file$/i }).click();
  await page.getByRole('button', { name: /^next$/i }).click();
  await expect(page.getByText(/columns|semicolon|quotes/i).first()).toBeVisible();
  await page.getByRole('button', { name: /^cancel$/i }).click();
  await page.getByRole('button', { name: /leave anyway/i }).click();
  await expect(page.getByRole('button', { name: /browse files/i })).toBeVisible();
  await page.locator('input[type="file"]').setInputFiles({
    name: 'ux-exact.csv',
    mimeType: 'text/csv',
    buffer: Buffer.from(`Date;Payee;Amount\n${today};UX exact;-10,99\n`),
  });
  await page.getByRole('button', { name: /^upload file$/i }).click();
  await page.locator('select').selectOption(String(account.id));
  await page.getByRole('button', { name: /^next$/i }).click();
  await expect(
    page.getByRole('heading', { name: 'Review Transactions', exact: true })
  ).toBeVisible();
  await page.getByRole('button', { name: /^next$/i }).click();
  await page.getByRole('button', { name: /^confirm import$/i }).click();
  await expect.poll(async () => (await api(page, `/accounts/${account.id}`)).balance).toBe(989.01);
  const history = await api(page, '/history?entityType=IMPORT');
  expect(history.totalElements).toBe(1);
  expect(history.content[0].entityLabel).toBe('ux-exact.csv');
});

test('minor fixes persist category icons and notification acknowledgement with amount blur', async ({
  page,
}) => {
  await completeOnboarding(page);
  await api(page, '/users/me/settings', { numberFormat: '1 234,56' }, 'PUT');
  await page.goto('/categories');
  await page.getByRole('button', { name: 'Add Category', exact: true }).first().click();
  const dialog = page.getByRole('dialog');
  await dialog.getByRole('button', { name: 'Choose icon 🏋️' }).click();
  await expect(dialog.getByRole('button', { name: 'Choose icon 🏋️' })).toHaveAttribute(
    'aria-pressed',
    'true'
  );
  await dialog.getByPlaceholder('Name', { exact: true }).fill('UX fitness');
  await dialog.getByRole('button', { name: /create|save/i }).click();
  await expect(dialog).not.toBeVisible();
  await page.reload();
  await expect(page.getByText('UX fitness', { exact: true })).toBeVisible();
  const categories = await api(page, '/categories');
  expect(categories.find((category: { name: string }) => category.name === 'UX fitness').icon).toBe(
    '🏋️'
  );
  await api(
    page,
    '/accounts',
    {
      name: 'Minor checking',
      type: 'CHECKING',
      currency: 'EUR',
      initialBalance: 723.45,
      openingDate: '2026-01-01',
    },
    'POST'
  );
  await page.goto('/accounts');
  await expect(page.getByText(/723,45/).first()).toBeVisible();
  await page.getByRole('button', { name: 'Hide amounts', exact: true }).click();
  const blurredBalance = page.locator('span.blur-md').filter({ hasText: '723,45' }).first();
  await expect(blurredBalance).toHaveAttribute('aria-hidden', 'true');
  await expect(blurredBalance).toHaveCSS('filter', /blur\(/);
  const bell = page.getByRole('button', { name: /^Notifications \(/ });
  await expect(bell).not.toHaveAttribute('aria-label', 'Notifications (0 unread)');
  const unreadBefore = Number((await bell.getAttribute('aria-label'))?.match(/\((\d+)/)?.[1]);
  await bell.click();
  await page.getByRole('button', { name: 'Low Account Balance', exact: true }).click();
  await expect(bell).toHaveAttribute('aria-label', `Notifications (${unreadBefore - 1} unread)`);
  await page.reload();
  await expect(bell).toHaveAttribute('aria-label', `Notifications (${unreadBefore - 1} unread)`);
  await expect(blurredBalance).toHaveAttribute('aria-hidden', 'true');
  await expect(blurredBalance).toHaveCSS('filter', /blur\(/);
  await page.getByRole('button', { name: 'Show amounts', exact: true }).click();
  await expect(blurredBalance).toHaveCount(0);
  await expect(page.getByText(/723,45/).first()).toBeVisible();
  const compound = await api(
    page,
    '/calculator/compound-interest/calculate',
    {
      principal: 10000,
      annualRate: 5,
      compoundingFrequency: 12,
      years: 10,
      regularContribution: 0,
      contributionAtBeginning: false,
    },
    'POST'
  );
  expect(compound.finalBalance).toBe(16470.09);
  await api(
    page,
    '/assets',
    {
      name: 'Minor bond',
      type: 'BOND',
      quantity: 1,
      purchasePrice: 50,
      currentPrice: 57,
      currency: 'EUR',
      purchaseDate: '2026-01-01',
    },
    'POST'
  );
  await api(
    page,
    '/accounts',
    {
      name: 'Minor credit card',
      type: 'CREDIT_CARD',
      currency: 'EUR',
      initialBalance: -250,
      openingDate: '2026-01-01',
    },
    'POST'
  );
  const allocations = await api(page, '/dashboard/networth-allocation');
  expect(allocations.some((item: { isLiability: boolean }) => item.isLiability)).toBe(true);
  expect(
    allocations.reduce((total: number, item: { percentage: number }) => total + item.percentage, 0)
  ).toBeCloseTo(100, 1);
  await page.goto('/dashboard');
  const allocationCard = page
    .getByRole('heading', { name: 'Asset Allocation', exact: true })
    .locator('../..');
  await expect(allocationCard.locator('svg text').filter({ hasText: '100,00%' })).toBeVisible();
  expect(await allocationCard.locator('svg text').allTextContents()).not.toContain('%');
  expect(await allocationCard.locator('svg text').allTextContents()).not.toContain('€0,00');
  await page.goto('/assets');
  await expect(page.getByText(/14,00%/).first()).toBeVisible();
});

test('QIF review preserves Food instead of matching Fast Food and explains account backdating', async ({
  page,
}) => {
  await completeOnboarding(page);
  const account = await accountFixture(page);
  await api(page, '/categories', { name: 'Fast Food', type: 'EXPENSE' }, 'POST');
  await page.goto('/import');
  await page.locator('input[type="file"]').setInputFiles({
    name: 'ux-category.qif',
    mimeType: 'application/qif',
    buffer: Buffer.from('!Type:Bank\nD01/02/2026\nT-25.50\nPPicard\nLFood\n^\n'),
  });
  await page.getByRole('button', { name: /^upload file$/i }).click();
  await expect(page.getByText(/predates the account opening date/)).toBeVisible();
  await page.locator('select').selectOption(String(account.id));
  await page.getByRole('button', { name: /^next$/i }).click();
  await expect(
    page.getByRole('heading', { name: 'Review Transactions', exact: true })
  ).toBeVisible();
  await expect(page.getByRole('cell', { name: 'Food', exact: true })).toBeVisible();
  await expect(page.getByText('Fast Food', { exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: /^next$/i }).click();
  await page.getByRole('button', { name: /^confirm import$/i }).click();
  await expect.poll(async () => (await api(page, `/accounts/${account.id}`)).balance).toBe(974.5);
  const categories = await api(page, '/categories');
  expect(categories.find((category: { name: string }) => category.name === 'Food')).toBeTruthy();
  await page.goto('/transactions');
  await expect(page.getByText('Picard', { exact: true })).toHaveCount(1);
});

test('French seeded categories, cached Sankey names and localized validation stay consistent', async ({
  page,
}) => {
  await completeOnboarding(page);
  const account = await accountFixture(page);
  const categories = await api(page, '/categories');
  const salary = categories.find((category: { name: string }) => category.name === 'Salary');
  const healthcare = categories.find(
    (category: { name: string }) => category.name === 'Healthcare'
  );
  expect(salary).toBeTruthy();
  expect(healthcare).toBeTruthy();
  for (const transaction of [
    { type: 'INCOME', amount: 3200, categoryId: salary.id, payee: 'Salary' },
    { type: 'EXPENSE', amount: 100, categoryId: healthcare.id, payee: 'Doctor' },
  ]) {
    await api(
      page,
      '/transactions',
      { ...transaction, accountId: account.id, currency: 'EUR', date: today },
      'POST'
    );
  }
  const english = await api(page, '/dashboard/cashflow-sankey?period=30');
  expect(english.incomeSources.some((node: { name: string }) => node.name === 'Salary')).toBe(true);
  await api(page, '/users/me/settings', { language: 'fr', numberFormat: '1 234,56' }, 'PUT');
  await page.evaluate(() => localStorage.setItem('openfinance_language', 'fr'));
  await page.goto('/dashboard');
  await expect(page.getByRole('heading', { name: 'Tableau de bord', exact: true })).toBeVisible();
  await expect(page.getByText('Salaire', { exact: true }).first()).toBeVisible();
  const localized = await page.evaluate(async () => {
    const headers = {
      Authorization: `Bearer ${sessionStorage.getItem('auth_token') || localStorage.getItem('auth_token')}`,
      'X-Encryption-Session': sessionStorage.getItem('encryption_session') || '',
      'Accept-Language': 'fr',
      'Content-Type': 'application/json',
    };
    const sankey = await (
      await fetch('/api/v1/dashboard/cashflow-sankey?period=30', { headers })
    ).json();
    const password = await fetch('/api/v1/users/me/password', {
      method: 'PUT',
      headers,
      body: JSON.stringify({
        currentPassword: 'WrongPassword123!',
        newPassword: 'NewPassword123!',
      }),
    });
    return { sankey, passwordStatus: password.status, password: await password.json() };
  });
  expect(
    localized.sankey.incomeSources.some((node: { name: string }) => node.name === 'Salaire')
  ).toBe(true);
  expect(localized.passwordStatus).toBe(400);
  expect(localized.password.message).toBe('Le mot de passe actuel est incorrect');
  const tree = await api(page, '/categories/tree');
  const flatten = (
    nodes: { id: number; totalAmount: number; subcategories?: unknown[] }[]
  ): { id: number; totalAmount: number }[] =>
    nodes.flatMap(node => [node, ...flatten((node.subcategories ?? []) as typeof nodes)]);
  expect(flatten(tree).find(category => category.id === healthcare.id)?.totalAmount).toBe(100);
  const payees = await api(page, '/payees/system');
  expect(payees.some((payee: { name: string }) => payee.name === 'Loan Payment Test')).toBe(false);
});
