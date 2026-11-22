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

async function completeOnboarding(page: Page, language: 'en' | 'fr' = 'en') {
  await api(
    page,
    '/users/me/onboarding',
    {
      country: 'FR',
      baseCurrency: 'EUR',
      secondaryCurrency: '',
      language,
      dateFormat: 'DD/MM/YYYY',
      numberFormat: language === 'fr' ? '1 234,56' : '1,234.56',
      amountDisplayMode: 'base',
    },
    'POST'
  );
  await page.goto('/dashboard');
  await expect(
    page.getByRole('heading', {
      name: language === 'fr' ? 'Tableau de bord' : 'Dashboard',
      exact: true,
    })
  ).toBeVisible();
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

test('account description links open a new tab from the card and update while editing', async ({
  page,
  context,
}) => {
  await completeOnboarding(page);
  const receipt = 'https://receipts.example.org/statement';
  await context.route('https://receipts.example.org/**', route =>
    route.fulfill({ body: 'Statement' })
  );
  await api(
    page,
    '/accounts',
    {
      name: 'Receipt account',
      type: 'CHECKING',
      currency: 'EUR',
      initialBalance: 10,
      description: `Statement (${receipt}).`,
    },
    'POST'
  );
  await page.goto('/accounts');
  const [tab] = await Promise.all([
    page.waitForEvent('popup'),
    page.getByRole('link', { name: receipt }).click(),
  ]);
  await expect(tab).toHaveURL(receipt);
  expect(await tab.evaluate(() => window.opener === null)).toBe(true);
  await tab.close();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.getByRole('button', { name: 'Edit account', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Edit Account' });
  const field = dialog.getByRole('textbox', { name: 'Description' });
  await expect(field).toHaveValue(`Statement (${receipt}).`);
  await field.fill('New statement at www.example.fr/receipt.');
  await expect(dialog.getByRole('link', { name: receipt })).toHaveCount(0);
  await expect(dialog.getByRole('link', { name: 'www.example.fr/receipt' })).toHaveAttribute(
    'href',
    'https://www.example.fr/receipt'
  );
  await field.fill('Paid in cash.');
  await expect(dialog.getByRole('link')).toHaveCount(0);
});

test('locked capital accounts can be created in French and distinguished from available funds on the dashboard', async ({
  page,
}, testInfo) => {
  await completeOnboarding(page, 'fr');
  await accountFixture(page);
  await page.goto('/accounts');
  await page.getByRole('button', { name: 'Ajouter un compte', exact: true }).first().click();
  const form = page.getByRole('dialog', { name: 'Créer un compte' });
  await form.getByRole('textbox', { name: /^Nom du compte/ }).fill('Mon PEE');
  await form.getByLabel(/Type de compte/).selectOption({ label: 'Capital bloqué' });
  const currency = form.getByText('Devise *', { exact: true }).locator('..').getByRole('button');
  if (!(await currency.textContent())?.includes('EUR')) {
    await currency.click();
    await page.getByRole('option', { name: /^EUR / }).click();
  }
  await form.getByRole('textbox', { name: /^Solde initial/ }).fill('2400');
  await form.getByRole('button', { name: 'Créer le compte', exact: true }).click();
  await expect(page.getByText('Mon PEE', { exact: true })).toBeVisible();
  const accounts = await api(page, '/accounts');
  const pee = accounts.find((account: { name: string }) => account.name === 'Mon PEE');
  expect(pee.type).toBe('LOCKED_SAVINGS');
  await page.goto('/dashboard');
  const card = page.getByRole('region', { name: 'Disponibilité des fonds' });
  for (const width of [1600, 390]) {
    await page.setViewportSize({ width, height: 900 });
    await expect(card.getByText('Capital disponible')).toBeVisible();
    await expect(card.getByText('Capital bloqué')).toBeVisible();
    await expect(card.getByText('29,41%')).toBeVisible();
    await expect(card.getByText('70,59%')).toBeVisible();
    await expect(card.getByRole('meter', { name: 'Capital disponible' })).toHaveAttribute(
      'aria-valuenow',
      '29.41'
    );
    await expect(card.locator('dd').first()).toContainText(/1[\s,.]?000/);
    await expect(card.locator('dd').last()).toContainText(/2[\s,.]?400/);
    await expect.poll(() => card.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
    await testInfo.attach(`account-liquidity-${width}`, {
      body: await card.screenshot({ animations: 'disabled' }),
      contentType: 'image/png',
    });
  }
  for (const [label, match, excluded] of [
    ['Capital disponible', 'UX checking', 'Mon PEE'],
    ['Capital bloqué', 'Mon PEE', 'UX checking'],
  ]) {
    await card.getByRole('link', { name: label }).click();
    const filterPanel = page.getByRole('region', { name: 'Filtrer les résultats' });
    const chip = filterPanel.getByRole('button', { name: `Retirer le filtre : ${label}` });
    await expect(chip).toBeVisible();
    await expect(page.getByText(match, { exact: true })).toBeVisible();
    await expect(page.getByText(excluded, { exact: true })).toHaveCount(0);
    await chip.click();
    await expect(page.getByText(excluded, { exact: true })).toBeVisible();
    await page.goBack();
  }
  await api(
    page,
    `/accounts/${pee.id}`,
    {
      name: pee.name,
      type: 'INVESTMENT',
      currency: 'EUR',
      initialBalance: '2400',
      adjustBalance: false,
    },
    'PUT'
  );
  await page.reload();
  await expect(card.locator('dd').first()).toContainText(/3[\s,.]?400/);
  await expect(card.locator('dd').last()).toContainText(/0[.,]00/);
});

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

test('import review bulk corrections reach only the selected ledger transactions', async ({
  page,
}, testInfo) => {
  await completeOnboarding(page);
  const account = await accountFixture(page);
  await page.goto('/import');
  await page.locator('input[type="file"]').setInputFiles({
    name: 'commute.csv',
    mimeType: 'text/csv',
    buffer: Buffer.from(
      [
        'Date;Payee;Amount;Description;Memo',
        `${today};Railway;-2;Original rail fare;Ticket receipt`,
        `${today};Metro;-4;Original metro fare;Transit pass`,
        `${today};Refund;30;Refund from shop;Keep refund note`,
      ].join('\n')
    ),
  });
  await page.getByRole('button', { name: /^upload file$/i }).click();
  await page.locator('select').selectOption(String(account.id));
  const next = page.getByRole('button', { name: /^next$/i });
  await next.click();
  const table = page.getByRole('table', { name: 'Review Transactions' });
  for (const payee of ['Railway', 'Metro']) {
    await table
      .getByRole('row', { name: new RegExp(payee) })
      .getByRole('checkbox')
      .check();
  }
  const bulk = page.getByRole('region', { name: '2 selected' });
  const date = new Date(`${today}T12:00:00Z`);
  date.setUTCDate(date.getUTCDate() - 1);
  const isoDate = date.toISOString().slice(0, 10);
  await bulk.getByRole('textbox', { name: 'Description', exact: true }).fill('Work commute');
  const notes = 'November travel\nReceipt attached';
  await bulk.getByRole('textbox', { name: 'Notes', exact: true }).fill(notes);
  await bulk
    .getByRole('textbox', { name: 'Date', exact: true })
    .fill(isoDate.split('-').reverse().join('/'));
  const amount = bulk.getByRole('textbox', { name: 'Amount', exact: true });
  await expect(bulk.getByTitle('EUR')).toHaveText('€');
  await amount.fill('-9-5');
  await amount.press('Enter');
  await expect(amount).toHaveValue('-14');
  for (const width of [1280, 390, 320]) {
    await page.setViewportSize({ width, height: 844 });
    expect(await bulk.evaluate(node => node.scrollWidth <= node.clientWidth)).toBe(true);
    if (width !== 320) await bulk.screenshot({ path: testInfo.outputPath(`bulk-${width}.png`) });
  }
  await bulk.getByRole('button', { name: 'Apply to 2' }).click();
  await expect(table.getByText('Work commute', { exact: true })).toHaveCount(2);
  await table.getByRole('row', { name: /Metro/ }).getByRole('checkbox').check();
  const metro = page.getByRole('region', { name: '1 selected' });
  await metro.getByRole('checkbox', { name: 'Clear notes' }).check();
  await metro.getByRole('button', { name: 'Apply to 1' }).click();
  await next.click();
  await page.getByRole('button', { name: /^confirm import$/i }).click();
  await expect.poll(async () => (await api(page, `/accounts/${account.id}`)).balance).toBe(1002);
  const { content } = await api(page, `/transactions/search?accountId=${account.id}`);
  expect(content).toHaveLength(3);
  for (const payee of ['Railway', 'Metro']) {
    expect(content).toContainEqual(
      expect.objectContaining({
        payee,
        date: isoDate,
        description: 'Work commute',
        amount: 14,
        type: 'EXPENSE',
      })
    );
  }
  expect(content.find((tx: { payee: string }) => tx.payee === 'Railway').notes).toBe(notes);
  expect(content.find((tx: { payee: string }) => tx.payee === 'Metro').notes ?? null).toBeNull();
  expect(content).toContainEqual(
    expect.objectContaining({
      payee: 'Refund',
      date: today,
      description: 'Refund from shop',
      notes: 'Keep refund note',
      amount: 30,
      type: 'INCOME',
    })
  );
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
  await fare.getByRole('button', { name: 'Edit transaction' }).click();
  const editRow = table
    .getByRole('row')
    .filter({ has: page.getByRole('button', { name: 'Save', exact: true }) });
  const amount = editRow.getByRole('textbox', { name: 'Amount', exact: true });
  const dateInput = editRow.getByRole('textbox', { name: 'Date', exact: true });
  await page.setViewportSize({ width: 1848, height: 900 });
  const amountBox = (await amount.boundingBox())!;
  const dateBox = (await dateInput.boundingBox())!;
  expect(
    Math.abs(amountBox.y + amountBox.height / 2 - (dateBox.y + dateBox.height / 2))
  ).toBeLessThanOrEqual(1);
  const symbol = editRow.getByText('€', { exact: true });
  await expect(symbol).toBeVisible();
  await expect(amount).toHaveAccessibleDescription('EUR');
  await testInfo.attach('edit-amount-desktop', {
    body: await editRow.screenshot(),
    contentType: 'image/png',
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(symbol).toBeVisible();
  const description = table.getByRole('textbox', { name: 'Description', exact: true });
  await expect(description).toHaveValue('FACTURE CARTE');
  await description.fill('Train fare for the work trip');
  const yesterday = new Date(`${today}T12:00:00Z`);
  yesterday.setUTCDate(yesterday.getUTCDate() - 1);
  const bookedDate = yesterday.toISOString().slice(0, 10);
  await dateInput.fill(bookedDate.split('-').reverse().join('/'));
  await amount.fill('-10.99-2');
  await amount.press('Enter');
  await expect(amount).toHaveValue('-12.99');
  await table.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(fare).toContainText('Train fare for the work trip');
  await fare.getByRole('button', { name: 'Tags and splits' }).click();
  await dialog.getByRole('textbox', { name: 'Split 2 amount' }).fill('5.74');
  await save.click();
  await next.click();
  await page.getByRole('button', { name: /^confirm import$/i }).click();
  await expect.poll(async () => (await api(page, `/accounts/${account.id}`)).balance).toBe(967.47);
  const { content } = await api(page, `/transactions/search?accountId=${account.id}`);
  expect(content).toHaveLength(3);
  const booked = content.find((row: { payee: string }) => row.payee === 'Travel desk');
  expect(booked).toMatchObject({ date: bookedDate, amount: 12.99, type: 'EXPENSE' });
  expect(booked.description).toBe('Train fare for the work trip');
  expect(booked.notes).toBe('DU 071026 TRAIN');
  expect(booked.tags).toBe('Receipt,Reviewed');
  const splits = await api(page, `/transactions/${booked.id}/splits`);
  expect(splits).toMatchObject([
    { amount: 7.25, categoryName: 'Transport', description: 'Fare', tags: ['Work trip'] },
    { amount: 5.74, categoryName: 'Food', tags: ['Personal'] },
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
  const filters = page.getByRole('region', { name: 'Filter results' });
  const toggle = page.getByRole('button', { name: 'Filters', exact: true });
  const removeLabel = `Remove filter: Import #${importId}`;
  const removeFilter = filters.getByRole('button', { name: removeLabel });
  await expect(toggle).toHaveAttribute('aria-expanded', 'true');
  await expect(removeFilter).toBeVisible();
  await testInfo.attach('import-filter', {
    body: await filters.screenshot(),
    contentType: 'image/png',
  });
  await toggle.click();
  await expect(filters).toHaveCount(0);
  await expect(page.getByRole('button', { name: removeLabel })).toBeVisible();
  await toggle.click();
  await expect(page.locator(`#transaction-${booked.id}`)).toBeVisible();
  await expect(page.locator(`#transaction-${manual.id}`)).toHaveCount(0);
  const summary = await api(page, `/transactions/summary?importSessionId=${importId}`);
  expect(summary.transactionCount).toBe(3);
  await page.reload();
  await expect(removeFilter).toBeVisible();
  await removeFilter.click();
  await expect(page).not.toHaveURL(/importSessionId=/);
  await expect(page.locator(`#transaction-${manual.id}`)).toBeVisible();
});

test('list filter buttons stay highlighted when a filtered view is collapsed', async ({ page }) => {
  await completeOnboarding(page);
  const views = [
    ['/transactions', '#keyword'],
    ['/accounts', '#keyword'],
    ['/assets', '#keyword'],
    ['/budget', '#budget-keyword'],
    ['/liabilities', '#search'],
    ['/real-estate', '#keyword'],
    ['/recurring-transactions', '#search'],
  ];

  for (const [path, field] of views) {
    await test.step(path, async () => {
      await page.goto(path);
      const toggle = page.getByRole('button', { name: 'Filters', exact: true });
      await expect(toggle).toHaveAttribute('aria-expanded', 'false');
      const neutral = await toggle.evaluate(button => getComputedStyle(button).backgroundImage);
      await toggle.click();
      const highlighted = await toggle.evaluate(button => getComputedStyle(button).backgroundImage);
      expect(highlighted).not.toBe(neutral);
      const panelId = await toggle.getAttribute('aria-controls');
      const keyword = page.locator(`#${panelId} ${field}`);
      await keyword.fill('example');
      await expect(toggle).toHaveAccessibleDescription('Active filters');
      await toggle.click();
      await expect(toggle).toHaveCSS('background-image', highlighted);
      await toggle.click();
      await keyword.fill('');
      await expect(toggle).toHaveAccessibleDescription('');
      await toggle.click();
      await expect(toggle).toHaveCSS('background-image', neutral);
    });
  }
});

test('applied badges follow the primary controls on desktop and mobile', async ({
  page,
}, testInfo) => {
  await completeOnboarding(page);
  for (const width of [1280, 390]) {
    await page.setViewportSize({ width, height: 850 });
    for (const { path, lastControl, badges } of [
      {
        path: '/transactions?noCategory=1&noPayee=1&importSessionId=42',
        lastControl: 'This year',
        badges: [/no category/i, /no payee/i, /Import #42/],
      },
      {
        path: '/accounts?lowBalance=1',
        lastControl: 'Sort by',
        badges: [/low balance/i],
      },
    ]) {
      await page.goto(path);
      const panel = page.getByRole('region', { name: 'Filter results' });
      const row = panel.getByRole('group', { name: 'Active filters' });
      const buttons = row.getByRole('button');
      await expect(buttons).toHaveCount(badges.length);
      for (const [index, name] of badges.entries()) {
        await expect(buttons.nth(index)).toHaveAccessibleName(name);
      }
      const previous =
        lastControl === 'Sort by'
          ? panel.getByRole('combobox', { name: /sort by/i })
          : panel.getByRole('button', { name: /this year/i });
      const above = await previous.boundingBox();
      const applied = await row.boundingBox();
      const more = await panel.getByText('More filters', { exact: true }).boundingBox();
      expect(applied!.y).toBeGreaterThanOrEqual(above!.y + above!.height);
      expect(more!.y).toBeGreaterThanOrEqual(applied!.y + applied!.height);
      for (const button of await buttons.all()) {
        const bounds = await button.boundingBox();
        expect(bounds!.x).toBeGreaterThanOrEqual(0);
        expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(width);
      }
      await testInfo.attach(`${path.split('?')[0].slice(1)}-filters-${width}`, {
        body: await panel.screenshot(),
        contentType: 'image/png',
      });
      const toggle = page.getByRole('button', { name: 'Filters', exact: true });
      await toggle.click();
      const collapsed = page.getByRole('group', { name: 'Active filters' });
      await expect(collapsed.getByRole('button')).toHaveCount(badges.length);
      await collapsed.getByRole('button', { name: badges[badges.length - 1] }).click();
      await expect(collapsed.getByRole('button')).toHaveCount(badges.length - 1);
      await expect(toggle).toHaveAccessibleDescription(badges.length > 1 ? 'Active filters' : '');
    }
  }
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
  const closed = await api(
    page,
    '/accounts',
    {
      name: 'Closed low balance',
      type: 'CHECKING',
      currency: 'EUR',
      initialBalance: 0,
      openingDate: today,
    },
    'POST'
  );
  await api(page, `/accounts/${closed.id}/close`, undefined, 'POST');
  await page.goto('/accounts');
  await expect(page.getByText('Closed low balance', { exact: true }).first()).toBeVisible();
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
  await expect(page).toHaveURL(/lowBalance=1/);
  await expect(page.getByText('Minor checking', { exact: true }).first()).toBeVisible();
  await expect(page.getByText('Closed low balance', { exact: true })).toHaveCount(0);
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
