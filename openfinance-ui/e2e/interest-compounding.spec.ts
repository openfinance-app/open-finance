import { expect, test, type Page } from '@playwright/test';
import { loginAs, registerWithRetry } from './helpers/auth';

async function api<T>(page: Page, path: string, method = 'GET', body?: unknown): Promise<T> {
  return page.evaluate(
    async ({ path, method, body }) => {
      const response = await fetch(`/api/v1${path}`, {
        method,
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${localStorage.getItem('auth_token') || sessionStorage.getItem('auth_token')}`,
          'X-Encryption-Session': sessionStorage.getItem('encryption_session') || '',
        },
        body: body === undefined ? undefined : JSON.stringify(body),
      });
      if (!response.ok) throw new Error(`${method} ${path}: HTTP ${response.status}`);
      return response.json();
    },
    { path, method, body }
  );
}

test('form preview, saved account and dashboard share calendar compounding', async ({
  page,
  request,
}, testInfo) => {
  test.setTimeout(90_000);
  await page.setViewportSize({ width: 1280, height: 1000 });
  const username = `compound_${Date.now()}`;
  const credentials = { username, password: 'Password123!', masterPassword: 'RealMaster123!' };
  expect(
    (
      await registerWithRetry(request, {
        ...credentials,
        email: `${username}@example.invalid`,
        skipSeeding: true,
      })
    ).status()
  ).toBe(201);
  await loginAs(page, credentials);
  await api(page, '/users/me/onboarding', 'POST', {
    country: 'FR',
    baseCurrency: 'EUR',
    secondaryCurrency: '',
    language: 'en',
    dateFormat: 'DD/MM/YYYY',
    numberFormat: '1,234.56',
    amountDisplayMode: 'base',
  });
  await api(page, '/users/me/base-currency', 'PUT', { baseCurrency: 'EUR' });
  await page.goto('/accounts');
  await page.getByRole('button', { name: 'Add Account', exact: true }).first().click();
  await page
    .getByRole('textbox', { name: 'Account Name *', exact: true })
    .fill('Quarterly savings');
  await page.getByLabel('Account Type', { exact: false }).selectOption('SAVINGS');
  const currency = page
    .getByRole('dialog', { name: 'Create Account' })
    .getByText('Currency *', { exact: true })
    .locator('..')
    .getByRole('button');
  if (!(await currency.textContent())?.includes('EUR')) {
    await currency.click();
    await page.getByRole('option', { name: 'EUR Euro', exact: true }).click();
  }
  await page.getByRole('textbox', { name: /^Initial Balance/ }).fill('1000');
  const year = new Date().getFullYear();
  const expected = year % 4 === 0 ? 40.72 : 40.6;
  await page.getByRole('textbox', { name: 'Opening Date', exact: false }).fill(`01/01/${year}`);
  await page.getByRole('switch').click();
  await page.getByLabel('Compounding', { exact: true }).selectOption('QUARTERLY');
  await page.getByRole('textbox', { name: 'Interest Rate %', exact: true }).fill('4');
  await page.getByLabel('Compounding', { exact: true }).press('Tab');
  const preview = page.getByText(`Estimated return · ${year}`, { exact: true }).locator('../..');
  await expect(preview).toContainText(`€${expected.toFixed(2)}`);
  await preview.scrollIntoViewIfNeeded();
  await expect(preview).toBeInViewport();
  await page.screenshot({
    path: testInfo.outputPath('quarterly-form-preview.png'),
    fullPage: true,
  });
  await page.getByRole('button', { name: 'Create Account', exact: true }).click();
  await expect(page.getByText('Quarterly savings', { exact: true })).toBeVisible();
  const accounts = await api<{ id: number; name: string; ownBalance: number }[]>(page, '/accounts');
  const account = accounts.find(a => a.name === 'Quarterly savings')!;
  expect(account.ownBalance).toBe(1000);
  expect(
    (await api<{ estimate: number }>(page, `/accounts/${account.id}/interest-estimate`)).estimate
  ).toBe(expected);
  expect(
    (await api<{ totalProjected: number }>(page, '/dashboard/estimated-interest?period=YTD'))
      .totalProjected
  ).toBe(expected);
  await page.getByText('Quarterly savings', { exact: true }).click();
  await page.getByRole('button', { name: 'Interest', exact: true }).click();
  await expect(
    page.getByText('Interest Rate History', { exact: true }).locator('..').locator('..')
  ).toContainText(`€${expected.toFixed(2)}`);
  await page.screenshot({ path: testInfo.outputPath('quarterly-account.png'), fullPage: true });

  // Classify a real bank payment through the UI; the account balance must stay identical.
  const credit = await api<{ id: number }>(page, '/transactions', 'POST', {
    accountId: account.id,
    type: 'INCOME',
    currency: 'EUR',
    amount: '9.86',
    date: `${year}-04-01`,
    description: 'Bank interest credit',
  });
  await page.goto('/transactions');
  await page.locator(`#transaction-${credit.id}`).hover();
  await page.getByRole('button', { name: 'Edit transaction', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog
    .getByRole('checkbox', { name: 'Interest credited by the bank', exact: true })
    .check();
  await dialog.getByRole('button', { name: 'Update Transaction', exact: true }).click();
  await expect(dialog).not.toBeVisible();
  expect(
    (await api<{ movementType: string }>(page, `/transactions/${credit.id}`)).movementType
  ).toBe('INTEREST');
  expect((await api<{ ownBalance: number }>(page, `/accounts/${account.id}`)).ownBalance).toBe(
    1009.86
  );
  const corrected = (
    await api<{ estimate: number }>(page, `/accounts/${account.id}/interest-estimate`)
  ).estimate;
  expect(corrected).toBe(expected);
});
