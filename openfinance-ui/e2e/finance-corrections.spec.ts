import { expect, test, type Page, type TestInfo } from '@playwright/test';
import { registerUser, signIn } from './helpers/auth';

test.setTimeout(120_000);

// Every financial record in these tests is created through the real forms.
// Response observation checks persistence; the purchase route deliberately loses one response.
async function newUser(page: Page) {
  const username = `corr_${Date.now().toString(36)}`;
  const user = {
    username,
    email: `${username}@example.invalid`,
    password: 'CorrectionLogin123!',
    masterPassword: 'CorrectionMaster123!',
  };
  await registerUser(page, user);
  await signIn(page, user);
  await page.getByRole('heading', { name: 'Welcome to Open Finance!', exact: true }).waitFor();
  await page.getByRole('button', { name: /USD —/ }).click();
  await page.getByRole('button', { name: 'EUR Euro', exact: true }).click();
  await page.getByRole('button', { name: 'DD/MM/YYYY', exact: true }).click();
  await page.getByRole('button', { name: 'Get Started', exact: true }).click();
  await page.getByRole('heading', { name: 'Dashboard', exact: true }).waitFor();
}

async function visit(page: Page, link: string, heading = link) {
  await page
    .getByRole('link', {
      name: link === 'Transactions' ? /^Transactions/ : link,
      exact: link !== 'Transactions',
    })
    .click();
  await page.getByRole('heading', { name: heading, exact: true }).waitFor();
}

async function evidence(page: Page, info: TestInfo, name: string) {
  await info.attach(name, {
    body: await page.screenshot({ fullPage: true }),
    contentType: 'image/png',
  });
  await info.attach(`${name}-screen`, {
    body: await page.locator('body').ariaSnapshot(),
    contentType: 'text/plain',
  });
}

