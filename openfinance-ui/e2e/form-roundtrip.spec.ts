import { expect, test } from '@playwright/test';
import { registerWithRetry } from './helpers/auth';

// Run with scripts/run-browser-tests.py: all records belong to disposable synthetic users.
for (const language of ['en', 'fr']) {
  test.describe(`ordinary form round trips (${language})`, () => {
    test.use({
      timezoneId: 'America/Los_Angeles',
      viewport: { width: language === 'fr' ? 390 : 1280, height: 1000 },
    });
    test('preserves financial meaning, clears metadata and protects drafts', async ({
      page,
    }, info) => {
      test.setTimeout(180_000);
      const french = language === 'fr';
      const username = `forms_${language}_${Date.now()}`;
      const credentials = {
        username,
        password: 'FormsPassword123!',
        masterPassword: 'FormsMaster123!',
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
      const api = async (method: string, path: string, data?: Record<string, unknown>) => {
        const response = await page.request.fetch(`/api/v1${path}`, { method, headers, data });
        expect(response.ok(), `${method} ${path}: ${await response.text()}`).toBe(true);
        return response.status() === 204 ? null : response.json();
      };
      await api('POST', '/users/me/onboarding', {
        country: 'FR',
        baseCurrency: 'EUR',
        language,
        dateFormat: 'YYYY-MM-DD',
        numberFormat: french ? '1 234,56' : '1,234.56',
        amountDisplayMode: 'base',
      });
      const account = await api('POST', '/accounts', {
        name: 'Forms checking',
        type: 'CHECKING',
        currency: 'EUR',
        initialBalance: '0',
        openingDate: '2020-01-01',
        description: 'Old note',
        accountNumber: '1234',
      });
      const usd = await api('POST', '/accounts', {
        name: 'Forms dollars',
        type: 'CHECKING',
        currency: 'USD',
        initialBalance: '0',
        openingDate: '2020-01-01',
      });
      const savings = await api('POST', '/accounts', {
        name: 'Forms savings',
        type: 'SAVINGS',
        currency: 'EUR',
        initialBalance: '0',
        openingDate: '2020-01-01',
      });
      const tx = await api('POST', '/transactions', {
        accountId: account.id,
        type: 'EXPENSE',
        amount: '50',
        currency: 'EUR',
        date: '2026-01-01',
        description: 'Forms expense',
      });
      const category = await api('POST', '/categories', { name: 'Forms category' });
      await api('POST', '/payees', { name: 'Forms vendor', categoryId: category.id });
      const recurring = await api('POST', '/recurring-transactions', {
        accountId: usd.id,
        type: 'EXPENSE',
        amount: '100',
        currency: 'EUR',
        categoryId: category.id,
        payee: 'Forms vendor',
        description: 'Forms recurrence',
        notes: 'Old note',
        frequency: 'MONTHLY',
        nextOccurrence: '2099-01-01',
      });
      const loan = await api('POST', '/liabilities', {
        name: 'Forms unknown rate',
        type: 'LOAN',
        principal: '1000',
        currentBalance: '1000',
        currency: 'EUR',
        startDate: '2020-01-01',
        notes: 'n'.repeat(800),
      });
      const asset = await api('POST', '/assets', {
        name: 'Forms vehicle',
        type: 'VEHICLE',
        quantity: '1',
        purchasePrice: '5000',
        currentPrice: '4000',
        currency: 'EUR',
        purchaseDate: '2020-01-01',
        notes: 'n'.repeat(800),
        brand: 'Brand',
        model: 'Model',
        serialNumber: 'Serial',
        condition: 'GOOD',
        usefulLifeYears: 5,
        warrantyExpiration: '2027-01-01',
      });
      const institution = await api('POST', '/institutions', { name: 'Forms unknown country' });
      const transfer = await api('POST', '/transactions/transfer', {
        accountId: account.id,
        toAccountId: savings.id,
        type: 'TRANSFER',
        amount: '5',
        currency: 'EUR',
        date: '2026-01-01',
        description: 'Forms transfer',
        paymentMethod: 'BANK_TRANSFER',
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
      page.on('pageerror', e => errors.push(e.message));
      const dialog = page.getByRole('dialog');
      const save = async () => {
        await dialog.locator('button[type="submit"]').click();
        await expect(dialog).toHaveCount(0);
      };
      const screenshot = async (name: string) => {
        await expect(dialog).toBeVisible();
        const dimensions = await dialog.evaluate(el => ({
          clientWidth: el.clientWidth,
          scrollWidth: el.scrollWidth,
          overflow: [...el.querySelectorAll<HTMLElement>('*')]
            .filter(
              child =>
                child.getBoundingClientRect().right > el.getBoundingClientRect().right ||
                child.scrollWidth > child.clientWidth + 1
            )
            .map(child => ({
              tag: child.tagName,
              id: child.id,
              class: child.className,
              width: child.clientWidth,
              scroll: child.scrollWidth,
              right: child.getBoundingClientRect().right,
            }))
            .slice(0, 15),
        }));
        expect(dimensions.scrollWidth, JSON.stringify(dimensions)).toBeLessThanOrEqual(
          dimensions.clientWidth
        );
        await page.screenshot({ path: info.outputPath(`${name}-${language}.png`), fullPage: true });
      };
      const editTransaction = async (id: number) => {
        await page.goto('/transactions');
        const row = page.locator(`#transaction-${id}`);
        await row.hover();
        await row
          .getByRole('button', {
            name: french ? 'Modifier la transaction' : 'Edit transaction',
            exact: true,
          })
          .click();
      };

      await page.goto('/accounts');
      const card = page.getByText('Forms checking', { exact: true });
      await card.hover();
      await page
        .getByRole('button', { name: french ? 'Modifier le compte' : 'Edit account', exact: true })
        .first()
        .click();
      await dialog.locator('#name').fill('Forms checking edited');
      await dialog.locator('#description').fill('');
      await dialog.locator('#accountNumber').fill('');
      await screenshot('account');
      await save();
      const savedAccount = await api('GET', `/accounts/${account.id}`);
      expect(Number(savedAccount.ownBalance)).toBe(-55);
      expect(savedAccount.description || '').toBe('');
      expect(savedAccount.accountNumber || '').toBe('');

      await page.goto('/liabilities');
      await page
        .getByRole('button', {
          name: french ? 'Modifier le passif' : 'Edit liability',
          exact: true,
        })
        .click();
      await expect(dialog.locator('#interestRate')).toHaveValue('');
      await dialog.locator('#name').fill('Forms loan edited');
      await screenshot('loan');
      await save();
      expect((await api('GET', `/liabilities/${loan.id}`)).interestRate).toBeNull();

      await page.goto('/recurring-transactions');
      await page
        .getByTitle(french ? 'Modifier la transaction récurrente' : 'Edit recurring transaction')
        .click();
      await expect(dialog.locator('#currency')).toContainText('EUR');
      await dialog.locator('#description').fill('Forms recurrence edited');
      await dialog.locator('#notes').fill('');
      await dialog.locator('#endDate').fill('2099-01-01');
      await screenshot('recurring');
      await save();
      const savedRecurring = await api('GET', `/recurring-transactions/${recurring.id}`);
      expect(savedRecurring.currency).toBe('EUR');
      expect(Number(savedRecurring.amount)).toBe(100);
      expect(savedRecurring.payee).toBe('Forms vendor');
      expect(savedRecurring.notes).toBeNull();
      await page
        .getByTitle(french ? 'Modifier la transaction récurrente' : 'Edit recurring transaction')
        .click();
      await dialog.locator('#type').selectOption('TRANSFER');
      await dialog.locator('#toAccountId').click();
      await page.getByRole('option', { name: /Forms savings/ }).click();
      await save();
      const changedType = await api('GET', `/recurring-transactions/${recurring.id}`);
      expect(changedType.categoryId).toBeNull();
      expect(changedType.payee).toBeNull();

      await page.goto('/assets');
      await page.getByText('Forms vehicle', { exact: true }).first().click();
      await page.getByRole('button', { name: french ? 'Modifier' : 'Edit', exact: true }).click();
      await dialog.locator('#name').fill('Forms vehicle edited');
      await screenshot('asset');
      await save();
      expect((await api('GET', `/assets/${asset.id}`)).notes).toHaveLength(800);

      await editTransaction(transfer.id);
      await dialog.locator('#paymentMethod').selectOption('CASH');
      await save();
      expect(
        (await api('GET', `/transactions/transfers/${transfer.transferId}`)).paymentMethod
      ).toBe('CASH');

      await editTransaction(tx.id);
      await expect(dialog.locator('#accountId')).toHaveAccessibleName(/.+/);
      await expect(dialog.locator('#payee')).toHaveAccessibleName(/.+/);
      await expect(dialog.locator('#categoryId')).toHaveAccessibleName(/.+/);
      await dialog.locator('#amount').fill('bad amount');
      await dialog.locator('button[type="submit"]').click();
      await expect(dialog.locator('#amount')).toHaveAttribute('aria-invalid', 'true');
      await expect(dialog.locator('#amount')).toHaveAccessibleDescription(/.+/);
      await expect(dialog.locator('#amount')).toHaveValue('bad amount');
      await screenshot('invalid-transaction');
      page.once('dialog', prompt => prompt.dismiss());
      await page.keyboard.press('Escape');
      await expect(dialog).toBeVisible();
      await dialog.locator('#amount').fill('60');
      await dialog.locator('#date').fill('2026-02-31');
      await dialog.locator('button[type="submit"]').click();
      await expect(dialog.locator('#date')).toHaveAttribute('aria-invalid', 'true');
      expect(Number((await api('GET', `/transactions/${tx.id}`)).amount)).toBe(50);
      page.once('dialog', prompt => prompt.accept());
      await page.keyboard.press('Escape');
      await expect(dialog).toHaveCount(0);

      await page.goto('/institutions');
      await page
        .getByRole('button', { name: new RegExp(`(?:Edit|Modifier).*${institution.name}`) })
        .click();
      await expect(dialog.locator('#institutionCountry')).not.toContainText('France');
      await dialog.locator('input').first().fill('Forms country still unknown');
      await screenshot('institution');
      await save();
      const institutions = await api('GET', '/institutions');
      expect(
        institutions.find((item: { id: number }) => item.id === institution.id).country
      ).toBeNull();
      // At 00:30 UTC it is still the previous day in Los Angeles.
      // Creation defaults and their date ceilings must use that local calendar day.
      await page.clock.setFixedTime(new Date('2026-11-12T00:30:00Z'));
      for (const fixture of [
        {
          path: '/recurring-transactions',
          add: french ? 'Ajouter une transaction récurrente' : 'Add Recurring Transaction',
          field: '#nextOccurrence',
        },
        {
          path: '/liabilities',
          add: french ? 'Ajouter un passif' : 'Add Liability',
          field: '#startDate',
        },
        { path: '/assets', add: french ? 'Ajouter un actif' : 'Add Asset', field: '#purchaseDate' },
        {
          path: '/real-estate',
          add: french ? 'Ajouter un bien' : 'Add Property',
          field: '#purchaseDate',
        },
      ]) {
        await page.goto(fixture.path);
        await page.getByRole('button', { name: fixture.add, exact: true }).first().click();
        if (fixture.path === '/assets')
          await dialog
            .getByRole('button', { name: french ? /Un placement/ : /An investment/ })
            .click();
        if (fixture.path === '/real-estate')
          await dialog
            .getByRole('button', {
              name: french
                ? /Enregistrer un bien que je possède/
                : /Record a property I already own/,
            })
            .click();
        await expect(dialog.locator(fixture.field)).toHaveValue('2026-11-11');
        await page.keyboard.press('Escape');
        await expect(dialog).toHaveCount(0);
      }
      expect(errors).toEqual([]);
    });
  });
}
