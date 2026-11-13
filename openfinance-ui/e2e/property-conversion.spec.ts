import { expect, test } from '@playwright/test';
import { registerWithRetry } from './helpers/auth';
import en from '../public/locales/en/realEstate.json' with { type: 'json' };
import fr from '../public/locales/fr/realEstate.json' with { type: 'json' };
import importEn from '../public/locales/en/import.json' with { type: 'json' };
import importFr from '../public/locales/fr/import.json' with { type: 'json' };
import commonEn from '../public/locales/en/common.json' with { type: 'json' };
import commonFr from '../public/locales/fr/common.json' with { type: 'json' };

for (const language of ['en', 'fr'] as const) {
  test(`imported holding becomes a property without another payment (${language})`, async ({
    page,
  }, info) => {
    test.setTimeout(180_000);
    await page.setViewportSize({ width: language === 'fr' ? 390 : 1280, height: 1000 });
    const labels = language === 'fr' ? fr : en;
    const imports = language === 'fr' ? importFr : importEn;
    const common = language === 'fr' ? commonFr : commonEn;
    const username = `property_${language}_${Date.now()}`;
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
      bank: [{ id: 1, t_name: 'Property source' }],
      account: [{ id: 10, rd_bank_id: 1, t_name: 'Apartment account', t_type: 'A', t_close: 'N' }],
      unit: [
        { id: 1, t_name: 'Euro (EUR)', t_symbol: '€', t_type: '1', rd_unit_id: 0 },
        {
          id: 2,
          t_name: 'Apartment unit',
          t_symbol: 'HOME',
          t_type: 'S',
          rd_unit_id: 1,
          t_source: '',
          t_internet_code: '',
        },
      ],
      unitvalue: [
        { id: 1, rd_unit_id: 1, d_date: '2024-01-01', f_quantity: 1 },
        { id: 2, rd_unit_id: 2, d_date: '2024-01-01', f_quantity: 1000 },
        { id: 3, rd_unit_id: 2, d_date: '2025-01-01', f_quantity: 1200 },
      ],
      operation: [
        {
          id: 1,
          rd_account_id: 10,
          d_date: '2024-01-01',
          i_group_id: 0,
          t_status: 'Y',
          rc_unit_id: 2,
          t_comment: 'Initial property contribution',
        },
        {
          id: 2,
          rd_account_id: 10,
          d_date: '2024-06-01',
          i_group_id: 0,
          t_status: 'Y',
          rc_unit_id: 2,
          t_comment: 'Additional property contribution',
        },
      ],
      suboperation: [
        { id: 11, rd_operation_id: 1, f_value: '1' },
        { id: 12, rd_operation_id: 2, f_value: '0.5' },
      ],
    };
    const importFile = async (repeat = false) => {
      await page.goto('/import');
      if (repeat)
        await page.getByRole('button', { name: common.buttons.close, exact: true }).click();
      await page.locator('input[type="file"]').setInputFiles({
        name: 'property.json',
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
    };
    await importFile();
    const before = await api('GET', '/real-estate/conversion/candidates');
    expect(before).toHaveLength(1);
    expect(before[0].currentValue).toBe(1800);
    expect(before[0].closesSourceAccount).toBe(true);
    const accountsBefore = await api('GET', '/accounts');
    const cashBefore = accountsBefore.find(
      (item: { name: string }) => item.name === 'Apartment account'
    );
    const txBefore = await api('GET', `/transactions/search?accountId=${cashBefore.id}`);
    expect(txBefore.totalElements).toBe(2);
    await page.goto('/real-estate');
    await page.getByRole('button', { name: labels.conversion.action, exact: true }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByRole('radio', { name: /Apartment unit/ })).toBeVisible();
    await dialog.screenshot({ path: info.outputPath('source.png') });
    await dialog.getByRole('radio', { name: /Apartment unit/ }).check();
    await dialog.getByRole('button', { name: labels.wizard.next, exact: true }).click();
    await dialog.getByRole('button', { name: labels.wizard.next, exact: true }).click();
    await expect(dialog.getByText(labels.form.validation.addressRequired)).toBeVisible();
    await dialog.getByLabel(labels.form.address, { exact: false }).fill('10 Synthetic Street');
    page.once('dialog', confirm => confirm.dismiss());
    await page.keyboard.press('Escape');
    await expect(dialog.getByLabel(labels.form.address, { exact: false })).toHaveValue(
      '10 Synthetic Street'
    );
    await dialog.screenshot({ path: info.outputPath('details.png') });
    await dialog.getByRole('button', { name: labels.wizard.next, exact: true }).click();
    await expect(dialog.getByText(labels.conversion.reviewTitle)).toBeVisible();
    await expect(dialog.getByText(labels.conversion.noPayment)).toBeVisible();
    await expect(
      dialog.getByText(labels.conversion.closeAccount.replace('{{account}}', 'Apartment account'))
    ).toBeVisible();
    expect(await dialog.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
    await dialog.screenshot({ path: info.outputPath('review.png') });
    await page.screenshot({ path: info.outputPath('viewport.png') });
    await dialog.getByRole('button', { name: labels.conversion.confirm, exact: true }).click();
    await expect(
      page.getByRole('heading', { name: labels.conversion.title, exact: true })
    ).toHaveCount(0);
    const properties = await api('GET', '/real-estate');
    expect(properties).toHaveLength(1);
    expect(properties[0]).toMatchObject({
      assetId: before[0].assetId,
      purchasePrice: 1500,
      currentValue: 1800,
      convertedFromHolding: true,
    });
    const after = await api('GET', `/accounts/${cashBefore.id}`);
    expect(after.ownBalance).toBe(cashBefore.ownBalance);
    expect(after.isActive).toBe(false);
    const txAfter = await api('GET', `/transactions/search?accountId=${cashBefore.id}`);
    expect(txAfter.totalElements).toBe(txBefore.totalElements);
    expect(await api('GET', '/real-estate/conversion/candidates')).toHaveLength(0);
    await page.getByRole('button', { name: labels.propertyDetail.tab.costs, exact: true }).click();
    await expect(page.getByRole('link', { name: /Initial property contribution/ })).toBeVisible();
    await expect(
      page.getByRole('link', { name: /Additional property contribution/ })
    ).toBeVisible();
    await expect(page.getByText(labels.movements.empty)).toHaveCount(0);
    await page.screenshot({ path: info.outputPath('property.png'), fullPage: true });
    const propertyFilter = `realEstateId=${properties[0].id}`;
    const originalLink = page.getByRole('link', { name: /Initial property contribution/ });
    await expect(originalLink).toHaveAttribute('href', new RegExp(propertyFilter));
    await originalLink.click();
    await expect(page).toHaveURL(new RegExp(`/transactions\\?${propertyFilter}`));
    await expect(
      page.getByText('Initial property contribution', { exact: true }).first()
    ).toBeVisible();
    expect((await api('GET', `/transactions/search?${propertyFilter}`)).totalElements).toBe(
      txBefore.totalElements
    );
    // The same file remains a harmless duplicate import after conversion.
    await importFile(true);
    const reimported = await api('GET', '/real-estate');
    expect(reimported[0].currentValue).toBe(1800);
    expect((await api('GET', `/accounts/${cashBefore.id}`)).isActive).toBe(false);
    expect(
      (await api('GET', `/transactions/search?accountId=${cashBefore.id}`)).totalElements
    ).toBe(txBefore.totalElements);

    // Exercise long, foreign-currency history using the response shape, without financial writes.
    const activity = await api('GET', `/transactions/search?${propertyFilter}`);
    const longHistory = Array.from({ length: 40 }, (_, index) => ({
      ...activity.content[index % activity.content.length],
      id: 10000 + index,
      description: `Property payment ${index + 1} — ${'Acquisition contribution '.repeat(4)}`,
      amount: index % 2 === 0 ? 655957 : 1311914,
      currency: 'XOF',
      amountInBaseCurrency: index % 2 === 0 ? 1000 : 2000,
      baseCurrency: 'EUR',
      exchangeRate: 1 / 655.957,
      isConverted: true,
    }));
    await page.route(
      url =>
        url.pathname === '/api/v1/transactions/search' &&
        url.searchParams.get('realEstateId') === String(properties[0].id),
      route =>
        route.fulfill({
          json: { ...activity, content: longHistory, totalElements: 40, totalPages: 1 },
        })
    );
    const viewports =
      language === 'fr'
        ? [
            { width: 390, height: 844, name: 'mobile' },
            { width: 900, height: 840, name: 'user' },
          ]
        : [{ width: 1280, height: 900, name: 'desktop' }];
    for (const viewport of viewports) {
      await page.setViewportSize(viewport);
      await page.goto(`/real-estate?highlight=${properties[0].id}`);
      await page
        .getByRole('button', { name: labels.propertyDetail.tab.overview, exact: true })
        .click();
      const overview = page.getByRole('region', {
        name: labels.propertyDetail.tab.overview,
        exact: true,
      });
      const actions = await overview.locator(':scope > *').nth(0).boundingBox();
      const metrics = await overview.locator(':scope > *').nth(1).boundingBox();
      expect(metrics!.y - actions!.y - actions!.height).toBeGreaterThanOrEqual(16);
      await page.screenshot({
        path: info.outputPath(`${viewport.name}-overview.png`),
        animations: 'disabled',
      });
      await page
        .getByRole('button', { name: labels.propertyDetail.tab.costs, exact: true })
        .click();
      const content = page.getByRole('region', {
        name: labels.propertyDetail.tab.costs,
        exact: true,
      });
      const paymentList = content.getByRole('region', {
        name: labels.movements.acquisition,
        exact: true,
      });
      await expect(paymentList.getByRole('link', { name: /Property payment 40/ })).toBeAttached();
      expect(await paymentList.evaluate(el => el.scrollHeight > el.clientHeight)).toBe(true);
      expect(
        await paymentList.evaluate(el => el.getBoundingClientRect().height)
      ).toBeLessThanOrEqual(320);
      expect(await content.evaluate(el => el.scrollHeight <= el.clientHeight + 1)).toBe(true);
      const heading = content.getByRole('heading', {
        name: labels.movements.acquisition,
        exact: true,
      });
      const headingBefore = await heading.boundingBox();
      const controls = content.getByRole('button').first();
      const controlsBefore = await controls.boundingBox();
      const tabs = page.getByRole('navigation', { name: labels.propertyDetail.sections });
      const tabsBefore = await tabs.boundingBox();
      await paymentList.evaluate(el => {
        el.scrollTop = 0;
      });
      const amountBounds = await paymentList.evaluate(async el => {
        await Promise.all(
          el
            .getAnimations({ subtree: true })
            .filter(animation => animation.effect?.getComputedTiming().iterations !== Infinity)
            .map(animation => animation.finished.catch(() => undefined))
        );
        return Array.from(el.querySelectorAll('a[href*="highlight="]')).map(link => {
          const row = link.parentElement!;
          const amount = row.querySelector<HTMLElement>('[aria-hidden="false"]')!;
          return {
            text: amount.textContent,
            rightGap: row.getBoundingClientRect().right - amount.getBoundingClientRect().right,
            scrollWidth: amount.scrollWidth,
            clientWidth: amount.clientWidth,
          };
        });
      });
      expect(amountBounds).toHaveLength(40);
      expect(
        amountBounds.every(amount => amount.text?.includes('€') && !amount.text.includes('XOF'))
      ).toBe(true);
      expect(
        amountBounds.every(
          amount => amount.rightGap >= 0 && amount.scrollWidth <= amount.clientWidth
        )
      ).toBe(true);
      expect(await content.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
      expect(await paymentList.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
      await info.attach(`${viewport.name}-amount-bounds`, {
        body: JSON.stringify(amountBounds),
        contentType: 'application/json',
      });
      await page.screenshot({
        path: info.outputPath(`${viewport.name}-costs.png`),
        animations: 'disabled',
      });
      await paymentList.focus();
      await paymentList.press('End');
      await expect.poll(() => paymentList.evaluate(el => el.scrollTop)).toBeGreaterThan(0);
      await expect(paymentList.getByRole('link', { name: /Property payment 40/ })).toBeInViewport();
      await expect(heading).toBeInViewport();
      await expect(controls).toBeInViewport();
      expect((await heading.boundingBox())!.y).toBeCloseTo(headingBefore!.y, 0);
      expect((await controls.boundingBox())!.y).toBeCloseTo(controlsBefore!.y, 0);
      expect((await tabs.boundingBox())!.y).toBeCloseTo(tabsBefore!.y, 0);
      expect(await content.evaluate(el => el.scrollTop)).toBe(0);
      expect(await page.getByRole('dialog').evaluate(el => el.scrollTop)).toBe(0);
      expect(await page.getByRole('dialog').evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(
        true
      );
      await page.screenshot({
        path: info.outputPath(`${viewport.name}-costs-end.png`),
        animations: 'disabled',
      });
    }
  });
}
