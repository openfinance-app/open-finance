import { mkdir } from 'node:fs/promises';
import { expect, test } from '@playwright/test';
import { registerWithRetry } from './helpers/auth';
import en from '../public/locales/en/liabilities.json' with { type: 'json' };
import fr from '../public/locales/fr/liabilities.json' with { type: 'json' };
import importEn from '../public/locales/en/import.json' with { type: 'json' };
import importFr from '../public/locales/fr/import.json' with { type: 'json' };
import commonEn from '../public/locales/en/common.json' with { type: 'json' };
import commonFr from '../public/locales/fr/common.json' with { type: 'json' };

for (const language of ['en', 'fr'] as const) {
  test(`imported debt account becomes a managed liability (${language})`, async ({
    page,
  }, info) => {
    test.setTimeout(180_000);
    await page.setViewportSize({
      width: language === 'fr' ? 390 : 1280,
      height: language === 'fr' ? 844 : 900,
    });
    await page.emulateMedia({ reducedMotion: 'reduce' });
    const labels = language === 'fr' ? fr : en;
    const imports = language === 'fr' ? importFr : importEn;
    const common = language === 'fr' ? commonFr : commonEn;
    const username = `liability_${language}_${Date.now()}`;
    const credentials = {
      username,
      password: 'PropertyPassword123!',
      masterPassword: 'PropertyMaster123!',
    };
    const registration = await registerWithRetry(page.request, {
      ...credentials,
      email: `${username}@example.invalid`,
      skipSeeding: true,
    });
    expect(registration.status()).toBe(201);
    const login = await page.request.post('/api/v1/auth/login', { data: credentials });
    expect(login.ok()).toBe(true);
    const auth = await login.json();
    const headers = {
      Authorization: `Bearer ${auth.token}`,
      'X-Encryption-Session': auth.encryptionKey,
    };
    const api = async (method: string, path: string, data?: unknown) => {
      const response = await page.request.fetch(`/api/v1${path}`, { method, headers, data });
      expect(response.ok(), `${path}: ${await response.text()}`).toBe(true);
      return response.status() === 204 ? null : response.json();
    };
    const importConfig = await api('GET', '/config/import');
    test.skip(
      !importConfig.skroogeJsonEnabled,
      'Skrooge import and property conversion are disabled'
    );
    await api('POST', '/users/me/onboarding', {
      country: 'FR',
      baseCurrency: 'EUR',
      language,
      dateFormat: 'YYYY-MM-DD',
      numberFormat: language === 'fr' ? '1 234,56' : '1,234.56',
      amountDisplayMode: 'base',
    });
    await page.addInitScript(
      ({ auth, language }) => {
        localStorage.setItem('auth_token', auth.token);
        localStorage.setItem(
          'auth_user',
          JSON.stringify({
            id: auth.userId,
            username: auth.username,
            baseCurrency: 'EUR',
            onboardingComplete: true,
          })
        );
        localStorage.setItem('openfinance_language', language);
        localStorage.setItem('theme', 'dark');
        sessionStorage.setItem('encryption_key', auth.encryptionKey);
        sessionStorage.setItem('encryption_session', auth.encryptionKey);
      },
      { auth, language }
    );
    const source = {
      bank: [{ id: 1, t_name: 'Imported lender' }],
      account: [
        {
          id: 10,
          rd_bank_id: 1,
          t_name: 'Mortgage account',
          t_type: 'L',
          t_close: 'N',
          rd_unit_id: 1,
        },
      ],
      unit: [{ id: 1, t_name: 'Euro (EUR)', t_symbol: '€', t_type: '1', rd_unit_id: 0 }],
      unitvalue: [{ id: 1, rd_unit_id: 1, d_date: '2024-01-01', f_quantity: 1 }],
      operation: [
        {
          id: 1,
          rd_account_id: 10,
          d_date: '2024-01-01',
          i_group_id: 0,
          t_status: 'Y',
          rc_unit_id: 1,
          t_comment: 'Initial borrowing',
        },
        {
          id: 2,
          rd_account_id: 10,
          d_date: '2024-06-01',
          i_group_id: 0,
          t_status: 'Y',
          rc_unit_id: 1,
          t_comment: 'Historical repayment',
        },
      ],
      suboperation: [
        { id: 11, rd_operation_id: 1, f_value: '-1200' },
        { id: 12, rd_operation_id: 2, f_value: '400' },
      ],
    };
    await page.goto('/import');
    await page.locator('input[type="file"]').setInputFiles({
      name: 'liability.json',
      mimeType: 'application/json',
      buffer: Buffer.from(JSON.stringify(source)),
    });
    await page.getByRole('button', { name: common.buttons.upload, exact: true }).click();
    const next = page.getByRole('button', { name: common.buttons.next, exact: true });
    await expect(next).toBeEnabled();
    await next.click();
    await expect(next).toBeEnabled();
    await next.click();
    await page.getByRole('button', { name: imports.summary.confirm, exact: true }).click();
    await expect(
      page.getByRole('status').filter({ hasText: imports.progress.success })
    ).toBeVisible({ timeout: 90_000 });
    const candidates = await api('GET', '/liabilities/conversion/candidates');
    expect(candidates).toHaveLength(1);
    expect(candidates[0].outstandingBalance).toBe(800);
    await page.goto('/liabilities');
    await page.getByRole('button', { name: labels.conversion.action, exact: true }).click();
    let dialog = page.getByRole('dialog');
    await expect(dialog.getByRole('radio', { name: /Mortgage account/ })).toBeVisible();
    const capture = async (stage: string) => {
      await page.evaluate(() => document.fonts.ready);
      await dialog.evaluate(element => {
        element.scrollTop = 0;
      });
      await expect
        .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
        .toBe(true);
      if (stage === 'invalid-date')
        await dialog
          .getByText(labels.conversion.invalidDate, { exact: true })
          .scrollIntoViewIfNeeded();
      await mkdir('../.impeccable/review/liability-conversion', { recursive: true });
      await page.screenshot({
        path: `../.impeccable/review/liability-conversion/${language}-${stage}.png`,
        animations: 'disabled',
      });
      await dialog.screenshot({ path: info.outputPath(`${stage}.png`), animations: 'disabled' });
      if (language === 'fr' && ['terms', 'review'].includes(stage)) {
        await dialog.evaluate(element => {
          element.scrollTop = element.scrollHeight;
        });
        await page.screenshot({
          path: `../.impeccable/review/liability-conversion/${language}-${stage}-bottom.png`,
          animations: 'disabled',
        });
      }
    };
    await capture('source');
    await dialog.getByRole('radio', { name: /Mortgage account/ }).check();
    await dialog.getByRole('button', { name: labels.wizard.next, exact: true }).click();
    const startDateInput = dialog.getByLabel(labels.conversion.startDate, { exact: false });
    const originalDate = await startDateInput.inputValue();
    await startDateInput.fill('');
    await dialog
      .getByRole('button', { name: labels.conversion.payments.next, exact: true })
      .click();
    await expect(dialog.getByText(labels.conversion.invalidDate, { exact: true })).toBeVisible();
    await capture('invalid-date');
    await startDateInput.fill(originalDate);
    await dialog.getByLabel(labels.conversion.principal, { exact: false }).fill('2000');
    await dialog.getByLabel(labels.conversion.fundedPrincipal, { exact: false }).fill('1200');
    await dialog.getByLabel(labels.conversion.type, { exact: true }).selectOption('MORTGAGE');
    await capture('terms');
    await dialog
      .getByRole('button', { name: labels.conversion.payments.next, exact: true })
      .click();
    await expect(dialog.getByText(labels.conversion.payments.empty, { exact: true })).toBeVisible();
    await capture('payments');
    await dialog.getByRole('button', { name: labels.conversion.review, exact: true }).click();
    await expect(
      dialog.getByRole('button', { name: labels.conversion.confirm, exact: true })
    ).toBeVisible();
    await capture('review');
    await dialog.getByRole('button', { name: labels.conversion.confirm, exact: true }).click();
    await expect.poll(async () => (await api('GET', '/liabilities')).length).toBe(1);
    const loans = await api('GET', '/liabilities');
    expect(loans[0].currentBalance).toBe(800);
    expect(loans[0].fundedAmount).toBe(1200);
    expect(loans[0].principalPaid).toBe(400);
    const account = await api('GET', `/accounts/${candidates[0].accountId}`);
    expect(account.isActive).toBe(false);
    expect(account.ownBalance).toBe(0);
    const tx = await api('GET', `/transactions/search?liabilityId=${loans[0].id}`);
    expect(tx.totalElements).toBe(2);
    dialog = page.getByRole('dialog');
    await expect(dialog.getByText(labels.detail.payments, { exact: true })).toBeVisible();
    await dialog.getByText(labels.detail.payments, { exact: true }).click();
    await expect(
      dialog.getByRole('link', { name: 'Historical repayment', exact: true })
    ).toBeVisible();
    await capture('history');
    await expect(
      dialog.locator('summary').filter({ hasText: labels.detail.operationGroups.payments })
    ).toContainText('1');
    await expect(
      dialog.locator('summary').filter({ hasText: labels.detail.operationGroups.drawdowns })
    ).toContainText('1');
    // Actual history remains accessible even when no forecast terms were supplied.
    await dialog.getByRole('button', { name: labels.detail.schedule, exact: true }).click();
    const history = dialog.getByRole('region', { name: labels.amortization.title, exact: true });
    await expect(history.getByText(labels.amortization.paid, { exact: true })).toBeVisible();
    await expect(history.getByRole('link', { name: '2024-06-01', exact: true })).toBeVisible();
    const recorded = await api('GET', `/liabilities/${loans[0].id}/amortization/history`);
    expect(recorded).toHaveLength(1);
    expect(recorded[0]).toMatchObject({
      paymentAmount: 400,
      principalPortion: 400,
      remainingBalance: 800,
    });
    await capture('amortization-history');
    // Stress the actual currency/tooltip components with a thirty-year forecast.
    await page.route('**/api/v1/users/me/settings', async route => {
      const response = await route.fetch();
      await route.fulfill({
        response,
        json: { ...(await response.json()), secondaryCurrency: 'USD' },
      });
    });
    await page.route('**/api/v1/currencies/exchange-rates**', async route => {
      await route.fulfill({ json: { rate: 1.1, baseCurrency: 'EUR', targetCurrency: 'USD' } });
    });
    await page.route(`**/api/v1/liabilities/${loans[0].id}/amortization`, async route => {
      await route.fulfill({
        json: Array.from({ length: 360 }, (_, index) => ({
          paymentNumber: index + 1,
          paymentDate: new Date(Date.UTC(2027, index, 5)).toISOString().slice(0, 10),
          paymentAmount: index === 359 ? 3.52 : 2.72,
          principalPortion: index === 359 ? 3.02 : 2.22,
          interestPortion: 0.5,
          insurancePortion: 0,
          remainingBalance: index === 359 ? 0 : (80000 - (index + 1) * 222) / 100,
        })),
      });
    });
    await page.goto(`/liabilities?highlight=${loans[0].id}`);
    await dialog.getByRole('button', { name: labels.detail.schedule, exact: true }).click();
    await expect(dialog.getByRole('combobox')).toHaveValue('0');
    await expect(dialog.locator('[role="region"]')).toHaveCount(1);
    await expect(history.locator('[data-testid="converted-amount"]')).toHaveCount(100);
    await dialog.getByRole('combobox').selectOption('18');
    await expect(history).toBeVisible();
    await expect(history).toContainText('2056-12-05');
    await expect(history.locator('[data-testid="converted-amount"]')).toHaveCount(5);
    await capture('long-amortization');
    await dialog.getByRole('button', { name: labels.detail.payments, exact: true }).click();
    await dialog.getByRole('link', { name: 'Historical repayment', exact: true }).click();
    await expect(page).toHaveURL(/liabilityId=/);
    await expect(page.getByText('Historical repayment', { exact: true }).first()).toBeVisible();
  });
}
