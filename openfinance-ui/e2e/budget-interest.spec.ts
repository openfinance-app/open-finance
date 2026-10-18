import { expect, test, type Page } from '@playwright/test';
import Decimal from 'decimal.js';
import { loginAs, registerWithRetry } from './helpers/auth';
import accountEn from '../public/locales/en/accounts.json' with { type: 'json' };
import accountFr from '../public/locales/fr/accounts.json' with { type: 'json' };
import budgetEn from '../public/locales/en/budgets.json' with { type: 'json' };
import budgetFr from '../public/locales/fr/budgets.json' with { type: 'json' };
import dashboardEn from '../public/locales/en/dashboard.json' with { type: 'json' };
import dashboardFr from '../public/locales/fr/dashboard.json' with { type: 'json' };
import commonEn from '../public/locales/en/common.json' with { type: 'json' };
import commonFr from '../public/locales/fr/common.json' with { type: 'json' };

async function api<T>(page: Page, path: string, method = 'GET', body?: unknown): Promise<T> {
  return page.evaluate(
    async ({ path, method, body }) => {
      const response = await fetch(`/api/v1${path}`, {
        method,
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${localStorage.getItem('auth_token') || sessionStorage.getItem('auth_token')}`,
          'X-Encryption-Session': sessionStorage.getItem('encryption_session') || '',
        },
        body: body === undefined ? undefined : JSON.stringify(body),
      });
      if (!response.ok) throw new Error(`${method} ${path}: HTTP ${response.status}`);
      return response.json();
    },
    { path, method, body }
  );
}

for (const language of ['en', 'fr'] as const) {
  test(`monthly budget history and editable interest value dates (${language})`, async ({
    page,
    request,
  }, testInfo) => {
    test.setTimeout(180_000);
    await page.setViewportSize({ width: 1280, height: 1100 });
    await page.emulateMedia({ reducedMotion: 'reduce' });
    const a = language === 'fr' ? accountFr : accountEn;
    const b = language === 'fr' ? budgetFr : budgetEn;
    const d = language === 'fr' ? dashboardFr : dashboardEn;
    const c = language === 'fr' ? commonFr : commonEn;
    const username = `periods_${language}_${Date.now()}`;
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
    await api(page, '/users/me/onboarding', 'POST', {
      country: 'FR',
      baseCurrency: 'EUR',
      secondaryCurrency: '',
      language,
      dateFormat: 'DD/MM/YYYY',
      numberFormat: '1,234.56',
      amountDisplayMode: 'base',
    });
    await api(page, '/users/me/base-currency', 'PUT', { baseCurrency: 'EUR' });
    const category = await api<{ id: number }>(page, '/categories', 'POST', {
      name: 'Alimentation',
    });
    const cash = await api<{ id: number }>(page, '/accounts', 'POST', {
      name: 'Budget cash',
      type: 'CHECKING',
      currency: 'EUR',
      initialBalance: '10000',
      openingDate: '2026-01-01',
    });
    const expiredCategory = await api<{ id: number }>(page, '/categories', 'POST', {
      name: 'Previous allowance',
    });
    await api(page, '/budgets', 'POST', {
      categoryId: expiredCategory.id,
      amount: '300',
      currency: 'EUR',
      period: 'MONTHLY',
      startDate: '2025-12-01',
      endDate: '2025-12-31',
      rollover: false,
    });
    await page.goto('/budget');
    await expect(page.getByText(b.summary.noActiveBudgets, { exact: true })).toBeVisible();
    await expect(
      page.getByText(b.summary.activeCount.replace('{{active}}', '0').replace('{{total}}', '1'), {
        exact: true,
      })
    ).toBeVisible();
    await expect(
      page.getByRole('button', {
        name: b.card.viewHistory.replace('{{category}}', 'Previous allowance'),
      })
    ).toBeVisible();
    const summaryAllowance = page
      .getByText(b.summary.totalBudgeted, { exact: true })
      .locator('../..');
    await expect(summaryAllowance).toContainText('€0.00');
    await page.screenshot({
      path: testInfo.outputPath(`budget-no-active-${language}.png`),
      fullPage: true,
      animations: 'disabled',
    });
    const amounts = [
      '440.35',
      '380.34',
      '464.76',
      '437.19',
      '569.745026627097949218',
      '594.17',
      '645.98',
      '548.36',
      '761.23',
      '154.44',
    ];
    for (const [month, amount] of amounts.entries()) {
      await api(page, '/transactions', 'POST', {
        accountId: cash.id,
        categoryId: category.id,
        amount,
        currency: 'EUR',
        type: 'EXPENSE',
        date: `2026-${String(month + 1).padStart(2, '0')}-01`,
      });
    }
    const budget = await api<{ id: number }>(page, '/budgets', 'POST', {
      categoryId: category.id,
      amount: '300',
      currency: 'EUR',
      period: 'MONTHLY',
      startDate: '2026-01-01',
      endDate: '2026-12-31',
      rollover: true,
      recurring: true,
    });
    const history = await api<{ history: unknown[]; totalBudgeted: number }>(
      page,
      `/budgets/${budget.id}/history`
    );
    expect(history.history).toHaveLength(12);
    expect(history.totalBudgeted).toBe(3600);
    await page.goto('/budget');
    await expect(page.getByText(b.summary.noActiveBudgets, { exact: true })).not.toBeVisible();
    await expect(
      page.getByText(b.summary.activeCount.replace('{{active}}', '1').replace('{{total}}', '2'), {
        exact: true,
      })
    ).toBeVisible();
    await expect(summaryAllowance).toContainText('€300.00');
    await page
      .getByRole('button', { name: b.card.viewHistory.replace('{{category}}', 'Alimentation') })
      .click();
    const breakdown = page.getByRole('button', {
      name: b.detail.periodBreakdown_other.replace('{{count}}', '12'),
    });
    await expect(breakdown).toHaveAttribute('aria-expanded', 'false');
    await expect(page.getByRole('table')).not.toBeVisible();
    await expect(page.getByText(b.detail.allowanceExplanation, { exact: true })).not.toBeVisible();
    await page.getByRole('button', { name: b.detail.allowanceExplanation, exact: true }).focus();
    await expect(page.getByRole('tooltip')).toContainText(b.detail.allowanceExplanation);
    await expect(breakdown).toHaveAttribute('aria-expanded', 'false');
    await breakdown.focus();
    await expect(page.getByRole('tooltip')).not.toBeVisible();
    // Recharts renders all twelve allowance bars in the real chart, not just a history table.
    await expect(page.locator('.recharts-bar-rectangle')).toHaveCount(22);
    await expect
      .poll(async () =>
        page
          .locator('.recharts-bar-rectangle')
          .evaluateAll(bars => bars.filter(bar => bar.getBoundingClientRect().height > 60).length)
      )
      .toBe(21);
    let previousHeights = '';
    let stableSamples = 0;
    await expect
      .poll(
        async () => {
          const heights = await page
            .locator('.recharts-bar-rectangle')
            .evaluateAll(bars =>
              JSON.stringify(bars.map(bar => bar.getBoundingClientRect().height))
            );
          stableSamples = heights === previousHeights ? stableSamples + 1 : 0;
          previousHeights = heights;
          return stableSamples;
        },
        { intervals: [100] }
      )
      .toBeGreaterThanOrEqual(3);
    await page.screenshot({
      path: testInfo.outputPath(`budget-collapsed-${language}.png`),
      fullPage: true,
      animations: 'disabled',
    });
    await breakdown.click();
    await expect(page.getByRole('table').getByRole('row')).toHaveCount(13);
    await expect(page.getByRole('columnheader', { name: b.card.carryover })).toBeVisible();
    await expect(page.getByRole('columnheader', { name: b.detail.columns.result })).toBeVisible();
    const firstPeriod = page.getByRole('table').getByRole('row').nth(1);
    const firstResult = firstPeriod.getByRole('cell').nth(1);
    await expect(firstPeriod.getByRole('cell')).toHaveCount(5);
    const overBudgetPercent = b.detail.overBudgetPercent.replace('{{percent}}', '46.8');
    await expect(firstResult).toContainText(overBudgetPercent);
    await expect(firstResult).toContainText('€140.35');
    const octoberResult = page.getByRole('table').getByRole('row').nth(10).getByRole('cell').nth(1);
    await expect(octoberResult).toContainText(b.card.remaining);
    await expect(octoberResult).toContainText('€145.56');
    await page.locator('.recharts-bar-rectangle').nth(12).hover();
    await expect(page.getByRole('tooltip')).toContainText(overBudgetPercent);
    await expect(page.getByRole('tooltip')).toContainText('€140.35');
    await page.screenshot({
      path: testInfo.outputPath(`budget-overage-tooltip-${language}.png`),
      fullPage: true,
      animations: 'disabled',
    });
    await page.getByRole('heading', { name: b.detail.spendingByPeriod }).hover();
    await page.screenshot({
      path: testInfo.outputPath(`budget-months-${language}.png`),
      fullPage: true,
      animations: 'disabled',
    });
    await page.setViewportSize({ width: 390, height: 844 });
    await firstResult.scrollIntoViewIfNeeded();
    const tableViewport = page.getByRole('table').locator('..');
    await tableViewport.evaluate(element => {
      element.scrollLeft = 0;
    });
    const visibleTableBounds = await tableViewport.boundingBox();
    const resultBounds = await firstResult.boundingBox();
    expect(visibleTableBounds).not.toBeNull();
    expect(resultBounds).not.toBeNull();
    expect(resultBounds!.x + resultBounds!.width).toBeLessThanOrEqual(
      visibleTableBounds!.x + visibleTableBounds!.width + 1
    );
    await page.screenshot({
      path: testInfo.outputPath(`budget-periods-mobile-${language}.png`),
      fullPage: true,
      animations: 'disabled',
    });
    await page.setViewportSize({ width: 1280, height: 1100 });

    const savings = await api<{ id: number }>(page, '/accounts', 'POST', {
      name: 'Fortnight savings',
      type: 'SAVINGS',
      currency: 'EUR',
      initialBalance: '0',
      openingDate: '2024-12-20',
      isInterestEnabled: true,
      interestPeriod: 'ANNUAL',
    });
    for (const [date, amount] of [
      ['2024-12-20', '22950'],
      ['2025-12-31', '495.16'],
    ]) {
      await api(page, '/transactions', 'POST', {
        accountId: savings.id,
        amount,
        currency: 'EUR',
        type: 'INCOME',
        date,
      });
    }
    for (const [validFrom, rate] of [
      ['2024-02-01', '3'],
      ['2025-02-01', '2.4'],
      ['2025-08-01', '1.7'],
      ['2026-02-01', '1.5'],
      ['2026-08-01', '1.7'],
    ]) {
      await api(page, `/accounts/${savings.id}/interest-variations`, 'POST', {
        rate,
        validFrom,
        taxRate: '0',
        creditValueDateMode: validFrom === '2025-08-01' ? 'TRANSACTION_DATE' : 'FORTNIGHT',
        debitValueDateMode: 'FORTNIGHT',
        dayCountBasis: 'ACTUAL_365',
      });
    }
    await page.goto('/accounts');
    const card = page.getByText('Fortnight savings', { exact: true }).locator('../../../../..');
    await expect(card.getByText(a.card.totalEarned, { exact: true })).toBeVisible();
    await expect(card.getByText('€375.32', { exact: true })).toBeVisible();
    await page.getByText('Fortnight savings', { exact: true }).click();
    await page.getByRole('button', { name: a.detail.interest, exact: true }).click();
    const interest = page
      .getByRole('heading', { name: a.interest.historyTitle })
      .locator('../../..');
    await expect(interest.getByRole('combobox')).toHaveCount(0);
    await expect(
      interest.getByRole('button', { name: a.interest.editRate, exact: true })
    ).toHaveCount(5);
    const projection = interest.getByText(a.interest.projectedNet, { exact: true }).locator('..');
    const earned = interest.getByText(a.interest.earnedSoFar, { exact: true }).locator('..');
    const lifetime = interest.getByText(a.interest.totalEarned, { exact: true }).locator('..');
    await expect(projection).toContainText('€375.32');
    await expect(interest.getByText('€398.57', { exact: true })).toHaveCount(0);
    // The August 2025 row is now visible in the complete rate history.
    await interest.getByRole('button', { name: a.interest.editRate, exact: true }).nth(2).click();
    await page
      .getByRole('combobox', { name: a.interest.creditValueDateMode, exact: true })
      .selectOption('FORTNIGHT');
    await page.getByRole('button', { name: a.interest.addDialog.saveRate, exact: true }).click();
    await expect(page.getByRole('dialog', { name: a.interest.editRate, exact: true })).toHaveCount(
      0
    );
    const summary = await api<{
      estimate: number;
      yearToDate: number;
      historicalAccumulated: number;
    }>(page, `/accounts/${savings.id}/interest-estimate`);
    expect(summary.estimate).toBe(375.32);
    expect(summary.historicalAccumulated).toBeGreaterThan(summary.yearToDate);
    const money = (value: number) => `€${value.toFixed(2)}`;
    await expect(earned).toContainText(money(summary.yearToDate));
    await expect(lifetime).toContainText(money(summary.historicalAccumulated));
    await testInfo.attach('interest-summary', {
      body: JSON.stringify(summary, null, 2),
      contentType: 'application/json',
    });

    const dashboardCard = page
      .getByRole('heading', { name: d.estimatedInterest.title, exact: true })
      .locator('../../..');
    const annualLabel = d.estimatedInterest.projectedCurrentYear.replace(
      '{{year}}',
      String(new Date().getFullYear())
    );
    const dashboardRow = dashboardCard.getByRole('button', {
      name: d.estimatedInterest.viewDetails.replace('{{account}}', 'Fortnight savings'),
    });
    const visitDashboard = async (expected: string) => {
      await page.getByRole('button', { name: c.aria.closeModal, exact: true }).click();
      await page.locator('a[href="/dashboard"]').first().click();
      await page
        .getByRole('group', { name: c.periodSelector.label })
        .getByRole('button', { name: c.periodSelector.presets.YTD, exact: true })
        .click();
      await expect(dashboardCard.getByText(`${annualLabel}:`)).toContainText(expected);
      await expect(dashboardRow).toContainText(expected);
      await expect(dashboardRow).toContainText(money(summary.yearToDate));
    };
    const returnToInterest = async () => {
      await dashboardRow.click();
      await page.getByRole('button', { name: a.detail.interest, exact: true }).click();
    };
    // Warm the dashboard cache before mutations; navigating in the app must show updated values.
    await visitDashboard('€375.32');
    await dashboardCard.scrollIntoViewIfNeeded();
    await dashboardCard.screenshot({
      path: testInfo.outputPath(`dashboard-interest-${language}.png`),
    });
    await page
      .getByRole('group', { name: c.periodSelector.label })
      .getByRole('button', { name: c.periodSelector.presets.MTD, exact: true })
      .click();
    await expect(dashboardCard.getByText(`${annualLabel}:`)).toContainText('€375.32');
    const monthEarned = await api<{ totalEarned: number }>(
      page,
      `/dashboard/estimated-interest?startDate=${new Date().getFullYear()}-${String(new Date().getMonth() + 1).padStart(2, '0')}-01&endDate=${new Date().getFullYear()}-${String(new Date().getMonth() + 1).padStart(2, '0')}-${String(new Date().getDate()).padStart(2, '0')}`
    );
    await expect(dashboardRow).toContainText(money(monthEarned.totalEarned));
    await returnToInterest();

    // A future rate affects only the remaining calendar-year projection.
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const futureDate = `${String(tomorrow.getDate()).padStart(2, '0')}/${String(tomorrow.getMonth() + 1).padStart(2, '0')}/${tomorrow.getFullYear()}`;
    const remainingDays = Math.max(
      0,
      Math.round(
        (Date.UTC(2027, 0, 1) -
          Date.UTC(tomorrow.getFullYear(), tomorrow.getMonth(), tomorrow.getDate())) /
          86_400_000
      )
    );
    const projectedWithExtraRate = (extraRate: string) =>
      `€${new Decimal('23445.16')
        .mul(
          new Decimal('0.017')
            .mul(31)
            .plus(new Decimal('0.015').mul(181))
            .plus(new Decimal('0.017').mul(153))
        )
        .plus(new Decimal('23445.16').mul(extraRate).mul(remainingDays))
        .div(365)
        .toFixed(2)}`;
    await interest.getByRole('button', { name: a.interest.addRate, exact: true }).click();
    await page.getByLabel(a.interest.addDialog.effectiveDate, { exact: false }).fill(futureDate);
    await page.getByLabel(a.interest.addDialog.interestRate, { exact: false }).fill('2');
    await page.getByRole('button', { name: a.interest.addDialog.saveRate, exact: true }).click();
    await expect(projection).toContainText(projectedWithExtraRate('0.003'));
    await visitDashboard(projectedWithExtraRate('0.003'));
    await returnToInterest();
    await expect(earned).toContainText(money(summary.yearToDate));
    await expect(lifetime).toContainText(money(summary.historicalAccumulated));
    await interest.getByRole('button', { name: a.interest.editRate, exact: true }).last().click();
    await page.getByLabel(a.interest.addDialog.interestRate, { exact: false }).fill('2.5');
    await page.getByRole('button', { name: a.interest.addDialog.saveRate, exact: true }).click();
    await expect(projection).toContainText(projectedWithExtraRate('0.008'));
    await visitDashboard(projectedWithExtraRate('0.008'));
    await returnToInterest();
    page.once('dialog', dialog => dialog.accept());
    await interest.getByRole('button', { name: a.interest.deleteRate, exact: true }).last().click();
    await expect(projection).toContainText('€375.32');
    await visitDashboard('€375.32');
    await returnToInterest();
    await expect(earned).toContainText(money(summary.yearToDate));
    await expect(lifetime).toContainText(money(summary.historicalAccumulated));
    expect((await api<{ ownBalance: number }>(page, `/accounts/${savings.id}`)).ownBalance).toBe(
      23445.16
    );
    expect(await api<unknown[]>(page, `/transactions?accountId=${savings.id}`)).toHaveLength(2);
    await page.getByRole('heading', { name: a.interest.historyTitle }).scrollIntoViewIfNeeded();
    await page.screenshot({
      path: testInfo.outputPath(`interest-summary-${language}.png`),
      fullPage: true,
      animations: 'disabled',
    });
    await page.setViewportSize({ width: 390, height: 844 });
    await expect(interest.getByRole('combobox')).toHaveCount(0);
    await expect(projection).toContainText('€375.32');
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)
    ).toBe(true);
    const addRateBounds = await page
      .getByRole('button', { name: a.interest.addRate, exact: true })
      .boundingBox();
    expect(addRateBounds).not.toBeNull();
    expect(addRateBounds!.x + addRateBounds!.width).toBeLessThanOrEqual(374);
    await page.screenshot({
      path: testInfo.outputPath(`interest-mobile-${language}.png`),
      fullPage: true,
      animations: 'disabled',
    });
    await page.goto('/accounts');
    await page.setViewportSize({ width: 1280, height: 1100 });
    await expect(card.getByText(a.card.totalEarned, { exact: true })).toBeVisible();
    await expect(
      card.getByText(money(summary.historicalAccumulated), { exact: true })
    ).toBeVisible();
    await expect(card.getByText('€375.32', { exact: true })).toBeVisible();
    await page.screenshot({
      path: testInfo.outputPath(`interest-card-${language}.png`),
      fullPage: true,
      animations: 'disabled',
    });
    await page.locator('a[href="/dashboard"]').first().click();
    await page
      .getByRole('group', { name: c.periodSelector.label })
      .getByRole('button', { name: c.periodSelector.presets.YTD, exact: true })
      .click();
    await page.setViewportSize({ width: 390, height: 844 });
    await expect(dashboardRow).toContainText('€375.32');
    await dashboardCard.scrollIntoViewIfNeeded();
    await dashboardCard.screenshot({
      path: testInfo.outputPath(`dashboard-interest-mobile-${language}.png`),
    });
  });
}
