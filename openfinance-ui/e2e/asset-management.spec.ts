import { expect, test } from '@playwright/test';

// Isolate the UI journeys from live balances. API behavior is covered by backend tests.
for (const width of [1280, 390]) {
  test(`manage a physical item, its costs and debt at ${width}px`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 900 });
    const user = {
      id: 1,
      username: 'preview',
      email: 'preview@example.invalid',
      baseCurrency: 'USD',
      onboardingComplete: true,
    };
    const car = {
      id: 1,
      userId: 1,
      name: 'Family car',
      type: 'VEHICLE',
      quantity: 1,
      purchasePrice: 24000,
      currentPrice: 18500,
      totalCost: 24000,
      totalValue: 18500,
      unrealizedGain: -5500,
      gainPercentage: -0.229,
      currency: 'USD',
      purchaseDate: '2024-01-15',
      holdingDays: 500,
      isPhysical: true,
      isActive: true,
      acquisitionType: 'PURCHASE',
      lastUpdated: '2026-10-29T10:00:00',
      createdAt: '2024-01-15T10:00:00',
    };
    const loan = {
      id: 9,
      name: 'Car loan',
      type: 'AUTO_LOAN',
      principal: 20000,
      currentBalance: 8000,
      currency: 'USD',
      startDate: '2024-01-15',
      interestRate: 4,
      fundingStatus: 'ACTIVE',
    };
    const costs: Record<string, unknown>[] = [];
    let links: Record<string, unknown>[] = [];
    const errors: string[] = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.addInitScript(user => {
      localStorage.setItem('auth_token', 'ui-test-token');
      localStorage.setItem('auth_user', JSON.stringify(user));
      localStorage.setItem('i18nextLng', 'en');
      sessionStorage.setItem('encryption_enabled', 'false');
    }, user);
    await page.route('**/api/v1/**', async route => {
      const request = route.request();
      const url = new URL(request.url());
      const path = url.pathname.replace('/api/v1', '');
      const paged = (content: unknown[]) => ({
        content,
        totalElements: content.length,
        totalPages: 1,
        number: 0,
        size: 20,
      });
      let body: unknown = [];
      if (path === '/users/me') body = user;
      else if (path === '/users/me/base-currency') body = { baseCurrency: 'USD' };
      else if (path === '/users/me/settings')
        body = {
          id: 1,
          userId: 1,
          theme: 'dark',
          language: 'en',
          country: 'US',
          dateFormat: 'YYYY-MM-DD',
          numberFormat: '1,234.56',
          amountDisplayMode: 'native',
          timezone: 'Europe/Paris',
        };
      else if (path === '/config/security') body = { encryptionEnabled: false };
      else if (path === '/currencies')
        body = [
          { code: 'USD', name: 'US Dollar', symbol: '$', decimalPlaces: 2 },
          { code: 'EUR', name: 'Euro', symbol: '€', decimalPlaces: 2 },
        ];
      else if (path === '/assets/search') body = paged([car]);
      else if (path === '/assets') body = [car];
      else if (path === '/assets/1') body = car;
      else if (path === '/assets/1/liabilities') body = links;
      else if (path === '/liabilities/9/asset-links') {
        if (request.method() === 'PUT') links = request.postDataJSON();
        body = links;
      } else if (path === '/liabilities') body = [loan];
      else if (path === '/liabilities/search') body = paged([loan]);
      else if (path === '/accounts')
        body = [
          {
            id: 1,
            name: 'Everyday account',
            type: 'CHECKING',
            currency: 'USD',
            balance: 10000,
            isActive: true,
          },
        ];
      else if (path === '/transactions' || path === '/transactions/search') {
        if (request.method() === 'POST') {
          const cost = {
            ...request.postDataJSON(),
            id: costs.length + 1,
            userId: 1,
            isReconciled: false,
          };
          costs.push(cost);
          body = cost;
        } else body = paged(costs);
      } else if (path === '/history/exists') body = { exists: false };
      else if (path === '/real-estate/search') body = paged([]);
      await route.fulfill({ json: body });
    });

    await page.goto('/assets?group=PHYSICAL');
    await expect(
      page.getByRole('button', { name: 'Physical assets', exact: true })
    ).toHaveAttribute('aria-pressed', 'true');
    await page.getByRole('button', { name: 'Add Asset', exact: true }).click();
    await expect(page.getByLabel(/Quantity/)).toHaveValue('1');
    await expect(page.getByLabel('Brand', { exact: true })).not.toBeVisible();
    await page.getByRole('dialog').evaluate(async el => {
      await Promise.all(el.getAnimations().map(animation => animation.finished));
    });
    await page.screenshot({
      path: testInfo.outputPath(`physical-form-${width}.png`),
      fullPage: true,
    });
    await page.getByRole('button', { name: 'Cancel', exact: true }).click();

    await page.goto('/assets?highlight=1');
    await page.getByRole('button', { name: 'Costs', exact: true }).click();
    await page.getByRole('button', { name: 'Add cost', exact: true }).click();
    await page.getByLabel('Description', { exact: true }).fill('New tires');
    await page.getByLabel('Amount (USD)', { exact: true }).fill('600');
    await page.getByLabel('Paid from', { exact: true }).selectOption('1');
    await page.getByRole('button', { name: 'Save expense', exact: true }).click();
    await expect(page.getByText('New tires', { exact: true })).toBeVisible();
    expect(costs[0]).toMatchObject({
      assetId: 1,
      movementType: 'MAINTENANCE',
      amount: '600',
      accountId: 1,
    });
    expect(costs[0]).not.toHaveProperty('realEstateId');

    await page.getByRole('button', { name: 'Linked debt', exact: true }).click();
    await page.getByRole('button', { name: 'Link a loan', exact: true }).click();
    await page.getByRole('combobox', { name: 'Loan', exact: true }).selectOption('9');
    await expect(page.getByRole('combobox', { name: 'Asset / property', exact: true })).toHaveValue(
      '1'
    );
    await expect(page.getByLabel('Share of loan (%)', { exact: true })).toHaveValue('100');
    await page.screenshot({ path: testInfo.outputPath(`loan-links-${width}.png`), fullPage: true });
    await page.getByRole('button', { name: 'Save links', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Link a loan', exact: true })).toBeVisible();
    expect(links).toEqual([{ assetId: 1, relationship: 'FINANCING', allocationPercentage: 100 }]);

    await page.goto('/real-estate');
    await page.getByRole('button', { name: 'Add Property', exact: true }).click();
    await expect(
      page.getByRole('button', { name: /Record a property I already own/ })
    ).toBeVisible();
    await expect(
      page.getByRole('button', { name: /Buy a property and record the payment/ })
    ).toBeVisible();
    await page.getByRole('dialog').evaluate(async el => {
      await Promise.all(el.getAnimations().map(animation => animation.finished));
    });
    await page.screenshot({
      path: testInfo.outputPath(`property-choices-${width}.png`),
      fullPage: true,
    });
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)
    ).toBe(true);
    expect(errors).toEqual([]);
  });
}
