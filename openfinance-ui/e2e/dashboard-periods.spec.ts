import { test, expect, type Page, type Response } from '@playwright/test';
import { loginAs, registerWithRetry } from './helpers/auth';
import common from '../public/locales/en/common.json' with { type: 'json' };

test.use({ hasTouch: true });

async function api<T>(page: Page, path: string, body: unknown, method = 'POST'): Promise<T> {
  const result = await page.evaluate(
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
      return { status: response.status, text: await response.text() };
    },
    { path, body, method }
  );
  expect(result.status, result.text).toBeLessThan(300);
  return (result.text ? JSON.parse(result.text) : null) as T;
}

function displayDate(iso: string): string {
  const [year, month, day] = iso.split('-');
  return `${month}/${day}/${year}`;
}

async function selectCustom(page: Page, from: string, to: string): Promise<void> {
  const selector = page.getByRole('group', { name: 'Time period' });
  await selector.getByRole('button', { name: /Custom range|→/ }).click();
  const dialog = page.getByRole('dialog');
  const currentFrom = await dialog.locator('input[type="date"]').first().inputValue();
  const setDate = async (label: string, iso: string): Promise<void> => {
    await dialog.getByLabel(label, { exact: true }).fill(displayDate(iso));
    await dialog.getByLabel(label, { exact: true }).press('Tab');
  };
  if (to < currentFrom) {
    await setDate('From', from);
    await setDate('To', to);
  } else {
    await setDate('To', to);
    await setDate('From', from);
  }
  await page.keyboard.press('Escape');
  const trigger = selector.getByRole('button', {
    name: `${displayDate(from)} → ${displayDate(to)}`,
  });
  await expect(trigger).toHaveAttribute('aria-pressed', 'true');
  // Wait for the date popover to restore focus before scrolling to a card tooltip.
  await expect(trigger).toBeFocused();
}

function responseFor(page: Page, path: string, from: string, to: string): Promise<Response> {
  return page.waitForResponse(response => {
    const url = new URL(response.url());
    return (
      url.pathname.endsWith(path) &&
      url.searchParams.get('startDate') === from &&
      url.searchParams.get('endDate') === to
    );
  });
}

