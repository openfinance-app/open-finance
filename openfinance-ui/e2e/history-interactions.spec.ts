import { test, expect, type Page } from '@playwright/test';

const account = {
  id: 1,
  userId: 999,
  name: 'Everyday account',
  type: 'CHECKING',
  currency: 'EUR',
  balance: 1200,
  ownBalance: 1200,
  openingBalance: 1000,
  openingDate: '2026-01-01',
  isActive: true,
  isInterestEnabled: false,
  createdAt: '2026-01-01T12:00:00Z',
};
const history = {
  id: 1,
  entityType: 'ACCOUNT',
  entityId: 1,
  entityLabel: account.name,
  operationType: 'UPDATE',
  createdAt: '2026-11-08T20:30:00Z',
  canUndo: true,
  canRedo: false,
  affectedRecords: 2,
  changedFieldsJson: JSON.stringify({ name: { before: 'Current account', after: account.name } }),
};
const records = [
  {
    recordType: 'accounts',
    recordId: '1',
    entityLabel: account.name,
    operationType: 'UPDATE',
    context: { currency: 'EUR', name: account.name },
    fields: [{ field: 'name', before: 'Current account', after: account.name }],
  },
  {
    recordType: 'interest_rate_variations',
    recordId: '2',
    operationType: 'UPDATE',
    context: { account_id: account.name, valid_from: '2026-01-01' },
    fields: [
      { field: 'tax_rate', before: '30', after: '31.4' },
      { field: 'notes', before: null, after: 'Updated withholding for this account' },
    ],
  },
];

function paged(content: unknown[]) {
  return {
    content,
    totalElements: content.length,
    totalPages: content.length ? 1 : 0,
    number: 0,
    size: 20,
  };
}

async function fixture(page: Page): Promise<void> {
  await page.addInitScript(() => {
    localStorage.setItem('auth_token', 'interface-fixture');
    localStorage.setItem(
      'auth_user',
      JSON.stringify({
        id: 999,
        username: 'Interface review',
        email: 'review@example.invalid',
        baseCurrency: 'EUR',
        onboardingCompleted: true,
      })
    );
    localStorage.setItem('openfinance_language', 'en');
    sessionStorage.setItem('encryption_session', 'interface-fixture');
  });
  // Synthetic responses keep these interaction tests independent of personal financial data.
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname;
    let data: unknown = [];
    if (path.endsWith('/users/me/settings'))
      data = { userId: 999, language: 'en', numberFormat: 'en-US', theme: 'dark' };
    if (path.endsWith('/users/me/base-currency')) data = { baseCurrency: 'EUR' };
    if (path.endsWith('/history')) data = paged([history]);
    if (path.endsWith('/history/1/details')) data = paged(records);
    if (path.endsWith('/accounts')) data = [account];
    if (path.endsWith('/accounts/search')) data = paged([account]);
    if (path.endsWith('/accounts/1')) data = account;
    if (path.endsWith('/accounts/1/balance-history'))
      data = [{ date: '2026-11-01', balance: 1200, currency: 'EUR' }];
    if (path.endsWith('/transactions/search')) data = paged([]);
    if (path.endsWith('/currencies'))
      data = [{ code: 'EUR', name: 'Euro', symbol: '€', type: 'FIAT', active: true }];
    await route.fulfill({ json: data });
  });
}

test.beforeEach(async ({ page }) => fixture(page));

test('selecting card text keeps the card closed; normal clicks still open and animate it', async ({
  page,
}) => {
  await page.goto('/accounts');
  const heading = page.getByRole('heading', { name: account.name, exact: true });
  await expect(heading).toBeVisible();
  const bounds = await heading.boundingBox();
  expect(bounds).not.toBeNull();
  await page.mouse.move(bounds!.x + 2, bounds!.y + bounds!.height / 2);
  await page.mouse.down();
  await page.mouse.move(bounds!.x + bounds!.width - 2, bounds!.y + bounds!.height / 2, {
    steps: 15,
  });
  await page.mouse.up();
  expect(await page.evaluate(() => window.getSelection()?.toString().length)).toBeGreaterThan(0);
  await expect(page.getByRole('dialog')).toHaveCount(0);

  await heading.click();
  const dialog = page.getByRole('dialog', { name: account.name, exact: true });
  await expect(dialog).toHaveAttribute('data-state', 'open');
  expect(await dialog.evaluate(el => getComputedStyle(el).animationName)).toBe('detail-panel-in');
  await expect(dialog).toHaveCSS('transform', 'none');
  await page.keyboard.press('Escape');
  await expect(dialog).toHaveAttribute('data-state', 'closed');
  await expect(dialog).toHaveCount(0);
  await heading.click();
  await expect(dialog).toBeVisible();
  await dialog.getByRole('button', { name: 'Close modal' }).click();
  await expect(dialog).toHaveCount(0);
});

