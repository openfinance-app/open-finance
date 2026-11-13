import { expect, test, type Locator } from '@playwright/test';
import { registerWithRetry } from './helpers/auth';

// Disposable API fixtures; the XOF/EUR peg makes the conversion independent of live quotes.
for (const language of ['en', 'fr'] as const) {
  test(`foreign costs, splits and search honor display preferences (${language})`, async ({
    page,
  }, info) => {
    test.setTimeout(120_000);
    await page.setViewportSize({ width: language === 'fr' ? 390 : 1280, height: 900 });
    const username = `currency_${language}_${Date.now()}`;
    const credentials = {
      username,
      password: 'CurrencyPassword123!',
      masterPassword: 'CurrencyMaster123!',
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
    await api('POST', '/users/me/onboarding', {
      country: 'FR',
      baseCurrency: 'EUR',
      language,
      dateFormat: 'YYYY-MM-DD',
      numberFormat: language === 'fr' ? '1 234,56' : '1,234.56',
      amountDisplayMode: 'base',
    });
    const account = await api('POST', '/accounts', {
      name: 'Currency checking',
      type: 'CHECKING',
      currency: 'XOF',
      initialBalance: '6559570',
      openingDate: '2020-01-01',
    });
    const asset = await api('POST', '/assets', {
      name: 'Currency vehicle',
      type: 'VEHICLE',
      quantity: '1',
      purchasePrice: '1967871',
      currentPrice: '1311914',
      currency: 'XOF',
      purchaseDate: '2024-01-01',
    });
    const loan = await api('POST', '/liabilities', {
      name: 'Currency loan',
      type: 'LOAN',
      principal: '655957',
      currentBalance: '655957',
      minimumPayment: '65600',
      currency: 'XOF',
      interestRate: '5',
      termMonths: 12,
      startDate: '2026-01-01',
    });
    await api('POST', '/transactions', {
      accountId: account.id,
      assetId: asset.id,
      movementType: 'MAINTENANCE',
      type: 'EXPENSE',
      amount: '655957',
      currency: 'XOF',
      date: '2024-06-01',
      description: 'Currency maintenance',
    });
    const split = await api('POST', '/transactions', {
      accountId: account.id,
      type: 'EXPENSE',
      amount: '1311914',
      currency: 'XOF',
      date: '2024-06-02',
      description: 'Currency split purchase',
      splits: [
        { amount: '655957', description: 'First part' },
        { amount: '655957', description: 'Second part' },
      ],
    });
    const search = await api('GET', '/search?q=Currency%20maintenance');
    expect(search.resultsByType.TRANSACTION[0]).toMatchObject({
      amount: 655957,
      currency: 'XOF',
      amountInBaseCurrency: 1000,
      baseCurrency: 'EUR',
      isConverted: true,
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
    const errors: string[] = [];
    page.on('pageerror', error => errors.push(error.message));
    const dialog = page.getByRole('dialog');
    const checkAmount = async (row: Locator, mode: 'base' | 'native' | 'both') => {
      await expect(row).toBeVisible();
      if (mode !== 'native') await expect(row).toContainText(/€1[ ,\u00a0\u202f]?000[,.]00/);
      else await expect(row).not.toContainText('€');
      if (mode !== 'base') await expect(row).toContainText(/XOF\s*655[ ,\u00a0\u202f]?957/);
      else await expect(row).not.toContainText('XOF');
      await expect
        .poll(() => row.evaluate(el => Math.max(0, el.scrollWidth - el.clientWidth)))
        .toBeLessThanOrEqual(1);
    };
    for (const mode of ['base', 'native', 'both'] as const) {
      await api('PUT', '/users/me/settings', { amountDisplayMode: mode });
      await page.goto('/assets');
      const assetCard = page.locator(`#asset-${asset.id}`);
      await expect(assetCard.getByRole('heading', { name: 'Currency vehicle' })).toBeVisible();
      const valueBar = assetCard.locator('.relative.rounded-full.overflow-hidden');
      await expect
        .poll(() =>
          valueBar.evaluate(el => {
            const box = el.getBoundingClientRect();
            return [...el.querySelectorAll('.bolt-in')].every(amount => {
              const bounds = amount.getBoundingClientRect();
              return (
                bounds.right <= box.right + 1 &&
                bounds.left >= box.left - 1 &&
                bounds.bottom <= box.bottom + 1
              );
            });
          })
        )
        .toBe(true);
      await assetCard.screenshot({
        path: info.outputPath(`asset-${mode}.png`),
        animations: 'disabled',
      });
      await page.goto(`/assets?highlight=${asset.id}`);
      await dialog
        .getByRole('button', { name: language === 'fr' ? 'Frais' : 'Costs', exact: true })
        .click();
      const costRow = dialog.getByRole('link', { name: /Currency maintenance/ }).locator('..');
      await checkAmount(costRow, mode);
      await dialog.screenshot({
        path: info.outputPath(`costs-${mode}.png`),
        animations: 'disabled',
      });

      await page.goto(`/transactions?highlight=${split.id}`);
      await dialog
        .getByRole('button', { name: language === 'fr' ? 'Ventilations' : 'Splits', exact: true })
        .click();
      const parts = dialog.getByRole('list', { name: 'Split details' }).getByRole('listitem');
      await expect(parts).toHaveCount(2);
      await checkAmount(parts.nth(0), mode);
      await checkAmount(parts.nth(1), mode);
      await dialog.screenshot({
        path: info.outputPath(`splits-${mode}.png`),
        animations: 'disabled',
      });

      await page.goto('/search?q=Currency%20maintenance');
      const result = page
        .getByRole('heading', { name: 'Currency maintenance', exact: true })
        .locator('..');
      await checkAmount(result, mode);
      await page.screenshot({
        path: info.outputPath(`search-${mode}.png`),
        animations: 'disabled',
      });

      await page.goto(`/liabilities?highlight=${loan.id}`);
      await dialog
        .getByRole('button', {
          name: language === 'fr' ? 'Tableau d’amortissement' : 'Amortization Schedule',
          exact: true,
        })
        .click();
      const principal = dialog
        .getByText(language === 'fr' ? 'Capital' : 'Principal', { exact: true })
        .first()
        .locator('..');
      await checkAmount(principal, mode);
      await dialog.screenshot({
        path: info.outputPath(`schedule-${mode}.png`),
        animations: 'disabled',
      });
    }
    expect(errors).toEqual([]);
  });
}
