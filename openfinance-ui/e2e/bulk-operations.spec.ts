import { expect, test, type Page } from '@playwright/test';
import { loginAs, registerWithRetry } from './helpers/auth';

test.setTimeout(180_000);

type RecordData = Record<string, unknown>;
type Api = (
  method: string,
  path: string,
  data?: RecordData,
  status?: number
) => Promise<RecordData>;
type Change = [field: string, mode: 'set' | 'clear', value?: string];
const today = new Date().toISOString().slice(0, 10);
const start = `${today.slice(0, 7)}-01`;

// Each test owns a new user. All business reads and writes use the real backend.
async function setup(page: Page): Promise<Api> {
  const username = `bulk_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 6)}`;
  const credentials = {
    username,
    email: `${username}@example.invalid`,
    password: 'BulkLogin123!',
    masterPassword: 'BulkMaster123!',
  };
  const registration = await registerWithRetry(page.request, { ...credentials, skipSeeding: true });
  expect(registration.status(), await registration.text()).toBe(201);
  await loginAs(page, credentials);
  const headers = await page.evaluate(() => {
    const result: Record<string, string> = {
      Authorization: `Bearer ${localStorage.getItem('auth_token') || sessionStorage.getItem('auth_token')}`,
    };
    const session = sessionStorage.getItem('encryption_session');
    if (session) result['X-Encryption-Session'] = session;
    return result;
  });
  return async (method, path, data, status = method === 'POST' ? 201 : 200) => {
    const response = await page.request.fetch(`/api/v1${path}`, { method, data, headers });
    expect(response.status(), `${method} ${path}: ${await response.text()}`).toBe(status);
    return response.status() === 204 ? {} : response.json();
  };
}

async function account(api: Api, name: string): Promise<RecordData> {
  return api('POST', '/accounts', {
    name,
    type: 'CHECKING',
    currency: 'EUR',
    initialBalance: 1000,
    openingDate: start,
  });
}

function movement(accountId: unknown, description: string): RecordData {
  return { accountId, description, amount: 10, currency: 'EUR', type: 'EXPENSE', date: today };
}

async function select(page: Page, names: string[]): Promise<void> {
  for (const name of names) {
    const box = page.getByRole('checkbox', { name: `Select ${name}`, exact: true });
    await box.focus();
    await box.check();
  }
}

async function selectVisible(page: Page): Promise<void> {
  const first = page.getByRole('checkbox', { name: /^Select / }).first();
  await first.focus();
  await first.check();
  await page.getByRole('checkbox', { name: 'Select visible', exact: true }).check();
}

