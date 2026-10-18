import { expect, test, type Locator, type Page } from '@playwright/test';
import { loginAs, registerWithRetry } from './helpers/auth';
import accountEn from '../public/locales/en/accounts.json' with { type: 'json' };
import accountFr from '../public/locales/fr/accounts.json' with { type: 'json' };
import budgetEn from '../public/locales/en/budgets.json' with { type: 'json' };
import budgetFr from '../public/locales/fr/budgets.json' with { type: 'json' };
import categoryEn from '../public/locales/en/categories.json' with { type: 'json' };
import categoryFr from '../public/locales/fr/categories.json' with { type: 'json' };
import commonEn from '../public/locales/en/common.json' with { type: 'json' };
import commonFr from '../public/locales/fr/common.json' with { type: 'json' };

async function api<T>(page: Page, path: string, body: unknown, method = 'POST'): Promise<T> {
  return page.evaluate(
    async ({ path, body, method }) => {
      const response = await fetch(`/api/v1${path}`, {
        method,
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${localStorage.getItem('auth_token') || sessionStorage.getItem('auth_token')}`,
          'X-Encryption-Session': sessionStorage.getItem('encryption_session') || '',
        },
        body: JSON.stringify(body),
      });
      if (!response.ok) throw new Error(`${method} ${path}: HTTP ${response.status}`);
      return response.json();
    },
    { path, body, method }
  );
}

async function checkPickerAnchor(page: Page, field: Locator, openLabel: string): Promise<void> {
  await field.scrollIntoViewIfNeeded();
  const native = field.locator('..').locator('input[type="date"]');
  const visibleBounds = await field.boundingBox();
  const nativeBounds = await native.boundingBox();
  expect(visibleBounds).not.toBeNull();
  expect(nativeBounds).not.toBeNull();
  expect(nativeBounds!.width).toBeGreaterThan(100);
  expect(nativeBounds!.height).toBeGreaterThan(20);
  expect(nativeBounds!.x).toBeCloseTo(visibleBounds!.x, 0);
  expect(nativeBounds!.y).toBeCloseTo(visibleBounds!.y, 0);
  expect(nativeBounds!.width).toBeCloseTo(visibleBounds!.width, 0);
  await field.locator('..').getByRole('button', { name: openLabel }).click();
  // Observe the real native picker call, rather than replacing it with a mock.
  await expect(native).toHaveAttribute('data-picker-opened', 'true');
  await page.keyboard.press('Escape');
}

async function checkHelp(page: Page, text: string, screenshotPath: string): Promise<void> {
  await expect(page.getByText(text, { exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: text, exact: true }).focus();
  await expect(page.getByRole('tooltip')).toHaveText(text);
  await expect(page.getByRole('tooltip').locator('..')).toHaveCSS('opacity', '1');
  const bounds = await page.getByRole('tooltip').locator('..').boundingBox();
  expect(bounds).not.toBeNull();
  expect(bounds!.x).toBeGreaterThanOrEqual(0);
  expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(page.viewportSize()!.width);
  await page.screenshot({ path: screenshotPath, animations: 'disabled' });
  await page.keyboard.press('Tab');
  await expect(page.getByRole('tooltip')).not.toBeVisible();
}

for (const language of ['en', 'fr'] as const) {
  test(`date picker anchors and contextual help (${language})`, async ({
    page,
    request,
  }, testInfo) => {
    test.setTimeout(120_000);
    await page.setViewportSize({ width: 1280, height: 1000 });
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.addInitScript(() => {
      const showPicker = HTMLInputElement.prototype.showPicker;
      HTMLInputElement.prototype.showPicker = function () {
        showPicker.call(this);
        this.dataset.pickerOpened = 'true';
      };
    });
    const a = language === 'fr' ? accountFr : accountEn;
    const b = language === 'fr' ? budgetFr : budgetEn;
    const c = language === 'fr' ? commonFr : commonEn;
    const categories = language === 'fr' ? categoryFr : categoryEn;
    const username = `picker_${language}_${Date.now()}`;
    const credentials = { username, password: 'Password123!', masterPassword: 'RealMaster123!' };
    expect(
      (
        await registerWithRetry(request, {
          ...credentials,
          email: `${username}@example.invalid`,
          skipSeeding: true,
        })
      ).status()
    ).toBe(201);
    await loginAs(page, credentials);
    await api(page, '/users/me/onboarding', {
      country: 'FR',
      baseCurrency: 'EUR',
      secondaryCurrency: '',
      language,
      dateFormat: 'DD/MM/YYYY',
      numberFormat: '1,234.56',
      amountDisplayMode: 'base',
    });
    await api(page, '/users/me/base-currency', { baseCurrency: 'EUR' }, 'PUT');
    await api(page, '/categories', { name: 'Groceries' });

    await page.goto('/budget');
    await page.getByRole('button', { name: b.addBudget, exact: true }).first().click();
    const endDate = page.getByRole('textbox', { name: `${b.form.endDate} *`, exact: true });
    await endDate.fill('31/12/2026');
    await checkPickerAnchor(page, endDate, c.dateRange.openPicker);
    await expect(endDate).toHaveValue('31/12/2026');
    // The earlier caret fix must survive when the calendar input gains a real anchor.
    await endDate.click();
    await endDate.evaluate((element: HTMLInputElement) => element.setSelectionRange(0, 1));
    await page.keyboard.press('Backspace');
    await expect(endDate).toHaveValue('1/12/2026');
    expect(await endDate.evaluate((element: HTMLInputElement) => element.selectionStart)).toBe(0);
    await page.keyboard.type('2');
    await expect(endDate).toHaveValue('21/12/2026');
    await page.screenshot({ path: testInfo.outputPath(`budget-date-${language}.png`) });
    await page.setViewportSize({ width: 390, height: 844 });
    await checkPickerAnchor(page, endDate, c.dateRange.openPicker);
    await page.screenshot({ path: testInfo.outputPath(`budget-date-mobile-${language}.png`) });

    await page.goto('/dashboard');
    await page
      .getByRole('group', { name: c.periodSelector.label })
      .getByRole('button', { name: c.dateRange.custom, exact: true })
      .click();
    await checkPickerAnchor(
      page,
      page.getByRole('textbox', { name: c.dateRange.from, exact: true }),
      c.dateRange.openPicker
    );

    await page.goto('/categories');
    await expect(
      page.getByRole('button', { name: categories.list.amountHint, exact: true }).locator('..')
    ).toContainText(categories.table.name);
    await checkHelp(
      page,
      categories.list.amountHint,
      testInfo.outputPath(`categories-tooltip-${language}.png`)
    );
    await page.screenshot({ path: testInfo.outputPath(`categories-help-${language}.png`) });
    const savings = await api<{ id: number }>(page, '/accounts', {
      name: 'Help savings',
      type: 'SAVINGS',
      currency: 'EUR',
      initialBalance: '1000',
      openingDate: '2026-01-01',
      isInterestEnabled: true,
      interestPeriod: 'ANNUAL',
    });
    await api(page, `/accounts/${savings.id}/interest-variations`, {
      rate: '3',
      validFrom: '2026-01-01',
      taxRate: '0',
    });
    await page.goto('/accounts');
    await page.getByText('Help savings', { exact: true }).click();
    await page.getByRole('button', { name: a.detail.interest, exact: true }).click();
    await checkHelp(
      page,
      a.interest.estimateExplanation,
      testInfo.outputPath(`interest-tooltip-${language}.png`)
    );
    await page.screenshot({ path: testInfo.outputPath(`interest-help-mobile-${language}.png`) });
    await page.setViewportSize({ width: 1280, height: 1000 });
    await page.getByRole('button', { name: a.interest.addRate, exact: true }).click();
    await checkPickerAnchor(
      page,
      page.getByRole('textbox', { name: a.interest.addDialog.effectiveDate, exact: false }),
      c.dateRange.openPicker
    );
  });
}