async function account(page: Page, name: string, amount: string, currency = 'EUR') {
  await visit(page, 'Accounts');
  await page.getByRole('button', { name: 'Add Account', exact: true }).first().click();
  await page.getByLabel('Account Name *', { exact: true }).fill(name);
  if (currency !== 'EUR') {
    await page.getByRole('button', { name: /EUR —/ }).click();
    await page.getByRole('textbox', { name: 'Search currency', exact: true }).fill(currency);
    await page.getByRole('button', { name: new RegExp(`^${currency} `) }).click();
  }
  await page.getByLabel(`Initial Balance (${currency}) *`, { exact: true }).fill(amount);
  await page.getByRole('button', { name: 'Create Account', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
}

async function liability(
  page: Page,
  name: string,
  principal: string,
  balance: string,
  currency = 'EUR'
) {
  await visit(page, 'Liabilities');
  await page.getByRole('button', { name: 'Add Liability', exact: true }).first().click();
  await page.getByLabel('Liability Name *', { exact: true }).fill(name);
  await page.getByLabel('Liability Type *', { exact: true }).selectOption('MORTGAGE');
  await page.getByLabel('Original or approved principal *', { exact: true }).fill(principal);
  await page.getByLabel('Current Balance *', { exact: true }).fill(balance);
  if (currency !== 'EUR') {
    await page.getByRole('button', { name: /EUR —/ }).click();
    await page.getByRole('textbox', { name: 'Search currency', exact: true }).fill(currency);
    await page.getByRole('button', { name: new RegExp(`^${currency} `) }).click();
  }
}

function loanCard(page: Page, name: string) {
  return page
    .getByRole('heading', { name: new RegExp(`^${name} `) })
    .locator('xpath=ancestor::div[starts-with(@id,"liability-")]');
}

test('unlinked mortgage edits persist and linked mortgage deletion restores correctly through history', async ({
  page,
}, info) => {
  await newUser(page);
  await liability(page, 'Editable loan', '1000', '500');
  await page.getByRole('button', { name: 'Create Liability', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await loanCard(page, 'Editable loan')
    .getByRole('button', { name: 'Edit liability', exact: true })
    .click();
  await page.getByLabel('Notes (Optional)', { exact: true }).fill('Saved by clicking Update');
  await page.getByRole('button', { name: 'Update Liability', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.reload();
  await expect(page.getByText('Saved by clicking Update', { exact: true })).toBeVisible();
  await evidence(page, info, 'loan-edit-persisted');

  await visit(page, 'Real Estate', 'Real Estate Portfolio');
  await page.getByRole('button', { name: 'Add Property', exact: true }).click();
  await page.getByLabel('Property Name *', { exact: true }).fill('Linked home');
  await page.getByLabel('Address *', { exact: true }).fill('Synthetic browser address');
  await page.getByLabel('Purchase Price *', { exact: true }).fill('1000');
  await page.getByLabel('Current Value *', { exact: true }).fill('1000');
  await page
    .getByRole('combobox')
    .filter({ hasText: /^None$/ })
    .click();
  await page.getByRole('option', { name: /Editable loan/ }).click();
  await page.getByRole('button', { name: 'Create Property', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await visit(page, 'Liabilities');
  await loanCard(page, 'Editable loan')
    .getByRole('button', { name: 'Delete liability', exact: true })
    .click();
  await page.getByRole('button', { name: 'Delete', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.reload();
  await expect(page.getByRole('heading', { name: /Editable loan/ })).toHaveCount(0);
  await visit(page, 'Real Estate', 'Real Estate Portfolio');
  await expect(page.getByRole('heading', { name: 'Linked home', exact: true })).toBeVisible();
  await expect(page.getByText('Editable loan', { exact: true })).toHaveCount(0);
  await evidence(page, info, 'linked-mortgage-deleted');
  await visit(page, 'History', 'Operation History');
  const deletion = page.getByRole('row').filter({ hasText: /Deleted Liability.*Editable loan/ });
  await deletion.getByRole('button', { name: 'Undo', exact: true }).click();
  await expect(deletion.getByRole('button', { name: 'Redo', exact: true })).toBeVisible();
  await visit(page, 'Liabilities');
  await expect(loanCard(page, 'Editable loan')).toContainText('500.00');
  await evidence(page, info, 'linked-mortgage-undo');
});

test('lost purchase response retries once and the complete purchase can be undone and redone', async ({
  page,
}, info) => {
  await newUser(page);
  await account(page, 'Purchase checking', '30000');
  await visit(page, 'Real Estate', 'Real Estate Portfolio');
  await page.getByRole('button', { name: /^Buy property$/i }).click();
  await page.getByLabel('Property Name *', { exact: true }).fill('Retry home');
  await page.getByLabel('Address *', { exact: true }).fill('Synthetic purchase address');
  await page.getByLabel('Purchase Price *', { exact: true }).fill('100000');
  await page.getByLabel('Current Value *', { exact: true }).fill('100000');
  await page.getByRole('button', { name: 'Next', exact: true }).click();
  await page.getByLabel('Mortgage name *', { exact: true }).fill('Retry mortgage');
  await page.getByLabel('Loan amount *', { exact: true }).fill('80000');
  await page.getByLabel('Disbursement route', { exact: true }).selectOption('account');
  await page.getByLabel('Down payment amount', { exact: true }).fill('20000');
  await page
    .getByRole('combobox')
    .filter({ hasText: /^None$/ })
    .click();
  await page.getByRole('option', { name: /Purchase checking/ }).click();
  await page.getByRole('button', { name: 'Next', exact: true }).click();
  const attempts: { request: unknown; response: unknown }[] = [];
  await page.route('**/api/v1/real-estate/purchase', async route => {
    const response = await route.fetch();
    expect(response.status()).toBe(201);
    attempts.push({ request: route.request().postDataJSON(), response: await response.json() });
    if (attempts.length === 1) await route.abort('failed');
    else await route.fulfill({ response });
  });
  await page.getByRole('button', { name: 'Confirm purchase', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Network Error');
  await evidence(page, info, 'lost-committed-response');
  await page.getByRole('button', { name: 'Confirm purchase', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(attempts).toHaveLength(2);
  expect(attempts[1]).toEqual(attempts[0]);
  await page.unroute('**/api/v1/real-estate/purchase');
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Retry home', exact: true })).toHaveCount(1);
  await visit(page, 'Accounts');
  await expect(page.getByText('€10,000.00', { exact: true }).first()).toBeVisible();
  await visit(page, 'Dashboard');
  await expect(page.getByText('€30,000.00', { exact: true }).first()).toBeVisible();
  await evidence(page, info, 'purchase-reconciled');
  await visit(page, 'Liabilities');
  await expect(loanCard(page, 'Retry mortgage')).toContainText('80,000.00');
  await loanCard(page, 'Retry mortgage')
    .getByRole('button', { name: 'Delete liability', exact: true })
    .click();
  await page.getByRole('button', { name: 'Delete', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText(/Cannot delete liability/);
  await expect(page.getByRole('dialog')).toBeVisible();
  await evidence(page, info, 'deletion-guard-visible');
  await page.getByRole('button', { name: 'Cancel', exact: true }).click();
  await visit(page, 'History', 'Operation History');
  const purchase = page.getByRole('row').filter({ hasText: 'Retry home' });
  await expect(purchase).toHaveCount(1);
  await purchase.getByRole('button', { name: 'Undo', exact: true }).click();
  await expect(purchase.getByRole('button', { name: 'Redo', exact: true })).toBeVisible();
  await visit(page, 'Accounts');
  await expect(page.getByText('€30,000.00', { exact: true }).first()).toBeVisible();
  await visit(page, 'History', 'Operation History');
  await purchase.getByRole('button', { name: 'Redo', exact: true }).click();
  await expect(purchase.getByRole('button', { name: 'Undo', exact: true })).toBeVisible();
  await visit(page, 'Accounts');
  await expect(page.getByText('€10,000.00', { exact: true }).first()).toBeVisible();
  await evidence(page, info, 'purchase-redone');
});

test('dedicated drawdown form preserves a small BTC amount', async ({ page }, info) => {
  await newUser(page);
  await account(page, 'Bitcoin account', '0', 'BTC');
  await liability(page, 'Bitcoin loan', '1', '0', 'BTC');
  await page.getByRole('button', { name: 'Create Liability', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await loanCard(page, 'Bitcoin loan')
    .getByRole('button', { name: 'View liability details', exact: true })
    .click();
  await page.getByRole('button', { name: 'Drawdowns', exact: true }).click();
  await page.getByRole('button', { name: 'Add tranche', exact: true }).click();
  await page.getByLabel('Planned amount *', { exact: true }).fill('0.001000000000000001');
  await page.getByRole('button', { name: 'Add', exact: true }).click();
  await page.getByRole('button', { name: 'Draw', exact: true }).click();
  await page.getByLabel('Amount *', { exact: true }).fill('0.001000000000000001');
  await page.getByLabel('Account *', { exact: true }).selectOption({ label: 'Bitcoin account' });
  const result = page.waitForResponse(
    r => r.url().endsWith('/disburse') && r.request().method() === 'POST'
  );
  await page.getByRole('button', { name: 'Confirm disbursement', exact: true }).click();
  const response = await result;
  expect(response.status()).toBe(200);
  expect(response.request().postDataJSON().amount).toBe('0.001000000000000001');
  expect(await response.text()).toContain('0.001000000000000001');
  await page.reload();
  await expect(loanCard(page, 'Bitcoin loan')).toContainText('0.001');
  await evidence(page, info, 'bitcoin-draw-persisted');
  await visit(page, 'Transactions');
  const expand = page.getByRole('button', { name: 'Expand Transactions', exact: true });
  if (await expand.isVisible()) await expand.click();
  await page.getByRole('link', { name: /Recurring/ }).click();
  await page.getByRole('heading', { name: 'Recurring Transactions', exact: true }).waitFor();
  await page.getByRole('button', { name: 'Add Recurring Transaction', exact: true }).click();
  const recurring = page.getByRole('dialog');
  await recurring.getByRole('combobox').filter({ hasText: 'Select account...' }).click();
  await page.getByRole('option', { name: /Bitcoin account/ }).click();
  await recurring.getByLabel('Amount *', { exact: true }).fill('0.0001');
  await recurring
    .getByLabel('Description *', { exact: true })
    .fill('Small scheduled Bitcoin payment');
  await recurring.getByLabel('Next Occurrence *', { exact: true }).fill('01/01/2099');
  await recurring.getByRole('button', { name: 'Create', exact: true }).click();
  await expect(recurring).toBeHidden();
  await page.reload();
  await expect(
    page.getByText('Small scheduled Bitcoin payment', { exact: true }).first()
  ).toBeVisible();
  await evidence(page, info, 'small-bitcoin-recurrence-persisted');
});

test('payoff removes future insurance and the monthly payment from the loan card', async ({
  page,
}, info) => {
  await newUser(page);
  await account(page, 'Payoff checking', '30000');
  await liability(page, 'Payoff loan', '10000', '10000');
  await page.getByLabel('Interest Rate (% Annual)', { exact: true }).fill('0');
  await page.getByLabel('Minimum Monthly Payment', { exact: true }).fill('10000');
  await page.getByLabel('Insurance Rate (% Annual)', { exact: true }).fill('1.2');
  const year = new Date().getFullYear() + 1;
  await page.getByLabel('End Date (Optional)', { exact: true }).fill(`30/09/${year}`);
  await page.getByRole('button', { name: 'Create Liability', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(loanCard(page, 'Payoff loan')).toContainText('10,010.00');
  await loanCard(page, 'Payoff loan')
    .getByRole('button', { name: 'View liability details', exact: true })
    .click();
  await expect(page.getByRole('dialog')).toContainText('10,010.00');
  await evidence(page, info, 'one-payment-insurance-projection');
  await page.getByRole('button', { name: 'Close', exact: true }).click();
  await visit(page, 'Transactions');
  await page.getByRole('button', { name: 'Add Transaction', exact: true }).first().click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Type *', { exact: true }).selectOption('EXPENSE');
  await dialog.getByLabel('Amount *', { exact: true }).fill('10010');
  await dialog
    .getByRole('combobox')
    .filter({ hasText: /^Account$/ })
    .click();
  await page.getByRole('option', { name: /Payoff checking/ }).click();
  await dialog
    .getByRole('combobox')
    .filter({ hasText: /^None$/ })
    .last()
    .click();
  await page.getByRole('option', { name: /Payoff loan/ }).click();
  await dialog.getByLabel('Description', { exact: true }).fill('Final loan payment');
  await dialog.getByRole('button', { name: 'Create Transaction', exact: true }).click();
  await expect(dialog).toBeHidden();
  await visit(page, 'Liabilities');
  await page.reload();
  const card = loanCard(page, 'Payoff loan');
  await expect(card).toContainText('Paid');
  await expect(card.getByText('Monthly Payment', { exact: true }).locator('..')).toContainText(
    '€0.00'
  );
  await card.getByRole('button', { name: 'View liability details', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('10,010.00');
  await expect(page.getByRole('dialog')).not.toContainText('10,130.00');
  await evidence(page, info, 'paid-loan-no-future-costs');
});

test('deactivating a property keeps its earlier value in the dashboard history', async ({
  page,
}, info) => {
  await newUser(page);
  await visit(page, 'Real Estate', 'Real Estate Portfolio');
  await page.getByRole('button', { name: 'Add Property', exact: true }).click();
  const past = new Date();
  past.setDate(1);
  past.setMonth(past.getMonth() - 2);
  const month = String(past.getMonth() + 1).padStart(2, '0');
  const year = past.getFullYear();
  await page.getByLabel('Property Name *', { exact: true }).fill('Historical home');
  await page.getByLabel('Address *', { exact: true }).fill('Synthetic historical address');
  await page.getByLabel('Purchase Price *', { exact: true }).fill('1000');
  await page.getByLabel('Current Value *', { exact: true }).fill('1000');
  await page.getByLabel('Purchase Date *', { exact: true }).fill(`01/${month}/${year}`);
  await page.getByRole('button', { name: 'Create Property', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.getByRole('button', { name: 'Edit property', exact: true }).click();
  await page.getByLabel('Current Value *', { exact: true }).fill('1200');
  await page.getByLabel('Property is active', { exact: true }).uncheck();
  await page.getByRole('button', { name: 'Update Property', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.reload();
  await visit(page, 'Dashboard');
  await page.getByRole('button', { name: 'Custom range', exact: true }).click();
  await page.locator('#period-from').fill(`01/${month}/${year}`);
  const history = page.waitForResponse(
    response =>
      response.url().includes('/networth-history?') &&
      response.url().includes(`startDate=${year}-${month}-01`) &&
      response.url().includes(`endDate=${year}-${month}-02`)
  );
  await page.locator('#period-to').fill(`02/${month}/${year}`);
  await page.keyboard.press('Escape');
  const response = await history;
  expect(response.status()).toBe(200);
  const points = await response.json();
  expect(points).toHaveLength(2);
  expect(points.map((point: { netWorth: number }) => point.netWorth)).toEqual([1000, 1000]);
  await page
    .getByRole('heading', { name: 'Net Worth Trend', exact: true })
    .locator('../../..')
    .getByRole('application')
    .press('ArrowRight');
  await expect(page.getByText('€1,000.00', { exact: true }).first()).toBeVisible();
  await evidence(page, info, 'inactive-property-preserves-history');
});

test('interest-only payment card includes current interest without scheduled principal', async ({
  page,
}, info) => {
  await newUser(page);
  await account(page, 'Draw account', '0');
  await liability(page, 'Interest-only mortgage', '300000', '0');
  await page.getByLabel('Interest Rate (% Annual)', { exact: true }).fill('4.1');
  await page.getByLabel('Minimum Monthly Payment', { exact: true }).fill('2000');
  await page.getByRole('button', { name: 'Create Liability', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await loanCard(page, 'Interest-only mortgage')
    .getByRole('button', { name: 'View liability details', exact: true })
    .click();
  await page.getByRole('button', { name: 'Drawdowns', exact: true }).click();
  await page.getByRole('button', { name: 'Add tranche', exact: true }).click();
  await page.getByLabel('Planned amount *', { exact: true }).fill('300000');
  await page.getByLabel('Interest-only', { exact: true }).check();
  await page.getByRole('button', { name: 'Add', exact: true }).click();
  await page.getByRole('button', { name: 'Draw', exact: true }).click();
  await page.getByLabel('Amount *', { exact: true }).fill('300000');
  await page.getByLabel('Account *', { exact: true }).selectOption({ label: 'Draw account' });
  await page.getByRole('button', { name: 'Confirm disbursement', exact: true }).click();
  await expect(
    page.getByRole('button', { name: 'Confirm disbursement', exact: true })
  ).toBeHidden();
  await page.reload();
  const card = loanCard(page, 'Interest-only mortgage');
  await expect(card.getByText('Monthly Payment', { exact: true }).locator('..')).toContainText(
    '€1,025.00'
  );
  await evidence(page, info, 'interest-only-current-payment');
});
