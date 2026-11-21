import { test, expect, type Page } from '@playwright/test';
import { registerWithRetry, signIn } from './helpers/auth';

test.use({ timezoneId: 'Europe/Paris' });
let today: string;

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
  testInfo.setTimeout(testInfo.timeout + 60_000);
  const registration = await registerWithRetry(request, {
    ...credentials,
    email: `${username}@example.invalid`,
    skipSeeding: !testInfo.title.includes('French seeded'),
  });
  expect(registration.status(), await registration.text()).toBe(201);
  await signIn(page, credentials);
  await page.waitForURL('**/onboarding');
  const summary = await api(page, '/dashboard/summary');
  today = summary.snapshotDate;
  expect(today).toMatch(/^\d{4}-\d{2}-\d{2}$/);
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
  const displayedToday = today.split('-').reverse().join('/');
  await expect(
    page.getByText(`Votre aperçu financier au ${displayedToday}`, { exact: true })
  ).toBeVisible();
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
  await page.locator(`#transaction-${transaction.id}`).hover();
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

test('OFX confirmation accepts sub-cent history and preserves its exact ledger value', async ({
  page,
}) => {
  await completeOnboarding(page);
  const account = await accountFixture(page);
  await api(
    page,
    '/transactions',
    {
      accountId: account.id,
      type: 'EXPENSE',
      amount: '12.34005683',
      currency: 'EUR',
      date: '2026-01-02',
      description: 'Historical FX movement',
    },
    'POST'
  );
  await page.goto('/import');
  await page.locator('input[type="file"]').setInputFiles({
    name: 'statement.ofx',
    mimeType: 'application/x-ofx',
    buffer: Buffer.from(`<?xml version="1.0"?>
      <OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><CURDEF>EUR</CURDEF>
      <BANKACCTFROM><ACCTID>CHECKING</ACCTID></BANKACCTFROM>
      <BANKTRANLIST><STMTTRN><TRNTYPE>CREDIT</TRNTYPE><DTPOSTED>20260202</DTPOSTED>
      <TRNAMT>10.20</TRNAMT><FITID>statement-credit</FITID><NAME>Deposit</NAME>
      </STMTTRN></BANKTRANLIST><LEDGERBAL><BALAMT>997.86</BALAMT>
      <DTASOF>20260202</DTASOF></LEDGERBAL></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>`),
  });
  await page.getByRole('button', { name: /^upload file$/i }).click();
  await page.locator('select').selectOption(String(account.id));
  const next = page.getByRole('button', { name: /^next$/i });
  await next.click();
  await expect(page.getByRole('table', { name: 'Review Transactions' })).toContainText('Deposit');
  await next.click();
  await page.getByRole('button', { name: /^confirm import$/i }).click();
  await expect
    .poll(async () => (await api(page, `/accounts/${account.id}`)).balance)
    .toBe(997.85994317);
});

