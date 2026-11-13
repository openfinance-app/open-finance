import { expect, test } from '@playwright/test';

// UI fixtures only; encrypted persistence and holdings are exercised by the backend integration test.
for (const width of [1280, 390]) {
  for (const theme of ['dark', 'light']) {
    test(`incoming transfer preserves the source and shows its receipt at ${width}px in ${theme}`, async ({
      page,
    }, info) => {
      const language = width === 390 ? 'fr' : 'en';
      const french = language === 'fr';
      const user = {
        id: 1,
        username: 'receipt-preview',
        email: 'receipt@example.invalid',
        baseCurrency: 'EUR',
        onboardingComplete: true,
      };
      const source = {
        id: 1,
        userId: 1,
        accountId: 1,
        toAccountId: 2,
        accountName: 'Bitcoin',
        toAccountName: 'Prediction account',
        type: 'EXPENSE',
        amount: 377.6497915456392,
        currency: 'EUR',
        date: '2026-06-24',
        transferId: 'receipt-preview',
        transferAmount: 392.531046206336,
        transferCurrency: 'EUR',
        transferOriginalAmount: 439.36,
        transferOriginalCurrency: 'USD',
        transferConversionRate: 0.8934155276,
        assetId: 9,
        investmentQuantity: -0.00589432,
        investmentCash: false,
        exactValues: { amount: '377.6497915456392', investmentQuantity: '-0.00589432' },
        description: 'Synthetic transfer receipt',
        isReconciled: false,
        createdAt: '2026-06-24',
      };
      const receipt = {
        ...source,
        id: 2,
        accountId: 2,
        toAccountId: 1,
        accountName: source.toAccountName,
        toAccountName: source.accountName,
        type: 'INCOME',
        amount: source.transferAmount,
        originalAmount: source.transferOriginalAmount,
        originalCurrency: 'USD',
        conversionRate: source.transferConversionRate,
        transferAmount: source.amount,
        transferOriginalAmount: undefined,
        transferOriginalCurrency: undefined,
        assetId: undefined,
        investmentQuantity: undefined,
        exactValues: { amount: '392.531046206336' },
      };
      let saved: Record<string, unknown> | undefined;
      const errors: string[] = [];
      page.on('pageerror', error => errors.push(error.message));
      await page.setViewportSize({ width, height: 1000 });
      await page.addInitScript(
        ({ user, language, theme }) => {
          localStorage.setItem('auth_token', 'ui-test-token');
          localStorage.setItem('auth_user', JSON.stringify(user));
          localStorage.setItem('openfinance_language', language);
          localStorage.setItem('theme', theme);
          sessionStorage.setItem('encryption_enabled', 'false');
        },
        { user, language, theme }
      );
      await page.route('**/api/v1/**', async route => {
        const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
        let body: unknown = [];
        if (path === '/users/me') body = user;
        else if (path === '/users/me/base-currency') body = { baseCurrency: 'EUR' };
        else if (path === '/users/me/settings')
          body = {
            userId: 1,
            theme,
            language,
            numberFormat: french ? '1 234,56' : '1,234.56',
            dateFormat: 'YYYY-MM-DD',
            amountDisplayMode: 'base',
          };
        else if (path === '/config/security') body = { encryptionEnabled: false };
        else if (path === '/accounts')
          body = [source.accountName, source.toAccountName].map((name, index) => ({
            id: index + 1,
            userId: 1,
            name,
            type: 'INVESTMENT',
            currency: 'EUR',
            balance: 0,
            isActive: true,
          }));
        else if (path === '/assets')
          body = [{ id: 9, accountId: 1, name: 'Bitcoin', type: 'CRYPTO', currency: 'EUR' }];
        else if (path === '/transactions/search') body = [receipt];
        else if (path === '/transactions/transfers/receipt-preview') {
          if (route.request().method() === 'PUT') saved = route.request().postDataJSON();
          body = source;
        } else if (path === '/history/exists') body = { exists: false };
        await route.fulfill({ json: body });
      });
      await page.goto('/transactions');
      await page.locator('#transaction-2').hover();
      await page
        .getByRole('button', { name: french ? 'Modifier la transaction' : 'Edit transaction' })
        .click();
      const dialog = page.getByRole('dialog');
      const recorded = dialog
        .locator('dl')
        .filter({ hasText: french ? 'Montant reçu enregistré' : 'Recorded receipt' });
      await expect(recorded).toContainText(french ? '439,36 USD' : '439.36 USD');
      await expect(recorded).toContainText(french ? '392,53 EUR' : '392.53 EUR');
      await expect(dialog.locator('#amount')).toHaveValue(
        french ? '377,6497915456392' : '377.6497915456392'
      );
      await recorded.scrollIntoViewIfNeeded();
      await page.screenshot({
        path: info.outputPath(`receipt-${width}-${theme}.png`),
        fullPage: true,
      });
      expect(await recorded.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
      await dialog.locator('#description').fill('Metadata edit');
      await dialog.locator('button[type="submit"]').click();
      await expect(dialog).toHaveCount(0);
      expect(saved).toMatchObject({
        fromAccountId: 1,
        toAccountId: 2,
        amount: '377.6497915456392',
        currency: 'EUR',
        investment: { assetId: 9, operation: 'REMOVE', quantity: '0.00589432' },
      });
      expect(saved?.originalCurrency).toBeUndefined();
      expect(errors).toEqual([]);
    });
  }
}
