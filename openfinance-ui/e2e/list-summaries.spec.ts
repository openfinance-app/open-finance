import { expect, test, type Page } from '@playwright/test';
import { loginAs, registerWithRetry } from './helpers/auth';

async function api<T>(page: Page, path: string, body?: unknown): Promise<T> {
  const response = await page.evaluate(
    async ({ path, body }) => {
      const result = await fetch(`/api/v1${path}`, {
        method: body === undefined ? 'GET' : 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${localStorage.getItem('auth_token') || sessionStorage.getItem('auth_token')}`,
          'X-Encryption-Session': sessionStorage.getItem('encryption_session') || '',
        },
        body: body === undefined ? undefined : JSON.stringify(body),
      });
      return { status: result.status, text: await result.text() };
    },
    { path, body }
  );
  expect(response.status, response.text).toBeLessThan(300);
  return JSON.parse(response.text) as T;
}

for (const width of [1280, 390]) {
  test(`category and payee drilldowns, summaries and date editing at ${width}px`, async ({
    page,
    request,
  }, testInfo) => {
    testInfo.setTimeout(180_000);
    await page.setViewportSize({ width, height: 900 });
    const errors: string[] = [];
    page.on('pageerror', error => errors.push(error.message));
    const username = `summary_${width}_${Date.now()}`;
    const credentials = { username, password: 'Password123!', masterPassword: 'RealMaster123!' };
    const registered = await registerWithRetry(request, {
      ...credentials,
      email: `${username}@example.invalid`,
      skipSeeding: true,
    });
    expect(registered.status()).toBe(201);
    await loginAs(page, credentials);
    await api(page, '/users/me/onboarding', {
      country: 'FR',
      baseCurrency: 'EUR',
      secondaryCurrency: '',
      language: 'en',
      dateFormat: 'DD/MM/YYYY',
      numberFormat: '1,234.56',
      amountDisplayMode: 'base',
    });
    // Refresh the authenticated profile after provisioning preferences through the API.
    await page.evaluate(() => {
      localStorage.clear();
      sessionStorage.clear();
    });
    await loginAs(page, credentials);
    const account = await api<{ id: number }>(page, '/accounts', {
      name: 'Checking summary',
      type: 'CHECKING',
      currency: 'EUR',
      initialBalance: 0,
      openingDate: '2026-01-01',
    });
    await api(page, '/accounts', {
      name: 'Savings summary',
      type: 'SAVINGS',
      currency: 'EUR',
      initialBalance: 500,
      openingDate: '2026-01-01',
    });
    const parent = await api<{ id: number }>(page, '/categories', { name: 'Parent summary' });
    const child1 = await api<{ id: number }>(page, '/categories', {
      name: 'Child one',
      parentId: parent.id,
    });
    const child2 = await api<{ id: number }>(page, '/categories', {
      name: 'Child two',
      parentId: parent.id,
    });
    for (const [categoryId, amount, payee] of [
      [parent.id, 100, 'Exact merchant'],
      [child1.id, 400, 'Exact merchant'],
      [child2.id, 600, 'Exact merchant extra'],
    ] as const) {
      await api(page, '/transactions', {
        accountId: account.id,
        categoryId,
        amount,
        payee,
        currency: 'EUR',
        type: 'INCOME',
        date: '2026-10-01',
      });
    }
    for (let i = 0; i < 22; i++) {
      await api(page, '/transactions', {
        accountId: account.id,
        categoryId: child2.id,
        amount: 1,
        payee: 'Other merchant',
        currency: 'EUR',
        type: 'EXPENSE',
        date: '2026-10-02',
      });
    }
    await page.goto('/categories');
    await expect(page.getByTitle('Direct amount').filter({ hasText: '€100.00' })).toBeVisible();
    await expect(
      page.getByTitle('Including subcategories').filter({ hasText: '€1,078.00' })
    ).toBeVisible();
    await page.screenshot({
      path: `../target/browser-tests/categories-summary-${width}.png`,
      fullPage: true,
    });
    await page
      .getByRole('link', { name: 'View transactions including subcategories for Parent summary' })
      .click();
    const summary = page.getByRole('region', { name: 'Matching transactions' });
    await expect(summary.locator('dd').first()).toHaveText('25');
    await expect(summary.locator('dd').last()).toHaveText('€1,078.00');
    await summary.screenshot({
      path: `../target/browser-tests/transactions-summary-${width}.png`,
    });
    await page.getByRole('button', { name: 'Next page', exact: true }).click();
    await expect(summary.locator('dd').first()).toHaveText('25');
    await expect(summary.locator('dd').last()).toHaveText('€1,078.00');
    await page.goto('/categories');
    await page.getByRole('link', { name: 'View direct transactions for Parent summary' }).click();
    await expect(summary.locator('dd').first()).toHaveText('1');
    await expect(summary.locator('dd').last()).toHaveText('€100.00');
    await page.goto('/payees');
    await page
      .getByRole('link', { name: 'View transactions for Exact merchant', exact: true })
      .click();
    await expect(summary.locator('dd').first()).toHaveText('2');
    await expect(summary.locator('dd').last()).toHaveText('€500.00');
    await page.goto('/accounts');
    await expect(page.getByText('All accounts · EUR').locator('..')).toContainText('€1,578.00');
    await page.screenshot({
      path: `../target/browser-tests/accounts-summary-${width}.png`,
      fullPage: true,
    });

    await page.goto('/dashboard');
    await page
      .getByRole('group', { name: 'Time period' })
      .getByRole('button', { name: /Custom range/ })
      .click();
    const from = page.getByLabel('From', { exact: true });
    const to = page.getByLabel('To', { exact: true });
    await to.fill('31/12/2026');
    await to.press('Tab');
    await from.fill('18/10/2026');
    await from.evaluate((input: HTMLInputElement) => input.setSelectionRange(0, 1));
    await from.press('Backspace');
    await expect(from).toHaveValue('8/10/2026');
    expect(await from.evaluate((input: HTMLInputElement) => input.selectionStart)).toBe(0);
    await from.press('2');
    await expect(from).toHaveValue('28/10/2026');
    expect(await from.evaluate((input: HTMLInputElement) => input.selectionStart)).toBe(1);
    await page.keyboard.press('Escape');
    expect(errors).toEqual([]);
  });
}