test('import review saves transaction tags and both split lines through confirmation', async ({
  page,
}, testInfo) => {
  await completeOnboarding(page);
  const account = await accountFixture(page);
  await page.goto('/import');
  await page.locator('input[type="file"]').setInputFiles({
    name: 'fare.csv',
    mimeType: 'text/csv',
    buffer: Buffer.from(
      [
        'Date;Payee;Amount;Tags;Memo;Description;Category',
        `${today};Travel desk;-10.99;Receipt;DU 071026 TRAIN;FACTURE CARTE;Transport`,
        `${today};FACTURE CARTE;-5.10;;DU 071026 METRO;FACTURE CARTE;Transport:Transports en commun`,
        `${today};FACTURE CARTE;-14.44;;Restaurant au centre de la ville, repas du soir avec reçu;FACTURE CARTE;Alimentation:Restaurant`,
      ].join('\n')
    ),
  });
  await page.getByRole('button', { name: /^upload file$/i }).click();
  await page.locator('select').selectOption(String(account.id));
  const next = page.getByRole('button', { name: /^next$/i });
  await next.click();
  const table = page.getByRole('table', { name: 'Review Transactions' });
  const fare = table.getByRole('row', { name: /Travel desk/ });
  for (const width of [1848, 1280, 820, 390, 320]) {
    await page.setViewportSize({ width, height: width > 900 ? 900 : 844 });
    await table.scrollIntoViewIfNeeded();
    expect(
      await table
        .locator('..')
        .evaluate(scroller => scroller.scrollWidth <= scroller.clientWidth + 1)
    ).toBe(true);
    const bounds = await fare.evaluate(row => {
      const rects = Array.from(row.querySelectorAll('td,button'), el => el.getBoundingClientRect());
      return rects.every(
        rect => rect.width === 0 || (rect.left >= 0 && rect.right <= window.innerWidth + 1)
      );
    });
    expect(bounds).toBe(true);
    if ([1848, 820, 390].includes(width)) {
      await testInfo.attach(`ledger-${width}`, {
        body: await table.screenshot(),
        contentType: 'image/png',
      });
    }
  }
  await page.setViewportSize({ width: 1280, height: 800 });
  await fare.getByRole('button', { name: 'Tags and splits' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toContainText('Travel desk · DU 071026 TRAIN');
  await expect(dialog.getByText('Receipt', { exact: true })).toBeVisible();
  await dialog.getByRole('button', { name: 'Split transaction' }).click();
  expect((await dialog.boundingBox())!.height).toBeLessThan(520);
  await testInfo.attach('compact-split', {
    body: await dialog.screenshot(),
    contentType: 'image/png',
  });
  await dialog.getByRole('textbox', { name: 'Split 1 amount' }).fill('7.25');
  const save = dialog.getByRole('button', { name: 'Save', exact: true });
  await expect(save).toBeDisabled();
  await dialog.getByRole('textbox', { name: 'Split 2 amount' }).fill('3.74');
  await dialog.getByRole('combobox', { name: 'Split 1 category' }).fill('Transport');
  await dialog.getByRole('button', { name: 'Notes and tags for split 1' }).click();
  await dialog.getByRole('textbox', { name: 'Split 1 tags' }).fill('Work trip');
  await dialog.getByRole('textbox', { name: 'Split 1 description' }).fill('Fare');
  await dialog.getByRole('combobox', { name: 'Split 2 category' }).fill('Food');
  await dialog.getByRole('button', { name: 'Notes and tags for split 2' }).click();
  await dialog.getByRole('textbox', { name: 'Split 2 tags' }).fill('Personal');
  await dialog.getByRole('textbox', { name: 'Transaction tags' }).fill('Reviewed');
  await testInfo.attach('desktop-review', {
    body: await page.screenshot(),
    contentType: 'image/png',
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(save).toBeInViewport();
  const fits = await dialog.evaluate(node => node.scrollWidth <= node.clientWidth);
  expect(fits).toBe(true);
  await testInfo.attach('mobile-review', {
    body: await page.screenshot(),
    contentType: 'image/png',
  });
  await save.click();
  await expect(page.getByRole('row', { name: /Travel desk/ })).toContainText('Reviewed');
  await next.click();
  await page.getByRole('button', { name: /^confirm import$/i }).click();
  await expect.poll(async () => (await api(page, `/accounts/${account.id}`)).balance).toBe(969.47);
  const { content } = await api(page, `/transactions/search?accountId=${account.id}`);
  expect(content).toHaveLength(3);
  const booked = content.find((row: { payee: string }) => row.payee === 'Travel desk');
  expect(booked.tags).toBe('Receipt,Reviewed');
  const splits = await api(page, `/transactions/${booked.id}/splits`);
  expect(splits).toMatchObject([
    { amount: 7.25, categoryName: 'Transport', description: 'Fare', tags: ['Work trip'] },
    { amount: 3.74, categoryName: 'Food', tags: ['Personal'] },
  ]);
  const manual = await api(
    page,
    '/transactions',
    {
      accountId: account.id,
      type: 'EXPENSE',
      amount: '2',
      currency: 'EUR',
      date: today,
      description: 'Separate manual entry',
    },
    'POST'
  );
  await page.getByRole('button', { name: 'View Transactions' }).click();
  await expect(page).toHaveURL(/\/transactions\?importSessionId=\d+$/);
  const importId = new URL(page.url()).searchParams.get('importSessionId');
  const removeFilter = page.getByRole('button', { name: `Remove filter: Import #${importId}` });
  await expect(removeFilter).toBeVisible();
  await expect(page.locator(`#transaction-${booked.id}`)).toBeVisible();
  await expect(page.locator(`#transaction-${manual.id}`)).toHaveCount(0);
  const summary = await api(page, `/transactions/summary?importSessionId=${importId}`);
  expect(summary.transactionCount).toBe(3);
  await page.reload();
  await expect(removeFilter).toBeVisible();
  await removeFilter.click();
  await expect(page.locator(`#transaction-${manual.id}`)).toBeVisible();
});

test('multi-file review keeps the source, notes and actions readable on a narrow screen', async ({
  page,
}) => {
  await completeOnboarding(page);
  await page.goto('/import');
  const reference = 'RECEIPT' + '1234567890'.repeat(8);
  const names = ['checking-statement-for-the-complete-financial-period.csv', 'savings.csv'];
  await page.locator('input[type="file"]').setInputFiles(
    names.map((name, index) => ({
      name,
      mimeType: 'text/csv',
      buffer: Buffer.from(
        `Date;Payee;Amount;Memo;Description\n${today};Shop ${index};-5.10;${reference};FACTURE CARTE\n`
      ),
    }))
  );
  await page.getByRole('button', { name: /^start import$/i }).click();
  await page.getByRole('button', { name: /^next$/i }).click();
  const table = page.getByRole('table', { name: 'Review Transactions' });
  for (const width of [1500, 320]) {
    await page.setViewportSize({ width, height: 844 });
    const row = table.getByRole('row', { name: /Shop 0/ });
    await expect(row.getByText(names[0], { exact: true })).toBeVisible();
    await expect(row.getByText(reference, { exact: true })).toBeVisible();
    expect(
      await table.locator('..').evaluate(node => node.scrollWidth <= node.clientWidth + 1)
    ).toBe(true);
    await row.getByRole('button', { name: 'Edit transaction' }).click();
    await expect(row.getByRole('textbox', { name: 'Notes' })).toHaveValue(reference);
    await row.getByTitle('Cancel').click();
  }
});

test('repairing an unbalanced QIF split in review makes the row importable', async ({ page }) => {
  await completeOnboarding(page);
  const account = await accountFixture(page);
  await page.goto('/import');
  await page.locator('input[type="file"]').setInputFiles({
    name: 'parts.qif',
    mimeType: 'application/qif',
    buffer: Buffer.from(
      '!Type:Bank\nD01/02/2026\nT-25.50\nPMerchant\nSFood\n$-20\nSTransport\n$-2\n^\n'
    ),
  });
  await page.getByRole('button', { name: /^upload file$/i }).click();
  await page.locator('select').selectOption(String(account.id));
  const next = page.getByRole('button', { name: /^next$/i });
  await next.click();
  await page.getByRole('button', { name: 'Tags and splits' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByRole('textbox', { name: 'Split 2 amount' }).fill('5.50');
  await dialog.getByRole('button', { name: 'Save', exact: true }).click();
  await next.click();
  await page.getByRole('button', { name: /^confirm import$/i }).click();
  await expect.poll(async () => (await api(page, `/accounts/${account.id}`)).balance).toBe(974.5);
});

test('clear all dismisses system notifications and every triggered budget alert', async ({
  page,
}, testInfo) => {
  await completeOnboarding(page);
  const account = await accountFixture(page);
  const category = await api(page, '/categories', { name: 'Panel test' }, 'POST');
  const budget = await api(
    page,
    '/budgets',
    {
      categoryId: category.id,
      amount: '100',
      currency: 'EUR',
      period: 'MONTHLY',
      startDate: today.slice(0, 7) + '-01',
      endDate: today.slice(0, 4) + '-12-31',
      rollover: false,
    },
    'POST'
  );
  await api(
    page,
    '/transactions',
    {
      accountId: account.id,
      categoryId: category.id,
      amount: '90',
      currency: 'EUR',
      type: 'EXPENSE',
      date: today,
    },
    'POST'
  );
  for (const threshold of [11, 12, 13, 14, 15, 16, 17]) {
    await api(page, `/budgets/alerts?budgetId=${budget.id}`, { threshold }, 'POST');
  }
  const budgetCount = await api(page, '/budgets/alerts/unread/count');
  expect(budgetCount).toBeGreaterThanOrEqual(7);
  await page.reload();
  const bell = page.getByRole('button', { name: /^Notifications \(/ });
  await bell.click();
  const panel = page.getByRole('region', { name: 'Notifications' });
  const clear = panel.getByRole('button', { name: 'Clear all' });
  const balanceNotification = panel.getByRole('button', {
    name: 'Low Account Balance',
    exact: true,
  });
  await expect(panel.getByRole('button', { name: /^Budget alert:/ })).toHaveCount(5);
  await expect(balanceNotification).toBeVisible();
  for (const width of [1280, 375]) {
    await page.setViewportSize({ width, height: 800 });
    await expect(clear).toBeInViewport();
    await testInfo.attach(`notifications-${width}`, {
      body: await panel.screenshot(),
      contentType: 'image/png',
    });
  }
  const unread = Number((await bell.getAttribute('aria-label'))?.match(/\d+/)?.[0]);
  const dismissBalance = panel.getByRole('button', { name: 'Dismiss Low Account Balance' });
  await dismissBalance.press('Enter');
  await expect(balanceNotification).toHaveCount(0);
  await expect(bell).toHaveAccessibleName(`Notifications (${unread - 1} unread)`);
  await page.reload();
  await bell.click();
  await expect(panel.getByRole('button', { name: /^Budget alert:/ })).toHaveCount(5);
  await expect(dismissBalance).toHaveCount(0);

  const budgetDismissals = panel.getByRole('button', { name: /^Dismiss .+ alert at / });
  const label = await budgetDismissals.first().getAttribute('aria-label');
  const dismissBudget = panel.getByRole('button', { name: label!, exact: true });
  await page.route('**/budgets/alerts/*/read', route => route.fulfill({ status: 503 }), {
    times: 1,
  });
  await dismissBudget.click();
  await expect(panel.getByRole('alert')).toHaveText('Couldn’t dismiss this alert. Try again.');
  await dismissBudget.click();
  await expect(dismissBudget).toHaveCount(0);
  await expect(bell).toHaveAccessibleName(`Notifications (${unread - 2} unread)`);
  expect(await api(page, '/budgets/alerts/unread/count')).toBe(budgetCount - 1);
  await expect(budgetDismissals).toHaveCount(5);
  await page.route('**/budgets/alerts/read-all', route => route.fulfill({ status: 503 }), {
    times: 1,
  });
  await clear.press('Enter');
  await expect(panel.getByRole('alert')).toHaveText('Couldn’t clear budget alerts. Try again.');
  await expect(panel.getByRole('button', { name: /^Budget alert:/ })).toHaveCount(5);
  await expect(balanceNotification).toHaveCount(0);
  await clear.press('Enter');
  await expect(panel.getByRole('status')).toHaveText('No new notifications');
  await expect(bell).toHaveAccessibleName('Notifications (0 unread)');
  expect(await api(page, '/budgets/alerts/unread/count')).toBe(0);
  await page.reload();
  await bell.click();
  await expect(panel.getByRole('status')).toHaveText('No new notifications');
  await expect(clear).toHaveCount(0);
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
  const savedCategory = page
    .getByRole('listitem')
    .filter({ has: page.getByText('UX fitness', { exact: true }) });
  await expect(savedCategory).toBeVisible();
  await expect(savedCategory.getByText('🏋️', { exact: true })).toBeVisible();
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
  expect(compound.finalBalance).toBeCloseTo(16470.09, 2);
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
  const salaryFlow = page
    .getByRole('group', { name: 'Diagramme des flux de trésorerie', exact: true })
    .getByRole('button', { name: 'Voir les transactions Salaire', exact: true });
  await expect(salaryFlow).toBeVisible();
  await expect(salaryFlow.locator('text').first()).toContainText('Salaire');
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
  expect(flatten(tree).find(category => category.id === healthcare.id)?.totalAmount).toBe(-100);
  const payees = await api(page, '/payees/system');
  expect(payees.some((payee: { name: string }) => payee.name === 'Loan Payment Test')).toBe(false);
});