async function apply(page: Page, changes: Change[], succeeded: number, failed = 0): Promise<void> {
  await page.getByRole('button', { name: 'Edit selected', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('button', { name: /^Apply to/ })).toBeDisabled();
  for (const [field, mode, value] of changes) {
    await dialog.getByLabel(`Action for ${field}`, { exact: true }).selectOption(mode);
    if (mode === 'set') {
      const control = dialog.getByLabel(field, { exact: true });
      await expect(control).toBeEnabled();
      if ((await control.evaluate(element => element.tagName)) === 'SELECT') {
        await control.selectOption(value ?? '');
      } else {
        await control.fill(value ?? '');
      }
    }
  }
  await dialog.getByRole('button', { name: /^Apply to/ }).click();
  await expect(dialog).toHaveCount(0);
  await expect(
    page.getByText(`${succeeded} completed · ${failed} failed`, { exact: true })
  ).toBeVisible();
}

async function removeSelected(page: Page, count: number): Promise<void> {
  const name = `Delete selected (${count})`;
  await page.getByRole('button', { name, exact: true }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Cancel', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByText(`${count} selected`, { exact: true })).toBeVisible();
  await page.getByRole('button', { name, exact: true }).click();
  await page.getByRole('dialog').getByRole('button', { name, exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByText(`${count} completed · 0 failed`, { exact: true })).toBeVisible();
}

test('all five bulk editors persist only chosen fields, including clear and false', async ({
  page,
}) => {
  const api = await setup(page);
  const bank = await api('POST', '/institutions', { name: 'Bulk bank', country: 'FR' });
  const category = await api('POST', '/categories', { name: 'Bulk category' });
  const accounts = [await account(api, 'Account A'), await account(api, 'Account B')];
  const assets: RecordData[] = [];
  const loans: RecordData[] = [];
  const transactions: RecordData[] = [];
  const payees: RecordData[] = [];
  for (const suffix of ['A', 'B']) {
    assets.push(
      await api('POST', '/assets', {
        name: `Asset ${suffix}`,
        type: 'STOCK',
        quantity: 2,
        purchasePrice: '1.123456789123456789',
        currentPrice: '12.123456789123456789',
        purchaseDate: start,
        currency: 'EUR',
        accountId: accounts[0].id,
      })
    );
    loans.push(
      await api('POST', '/liabilities', {
        name: `Loan ${suffix}`,
        type: 'PERSONAL_LOAN',
        principal: 1000,
        currentBalance: 1000,
        startDate: start,
        currency: 'EUR',
        interestRate: 0,
      })
    );
    transactions.push(
      await api('POST', '/transactions', movement(accounts[0].id, `Movement ${suffix}`))
    );
    payees.push(await api('POST', '/payees', { name: `Merchant ${suffix}` }));
  }

  await page.goto('/assets');
  await select(page, ['Asset A', 'Asset B']);
  await apply(
    page,
    [
      ['Notes', 'set', 'Shared note'],
      ['Linked account', 'clear'],
    ],
    2
  );
  for (const asset of assets) {
    const after = await api('GET', `/assets/${asset.id}`);
    expect(after).toMatchObject({
      notes: 'Shared note',
      accountId: null,
      name: asset.name,
      quantity: asset.quantity,
      currentPrice: asset.currentPrice,
      purchasePrice: asset.purchasePrice,
    });
    expect(after.exactValues).toMatchObject({
      purchasePrice: '1.123456789123456789',
      currentPrice: '12.123456789123456789',
    });
  }
  const beforeAccounts = await Promise.all(
    accounts.map(item => api('GET', `/accounts/${item.id}`))
  );
  await page.goto('/accounts');
  await select(page, ['Account A', 'Account B']);
  await apply(
    page,
    [
      ['Institution', 'set', String(bank.id)],
      ['Account type', 'set', 'SAVINGS'],
      ['Description', 'set', 'Shared purpose'],
    ],
    2
  );
  for (const before of beforeAccounts) {
    expect(await api('GET', `/accounts/${before.id}`)).toMatchObject({
      type: 'SAVINGS',
      institution: { id: bank.id },
      description: 'Shared purpose',
      balance: before.balance,
      ownBalance: before.ownBalance,
      openingDate: before.openingDate,
    });
  }
  await page.goto('/liabilities');
  await select(page, ['Loan A', 'Loan B']);
  await apply(
    page,
    [
      ['Institution', 'set', String(bank.id)],
      ['Notes', 'set', 'Shared loan note'],
    ],
    2
  );
  for (const loan of loans) {
    expect(await api('GET', `/liabilities/${loan.id}`)).toMatchObject({
      notes: 'Shared loan note',
      institution: { id: bank.id },
      principal: loan.principal,
      currentBalance: loan.currentBalance,
      startDate: loan.startDate,
    });
  }
  await page.goto('/transactions');
  await select(page, ['Movement A', 'Movement B']);
  await apply(
    page,
    [
      ['Category', 'set', String(category.id)],
      ['Notes', 'set', 'Shared note'],
      ['Reconciled', 'set', 'false'],
    ],
    2
  );
  for (const tx of transactions) {
    expect(await api('GET', `/transactions/${tx.id}`)).toMatchObject({
      categoryId: category.id,
      notes: 'Shared note',
      isReconciled: false,
      amount: tx.amount,
      description: tx.description,
      date: tx.date,
      accountId: tx.accountId,
    });
  }
  await page.goto('/payees');
  await select(page, ['Merchant A', 'Merchant B']);
  await apply(page, [['Category', 'set', String(category.id)]], 2);
  for (const payee of payees) {
    expect(await api('GET', `/payees/${payee.id}`)).toMatchObject({
      name: payee.name,
      categoryId: category.id,
    });
  }
  await select(page, ['Merchant A', 'Merchant B']);
  await apply(page, [['Category', 'clear']], 2);
  for (const payee of payees)
    expect((await api('GET', `/payees/${payee.id}`)).categoryId).toBeNull();
});

test('mixed category edits are atomic per record, retry failures, and delete a transfer once', async ({
  page,
}) => {
  const api = await setup(page);
  const source = await account(api, 'Source');
  const destination = await account(api, 'Destination');
  const category = await api('POST', '/categories', { name: 'Expenses' });
  const ordinary = await api('POST', '/transactions', movement(source.id, 'Ordinary'));
  const split = await api('POST', '/transactions', {
    ...movement(source.id, 'Split'),
    splits: [
      { categoryId: category.id, amount: 6 },
      { categoryId: category.id, amount: 4 },
    ],
  });
  await api('POST', '/transactions/transfer', {
    ...movement(source.id, 'Transfer'),
    type: 'TRANSFER',
    toAccountId: destination.id,
  });
  const before = await api('GET', `/transactions/${split.id}`);
  await page.goto('/transactions');
  await selectVisible(page);
  await apply(
    page,
    [
      ['Category', 'set', String(category.id)],
      ['Notes', 'set', 'First attempt'],
    ],
    1,
    3
  );
  expect((await api('GET', `/transactions/${ordinary.id}`)).notes).toBe('First attempt');
  expect((await api('GET', `/transactions/${split.id}`)).notes).toBe(before.notes);
  await expect(page.getByRole('checkbox', { name: 'Select Split', exact: true })).toBeChecked();
  await apply(page, [['Notes', 'set', 'Retry without category']], 3);
  expect(await api('GET', `/transactions/${split.id}`)).toMatchObject({
    notes: 'Retry without category',
    splits: before.splits,
  });

  const deletes: string[] = [];
  page.on('request', request => {
    if (request.method() === 'DELETE') deletes.push(request.url());
  });
  await selectVisible(page);
  await removeSelected(page, 4);
  expect(deletes).toHaveLength(3);
  expect((await api('GET', `/accounts/${source.id}`)).ownBalance).toBe(1000);
  expect((await api('GET', `/accounts/${destination.id}`)).ownBalance).toBe(1000);
});

test('bulk delete works in the remaining nine views and preserves dependencies', async ({
  page,
}) => {
  const api = await setup(page);
  const cash = await account(api, 'Cash');
  const expense = await api('POST', '/categories', { name: 'Expenses' });
  const other = await api('POST', '/categories', { name: 'Other expenses' });
  const pairs: { resource: string; route: string; records: RecordData[]; names: string[] }[] = [];
  const seeds: [string, RecordData][] = [
    [
      'assets',
      {
        type: 'STOCK',
        quantity: 1,
        purchasePrice: 10,
        currentPrice: 10,
        purchaseDate: start,
        currency: 'EUR',
      },
    ],
    [
      'real-estate',
      {
        propertyType: 'RESIDENTIAL',
        address: 'Synthetic address',
        purchasePrice: 1000,
        currentValue: 1000,
        purchaseDate: start,
        currency: 'EUR',
      },
    ],
    [
      'liabilities',
      {
        type: 'PERSONAL_LOAN',
        principal: 1000,
        currentBalance: 1000,
        startDate: start,
        currency: 'EUR',
        interestRate: 0,
      },
    ],
    ['payees', {}],
    [
      'recurring-transactions',
      {
        accountId: cash.id,
        type: 'EXPENSE',
        amount: 10,
        currency: 'EUR',
        frequency: 'MONTHLY',
        nextOccurrence: '2099-01-01',
      },
    ],
    [
      'transaction-rules',
      {
        conditions: [{ field: 'DESCRIPTION', operator: 'CONTAINS', value: 'Synthetic' }],
        actions: [{ actionType: 'ADD_TAG', actionValue: 'bulk' }],
      },
    ],
  ];
  for (const [resource, fields] of seeds) {
    const records: RecordData[] = [];
    const names = ['A', 'B'].map(suffix => `${resource} ${suffix}`);
    for (const name of names)
      records.push(await api('POST', `/${resource}`, { ...fields, name, description: name }));
    pairs.push({ resource, route: resource, records, names });
  }
  const budgets: RecordData[] = [];
  for (const category of [expense, other])
    budgets.push(
      await api('POST', '/budgets', {
        categoryId: category.id,
        amount: 100,
        currency: 'EUR',
        period: 'MONTHLY',
        startDate: start,
        endDate: new Date(Date.UTC(Number(today.slice(0, 4)), Number(today.slice(5, 7)), 0))
          .toISOString()
          .slice(0, 10),
        rollover: false,
      })
    );
  pairs.push({
    resource: 'budgets',
    route: 'budget',
    records: budgets,
    names: ['Expenses', 'Other expenses'],
  });
  pairs.push({
    resource: 'accounts',
    route: 'accounts',
    records: [await account(api, 'Delete A'), await account(api, 'Delete B')],
    names: ['Delete A', 'Delete B'],
  });
  for (const pair of pairs) {
    await page.goto(`/${pair.route}`);
    await select(page, pair.names);
    await removeSelected(page, 2);
    for (const record of pair.records) {
      if (pair.resource === 'real-estate') {
        expect((await api('GET', `/real-estate/${record.id}`)).isActive).toBe(false);
      } else await api('GET', `/${pair.resource}/${record.id}`, undefined, 404);
    }
  }
  const parent = await api('POST', '/categories', { name: 'Delete parent' });
  const child = await api('POST', '/categories', { name: 'Delete child', parentId: parent.id });
  await page.goto('/categories');
  await page.getByRole('button', { name: 'Expand all', exact: true }).click();
  await select(page, ['Delete parent', 'Delete child']);
  const deleted: string[] = [];
  page.on('request', request => {
    if (request.method() === 'DELETE') deleted.push(request.url());
  });
  await removeSelected(page, 2);
  expect(deleted[0]).toContain(`/categories/${child.id}`);
  expect(deleted[1]).toContain(`/categories/${parent.id}`);
  expect((await api('GET', `/accounts/${cash.id}`)).ownBalance).toBe(1000);
  expect((await api('GET', `/categories/${expense.id}`)).id).toBe(expense.id);
});

test('selection stays on the visible page and deleting its last record returns to a valid page', async ({
  page,
}) => {
  const api = await setup(page);
  const cash = await account(api, 'Cash');
  for (let index = 0; index < 21; index++) {
    await api('POST', '/transactions', movement(cash.id, `Movement ${index}`));
  }
  await page.goto('/transactions');
  await selectVisible(page);
  await expect(page.getByText('20 selected', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Next page', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Edit selected', exact: true })).toHaveCount(0);
  await selectVisible(page);
  await removeSelected(page, 1);
  await expect(page.getByRole('checkbox', { name: /^Select / })).toHaveCount(20);
  await expect(page.getByRole('button', { name: 'Previous page', exact: true })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Next page', exact: true })).toBeDisabled();
});
