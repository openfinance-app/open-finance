import { test, expect, type Page } from '@playwright/test';
import { loginAs, registerWithRetry } from './helpers/auth';

const month = (offset: number) => {
  const now = new Date();
  const date = new Date(now.getFullYear(), now.getMonth() + offset, 1);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-01`;
};

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

test.beforeEach(async ({ page, request }) => {
  const username = `audit_browser_${Date.now()}`;
  const credentials = { username, password: 'Password123!', masterPassword: 'RealMaster123!' };
  const registration = await registerWithRetry(request, {
    ...credentials,
    email: `${username}@example.invalid`,
    skipSeeding: true,
  });
  expect(registration.status()).toBe(201);
  await loginAs(page, credentials);
  await api(page, '/users/me/base-currency', { baseCurrency: 'EUR' }, 'PUT');
});

async function fixtures(page: Page) {
  const account = await api(
    page,
    '/accounts',
    {
      name: 'Audit cash',
      type: 'CHECKING',
      currency: 'EUR',
      initialBalance: 1000,
      openingDate: month(-3),
    },
    'POST'
  );
  const property = await api(
    page,
    '/real-estate',
    {
      name: 'Audit property',
      propertyType: 'RESIDENTIAL',
      address: 'Test address',
      purchasePrice: 1000,
      currentValue: 1000,
      purchaseDate: month(-3),
      currency: 'EUR',
    },
    'POST'
  );
  return { account, property };
}

async function expenseForm(page: Page, date: string, amount: string) {
  await page.goto('/transactions');
  await page
    .getByRole('button', { name: /add transaction/i })
    .first()
    .click();
  const dialog = page.getByRole('dialog');
  await dialog.locator('select[name="type"]').selectOption('EXPENSE');
  await dialog.locator('button[role="combobox"]').first().click();
  await page.getByRole('option', { name: /Audit cash/ }).click();
  await dialog.locator('#amount').fill(amount);
  const [year, m, day] = date.split('-');
  await dialog.locator('#date').fill(`${m}/${day}/${year}`);
  return dialog;
}

test('dated improvements entered through the form preserve wealth in either date order', async ({
  page,
}) => {
  const { property } = await fixtures(page);
  for (const [date, amount] of [
    [month(-1), '200'],
    [month(-2), '100'],
  ]) {
    const dialog = await expenseForm(page, date, amount);
    await dialog.locator('#movementType').selectOption('CAPITAL_IMPROVEMENT');
    await dialog.locator('#movement-realEstateId').selectOption(String(property.id));
    await dialog.getByRole('button', { name: /^create transaction$/i }).click();
    await expect(dialog).not.toBeVisible();
  }
  const rows = await api(
    page,
    `/dashboard/networth-history?startDate=${month(-2)}&endDate=${month(-1)}&recalculate=true`
  );
  expect(rows).toHaveLength(2);
  for (const row of rows) expect(row.netWorth).toBe(2000);
  await page.goto('/dashboard');
  await expect(page.getByText('2,000.00', { exact: false }).first()).toBeVisible();
});

test('property editing refreshes a warmed dashboard and the backing asset', async ({ page }) => {
  const { property } = await fixtures(page);
  await api(page, '/dashboard/summary');
  await page.goto('/real-estate');
  await page.getByRole('button', { name: /edit property/i }).click();
  const dialog = page.getByRole('dialog');
  await dialog.locator('#currentValue').fill('1500');
  await dialog.getByRole('button', { name: /update|save/i }).click();
  await expect(dialog).not.toBeVisible();
  expect((await api(page, `/assets/${property.assetId}`)).currentPrice).toBe(1500);
  expect((await api(page, '/dashboard/summary')).netWorth.netWorth).toBe(2500);
  await page.goto('/dashboard');
  await expect(page.getByText('2,500.00', { exact: false }).first()).toBeVisible();
});

test('closing an account through its action preserves earlier history', async ({ page }) => {
  await fixtures(page);
  await page.goto('/accounts');
  await page.getByRole('button', { name: /close account/i }).click();
  await page
    .getByRole('dialog')
    .getByRole('button', { name: /close account/i })
    .click();
  await expect(page.getByRole('dialog')).not.toBeVisible();
  const rows = await api(
    page,
    `/dashboard/networth-history?startDate=${month(-3)}&endDate=${month(-3)}&recalculate=true`
  );
  expect(rows[0].netWorth).toBe(2000);
  expect((await api(page, '/dashboard/summary')).netWorth.netWorth).toBe(1000);
});

test('a repayment before origination is rejected through the transaction form', async ({
  page,
}) => {
  const { account } = await fixtures(page);
  const loan = await api(
    page,
    '/liabilities',
    {
      name: 'Audit loan',
      type: 'PERSONAL_LOAN',
      principal: 1000,
      currentBalance: 1000,
      currency: 'EUR',
      startDate: month(-1),
      interestRate: 0,
    },
    'POST'
  );
  const dialog = await expenseForm(page, month(-2), '100');
  await dialog
    .locator('label[for="liabilityId"]')
    .locator('../..')
    .getByRole('combobox')
    .first()
    .click();
  await page.getByRole('option', { name: /Audit loan/ }).click();
  await dialog.getByRole('checkbox', { name: /apply split/i }).uncheck();
  await dialog.locator('#principalAmount').fill('100');
  const rejected = page.waitForResponse(
    r => r.url().endsWith('/transactions') && r.request().method() === 'POST'
  );
  await dialog.getByRole('button', { name: /^create transaction$/i }).click();
  expect((await rejected).status()).toBe(400);
  await expect(dialog).toBeVisible();
  expect((await api(page, `/accounts/${account.id}`)).ownBalance).toBe(1000);
  expect((await api(page, `/liabilities/${loan.id}`)).currentBalance).toBe(1000);
});