for (const viewport of [
  { width: 1280, height: 900 },
  { width: 390, height: 844 },
]) {
  test(`custom periods and observed balances at ${viewport.width}px`, async ({
    page,
    request,
  }, testInfo) => {
    await page.setViewportSize(viewport);
    await page.clock.setFixedTime(new Date('2026-10-17T12:00:00'));
    const errors: string[] = [];
    page.on('pageerror', error => errors.push(error.message));
    const requests: URL[] = [];
    page.on('request', request => requests.push(new URL(request.url())));
    const username = `period_${viewport.width}_${Date.now()}`;
    const credentials = { username, password: 'Password123!', masterPassword: 'RealMaster123!' };
    const registered = await registerWithRetry(request, {
      ...credentials,
      email: `${username}@example.invalid`,
      skipSeeding: true,
    });
    expect(registered.status()).toBe(201);
    await loginAs(page, credentials);
    const selector = page.getByRole('group', { name: 'Time period' });
    await expect(selector.getByRole('button')).toHaveCount(8);
    await expect(
      selector.getByRole('button', { name: common.periodSelector.presets.MTD })
    ).toHaveAttribute('aria-pressed', 'true');
    await expect(selector.getByRole('button', { name: /Month \/ Year/ })).toHaveCount(0);
    await expect
      .poll(() =>
        requests.some(
          url =>
            url.pathname.endsWith('/networth-history') &&
            url.searchParams.get('startDate') === '2026-10-01' &&
            url.searchParams.get('endDate') === '2026-10-17'
        )
      )
      .toBe(true);

    await api(page, '/users/me/base-currency', { baseCurrency: 'EUR' }, 'PUT');
    const account = await api<{ id: number }>(page, '/accounts', {
      name: 'Calendar cash',
      type: 'CHECKING',
      currency: 'EUR',
      initialBalance: 1000,
      openingDate: '2025-01-01',
    });
    for (const [date, amount] of [
      ['2026-05-31', 500],
      ['2026-06-15', 100],
      ['2026-07-15', 150],
      ['2026-09-15', 20],
      ['2026-10-05', 40],
    ]) {
      await api(page, '/transactions', {
        accountId: account.id,
        date,
        amount,
        currency: 'EUR',
        type: 'EXPENSE',
      });
    }
    await page.reload();
    const currentMonthCaption = page.getByRole('button', { name: 'vs last 17d', exact: true });
    await expect(currentMonthCaption).toBeVisible();
    await currentMonthCaption.focus();
    await expect(page.getByRole('tooltip')).toHaveText('Selected period: 10/01/2026 → 10/17/2026');
    await page.screenshot({
      path: testInfo.outputPath('current-month-period-tooltip.png'),
      animations: 'disabled',
    });
    await page.keyboard.press('Escape');
    const sankeyCard = page.getByRole('group', { name: 'Cash flow diagram' }).locator('../..');
    await expect(sankeyCard.getByRole('button', { name: 'last 17d', exact: true })).toBeVisible();
    const julyResponse = responseFor(page, '/cashflow-sankey', '2026-07-01', '2026-07-31');
    const juneResponse = responseFor(page, '/cashflow-sankey', '2026-06-01', '2026-06-30');
    await selectCustom(page, '2026-07-01', '2026-07-31');
    const [july, june] = await Promise.all([julyResponse, juneResponse]);
    expect(july.status()).toBe(200);
    expect(june.status()).toBe(200);
    expect((await july.json()).totalExpenses).toBe(150);
    expect((await june.json()).totalExpenses).toBe(100);
    await expect(page.getByRole('status').filter({ hasText: 'Compared with' })).toContainText(
      '06/30/2026'
    );
    // Cash Flow, Sankey and Recent Transactions must expose the same selected dates.
    const customCaptions = page.getByRole('button', { name: '31d', exact: true });
    await expect(customCaptions).toHaveCount(3);
    for (const caption of await customCaptions.all()) {
      if (viewport.width === 390) {
        await caption.tap();
      } else {
        await caption.hover();
      }
      await expect(page.getByRole('tooltip')).toHaveText(
        'Selected period: 07/01/2026 → 07/31/2026'
      );
      await page.keyboard.press('Escape');
      await expect(page.getByRole('tooltip')).toHaveCount(0);
    }
    expect(requests.some(url => url.searchParams.get('startDate') === '2026-05-31')).toBe(false);

    const octoberHistory = responseFor(page, '/networth-history', '2026-10-01', '2026-10-31');
    const september = responseFor(page, '/cashflow-sankey', '2026-09-01', '2026-09-30');
    await selectCustom(page, '2026-10-01', '2026-10-31');
    const historyResponse = await octoberHistory;
    expect(historyResponse.status()).toBe(200);
    const points = (await historyResponse.json()) as Array<{ date: string; netWorth: number }>;
    expect(points.length).toBeGreaterThan(1);
    expect(points.every(point => point.date >= '2026-10-01' && point.date <= '2026-10-17')).toBe(
      true
    );
    expect(points.at(-1)).toMatchObject({ date: '2026-10-17', netWorth: 190 });
    expect((await (await september).json()).totalExpenses).toBe(20);
    await expect(page.getByRole('status').filter({ hasText: 'Compared with' })).toContainText(
      '09/30/2026'
    );
    await expect(page.getByText('Data through 10/17/2026', { exact: true })).toHaveCount(2);
    const chart = page
      .getByRole('heading', { name: 'Net Worth Trend', exact: true })
      .locator('../../..');
    await chart.scrollIntoViewIfNeeded();
    const caption = chart.getByRole('button', { name: 'Trend for 31d', exact: true });
    if (viewport.width === 390) {
      await caption.tap();
    } else {
      await caption.hover();
    }
    await expect(page.getByRole('tooltip')).toHaveText('Selected period: 10/01/2026 → 10/31/2026');
    await page.keyboard.press('Escape');
    await expect(chart.getByText('10/17/2026', { exact: true })).toBeVisible();
    await expect(chart.getByText('10/31/2026', { exact: true })).toHaveCount(0);
    // Actual observations fill the axis; no blank October 18–31 tail is shown.
    await expect
      .poll(() =>
        chart.evaluate(element => {
          const curve = element.querySelector<SVGGraphicsElement>('.recharts-area-curve');
          const axis = element.querySelector<SVGLineElement>(
            '.recharts-xAxis .recharts-cartesian-axis-line'
          );
          if (!curve || !axis) return 0;
          return curve.getBBox().width / (axis.x2.baseVal.value - axis.x1.baseVal.value);
        })
      )
      .toBeGreaterThan(0.99);
    expect(
      await chart.evaluate(element => {
        const curve = element.querySelector<SVGGraphicsElement>('.recharts-area-curve')!;
        const axis = element.querySelector<SVGLineElement>(
          '.recharts-xAxis .recharts-cartesian-axis-line'
        )!;
        return curve.getBBox().width / (axis.x2.baseVal.value - axis.x1.baseVal.value);
      })
    ).toBeLessThanOrEqual(1.01);
    // Recharts reveals its initial line through a JavaScript-animated clip rect.
    // Wait until it is drawn before saving the visual evidence.
    await expect
      .poll(() =>
        chart.evaluate(element => {
          const clip = element.querySelector<SVGRectElement>(
            'clipPath[id^="animationClipPath"] rect'
          );
          const curve = element.querySelector<SVGGraphicsElement>('.recharts-area-curve');
          return Boolean(curve && (!clip || clip.width.baseVal.value >= curve.getBBox().width));
        })
      )
      .toBe(true);
    await page.screenshot({
      path: testInfo.outputPath('future-month-trend.png'),
      animations: 'disabled',
    });
    await page.reload();
    await expect(selector.getByRole('button', { name: '10/01/2026 → 10/31/2026' })).toHaveAttribute(
      'aria-pressed',
      'true'
    );

    const yearComparison = responseFor(page, '/cashflow-sankey', '2025-01-01', '2025-12-31');
    await selectCustom(page, '2026-01-01', '2026-12-31');
    expect((await yearComparison).status()).toBe(200);
    await expect(page.getByRole('status').filter({ hasText: 'Compared with' })).toContainText(
      '12/31/2025'
    );

    const futureHistory = responseFor(page, '/networth-history', '2027-01-01', '2027-12-31');
    await selectCustom(page, '2027-01-01', '2027-12-31');
    const emptyHistory = await futureHistory;
    expect(emptyHistory.status()).toBe(200);
    expect(await emptyHistory.json()).toEqual([]);
    await expect(page.getByText('No historical data available to show trend.')).toBeVisible();
    await expect(page.getByText('No change available for this period.')).toBeVisible();

    // Reload clears cached rolling requests made while editing Custom dates.
    await page.reload();
    await page.waitForLoadState('networkidle');
    for (const [label, from] of [
      ['1D', '2026-10-17'],
      ['7D', '2026-10-11'],
      ['30D', '2026-09-18'],
    ]) {
      const history = responseFor(page, '/networth-history', from, '2026-10-17');
      await selector.getByRole('button', { name: label, exact: true }).click();
      const response = await history;
      expect(response.status()).toBe(200);
      const observations = (await response.json()) as Array<{ date: string }>;
      expect(observations.every(point => point.date >= from && point.date <= '2026-10-17')).toBe(
        true
      );
      if (label === '7D') {
        const periodCaption = sankeyCard.getByRole('button', { name: 'last 7d', exact: true });
        await periodCaption.scrollIntoViewIfNeeded();
        if (viewport.width === 390) {
          await periodCaption.tap();
        } else {
          await periodCaption.hover();
        }
        await expect(page.getByRole('tooltip')).toHaveText(
          'Selected period: 10/11/2026 → 10/17/2026'
        );
        await page.screenshot({
          path: testInfo.outputPath('sankey-period-tooltip.png'),
          animations: 'disabled',
        });
        await page.keyboard.press('Escape');
      }
      if (label === '1D') {
        expect(observations).toHaveLength(1);
        await chart.scrollIntoViewIfNeeded();
        await expect(chart.locator('.recharts-area-dot')).toBeVisible();
        await page.screenshot({
          path: testInfo.outputPath('one-day-trend.png'),
          animations: 'disabled',
        });
      }
    }
    await selector.scrollIntoViewIfNeeded();
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)
    ).toBe(true);
    expect(errors).toEqual([]);
  });
}