test('reduced motion removes panel travel and still dismisses', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/accounts');
  await page.getByRole('heading', { name: account.name, exact: true }).click();
  const dialog = page.getByRole('dialog', { name: account.name, exact: true });
  await expect(dialog).toBeVisible();
  const animation = await dialog.evaluate(el => ({
    name: getComputedStyle(el).animationName,
    duration: getComputedStyle(el).animationDuration,
  }));
  expect(animation).toEqual({ name: 'detail-overlay-in', duration: '0.001s' });
  await page.keyboard.press('Escape');
  await expect(dialog).toHaveCount(0);
});

test('a nested shared dialog stays centered and dismisses without closing its details', async ({
  page,
}) => {
  await page.goto('/accounts');
  await page.getByRole('heading', { name: account.name, exact: true }).click();
  const details = page.getByRole('dialog', { name: account.name, exact: true });
  await details.getByRole('button', { name: 'Edit', exact: true }).click();
  const editor = page.getByRole('dialog', { name: 'Edit Account', exact: true });
  await expect(editor).toHaveCSS('transform', 'none');
  const bounds = await editor.boundingBox();
  expect(Math.abs(bounds!.x + bounds!.width / 2 - page.viewportSize()!.width / 2)).toBeLessThan(1);
  await page.keyboard.press('Escape');
  await expect(editor).toHaveCount(0);
  await expect(details).toBeVisible();
});

test('details fit the mobile viewport and dismiss normally', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 390, height: 900 });
  await page.goto('/accounts');
  await page.getByRole('heading', { name: account.name, exact: true }).click();
  const dialog = page.getByRole('dialog', { name: account.name, exact: true });
  await expect(dialog).toHaveCSS('transform', 'none');
  const bounds = await dialog.boundingBox();
  expect(bounds!.x).toBeGreaterThanOrEqual(0);
  expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(390);
  await page.screenshot({ path: testInfo.outputPath('account-details-mobile.png') });
  await dialog.getByRole('button', { name: 'Close modal' }).click();
  await expect(dialog).toHaveCount(0);
});

for (const width of [1280, 390]) {
  test(`history shows related changes and masks financial values at ${width}px`, async ({
    page,
  }, testInfo) => {
    await page.setViewportSize({ width, height: 900 });
    await page.goto('/history');
    await page.getByRole('button', { name: 'Show changes' }).click();
    await expect(page.getByText('Tax rate (%)', { exact: true })).toBeVisible();
    await expect(page.getByText('31.4', { exact: true })).toBeVisible();
    await expect(page.getByText('Updated withholding for this account')).toBeVisible();
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)
    ).toBe(true);
    await page.getByText('Tax rate (%)', { exact: true }).scrollIntoViewIfNeeded();
    const valueBounds = await page.getByText('31.4', { exact: true }).boundingBox();
    expect(valueBounds!.x).toBeGreaterThanOrEqual(0);
    expect(valueBounds!.x + valueBounds!.width).toBeLessThanOrEqual(width);
    await page.screenshot({ path: testInfo.outputPath(`history-${width}.png`), fullPage: true });
    // The global privacy control is in the desktop toolbar; its state also applies on mobile.
    if (width < 640) await page.setViewportSize({ width: 1280, height: 900 });
    await page.getByRole('button', { name: /hide amounts/i }).click();
    await page.setViewportSize({ width, height: 900 });
    await expect(page.getByText('31.4', { exact: true })).toHaveCount(0);
    await expect(page.getByText('Updated withholding for this account')).toBeVisible();
  });
}
