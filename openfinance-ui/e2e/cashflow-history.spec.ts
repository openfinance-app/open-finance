import { expect, test, type Page } from '@playwright/test';
import { loginAs, registerWithRetry } from './helpers/auth';

async function api(page: Page, path: string, body: unknown, method = 'POST') {
  const result = await page.evaluate(
    async ({ path, body, method }) => {
      const response = await fetch(`/api/v1${path}`, {
        method,
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${localStorage.getItem('auth_token') || sessionStorage.getItem('auth_token')}`,
          'X-Encryption-Session': sessionStorage.getItem('encryption_session') || '',
        },
        body: JSON.stringify(body),
      });
      return { status: response.status, text: await response.text() };
    },
    { path, body, method }
  );
  expect(result.status, result.text).toBeLessThan(300);
  return JSON.parse(result.text);
}

test('cash flow calendar, monthly and yearly split views work on desktop and mobile', async ({
  page,
  request,
}, testInfo) => {
  const username = `cashflow_${Date.now()}`;
  const credentials = { username, password: 'Password123!', masterPassword: 'RealMaster123!' };
  const response = await registerWithRetry(request, {
    ...credentials,
    email: `${username}@example.invalid`,
    skipSeeding: true,
  });
  expect(response.status()).toBe(201);
  await loginAs(page, credentials);
  await api(page, '/users/me/base-currency', { baseCurrency: 'EUR' }, 'PUT');
  const year = new Date().getFullYear();
  const cash = await api(page, '/accounts', {
    name: 'Cash flow test',
    type: 'CHECKING',
    currency: 'EUR',
    initialBalance: 10000,
    openingDate: `${year - 1}-01-01`,
  });
  for (const [type, amount, date] of [
    ['INCOME', 1234.56, `${year}-02-01`],
    ['EXPENSE', 1500, `${year}-02-28`],
    ['INCOME', 2000, `${year - 1}-12-31`],
  ]) {
    await api(page, '/transactions', { accountId: cash.id, type, amount, date, currency: 'EUR' });
  }
  await page.reload();
  const title = page.getByRole('heading', { name: 'Cash Flow History', exact: true });
  const card = title.locator('../../..');
  await card.scrollIntoViewIfNeeded();
  const selector = card.getByRole('combobox', { name: 'Cash flow grouping' });
  await expect(selector).toHaveValue('DAY');
  await expect(card.getByRole('button', { name: /^View transactions for/ })).toHaveCount(
    new Date(year, new Date().getMonth() + 1, 0).getDate()
  );

  await selector.selectOption('MONTH');
  const february = card.getByRole('button', { name: `View transactions for February ${year}` });
  await expect(february).toContainText('1,234.56');
  await expect(february).toContainText('1,500.00');
  await expect(february).toContainText('-€265.44');
  const chart = card.getByRole('figure', { name: 'Income, expenses and net cash flow' });
  await expect(chart.locator('.recharts-bar')).toHaveCount(2);
  await expect(chart.locator('.recharts-line')).toHaveCount(1);
  const tileBox = await february.boundingBox();
  const chartBox = await chart.boundingBox();
  expect(chartBox!.x).toBeGreaterThan(tileBox!.x);
  const decemberBox = await card
    .getByRole('button', { name: `View transactions for December ${year}` })
    .boundingBox();
  const cardBox = await card.boundingBox();
  expect(decemberBox!.y + decemberBox!.height).toBeLessThanOrEqual(
    cardBox!.y + cardBox!.height - 12
  );
  await card.screenshot({ path: testInfo.outputPath('cashflow-monthly-desktop.png') });

  await selector.selectOption('YEAR');
  await expect(card.getByRole('button', { name: /^View transactions for/ })).toHaveCount(10);
  const thisYear = card.getByRole('button', { name: `View transactions for ${year}`, exact: true });
  await expect(thisYear).toContainText('1,234.56');
  await expect(thisYear).toContainText('1,500.00');
  await card.screenshot({ path: testInfo.outputPath('cashflow-yearly-desktop.png') });
  await card.getByRole('button', { name: 'Previous 10 years' }).click();
  await expect(card.getByText(`${year - 19}–${year - 10}`)).toBeVisible();
  await card.getByRole('button', { name: 'Today' }).click();
  await expect(thisYear).toContainText('1,234.56');

  await page.setViewportSize({ width: 390, height: 844 });
  await selector.selectOption('MONTH');
  await expect(february).toContainText('-€265.44');
  await chart.scrollIntoViewIfNeeded();
  await expect(chart.locator('svg').first()).toBeVisible();
  expect(await card.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true
  );
  await card.screenshot({ path: testInfo.outputPath('cashflow-monthly-mobile.png') });
  await february.click();
  const lastDay = new Date(year, 2, 0).getDate();
  await expect(page).toHaveURL(
    new RegExp(`dateFrom=${year}-02-01&dateTo=${year}-02-${lastDay}&excludeTransfers=true`)
  );
});
