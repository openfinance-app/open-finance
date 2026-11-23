import { expect, test, type Page } from '@playwright/test';
import { loginAs, registerWithRetry } from './helpers/auth';
import dashboardEn from '../public/locales/en/dashboard.json' with { type: 'json' };
import dashboardFr from '../public/locales/fr/dashboard.json' with { type: 'json' };
import commonEn from '../public/locales/en/common.json' with { type: 'json' };
import commonFr from '../public/locales/fr/common.json' with { type: 'json' };

async function api<T>(page: Page, path: string, body?: unknown): Promise<T> {
  const result = await page.evaluate(
    async ({ path, body }) => {
      const response = await fetch(`/api/v1${path}`, {
        method: body === undefined ? 'GET' : 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${localStorage.getItem('auth_token') || sessionStorage.getItem('auth_token')}`,
          'X-Encryption-Session': sessionStorage.getItem('encryption_session') || '',
        },
        body: body === undefined ? undefined : JSON.stringify(body),
      });
      return { status: response.status, body: await response.json() };
    },
    { path, body }
  );
  expect(result.status).toBeLessThan(300);
  return result.body as T;
}

test.beforeEach(async ({ page, request }) => {
  const username = `cards_${Date.now()}`;
  const credentials = { username, password: 'Password123!', masterPassword: 'RealMaster123!' };
  const registration = await registerWithRetry(request, {
    ...credentials,
    email: `${username}@example.invalid`,
    skipSeeding: true,
  });
  expect(registration.status()).toBe(201);
  await loginAs(page, credentials);
});

test('keeps the French empty state and partial refresh inside a resized insights card', async ({
  page,
}, testInfo) => {
  const labels = dashboardFr.insightsCard;
  await api(page, '/users/me/onboarding', {
    country: 'FR',
    baseCurrency: 'EUR',
    secondaryCurrency: '',
    language: 'fr',
    dateFormat: 'DD/MM/YYYY',
    numberFormat: '1,234.56',
    amountDisplayMode: 'base',
  });
  await page.route('**/api/v1/insights/generate', route =>
    route.fulfill({ json: [], headers: { 'X-Insights-Unavailable-Sources': 'REGION_COMPARISON' } })
  );
  await page.reload();
  const tile = page.locator('.react-grid-item').filter({
    has: page.getByRole('heading', { name: labels.title, exact: true }),
  });
  const generate = tile.getByRole('button', { name: labels.generate });
  await generate.click();
  const notice = tile.getByRole('status');
  await expect(notice).toHaveText(labels.partialRefresh);
  for (const [viewport, width, height] of [
    [1440, 410, 264],
    [390, 320, 208],
  ]) {
    await page.setViewportSize({ width: viewport, height: 900 });
    await tile.evaluate(
      (el, [w, h]) => {
        el.style.width = w + 'px';
        el.style.height = h + 'px';
      },
      [width, height]
    );
    await expect
      .poll(() =>
        tile.evaluate(el => [el.getBoundingClientRect().width, el.getBoundingClientRect().height])
      )
      .toEqual([width, height]);
    await tile.evaluate(el => el.scrollIntoView({ block: 'center', behavior: 'instant' }));
    for (const item of [generate, tile.getByText(labels.noInsights), notice]) {
      const box = await item.boundingBox();
      const frame = await tile.boundingBox();
      expect(box!.x).toBeGreaterThanOrEqual(frame!.x);
      expect(box!.x + box!.width).toBeLessThanOrEqual(frame!.x + frame!.width + 1);
      expect(box!.y).toBeGreaterThanOrEqual(frame!.y);
      expect(box!.y + box!.height).toBeLessThanOrEqual(frame!.y + frame!.height + 1);
    }
    await testInfo.attach(`empty-insights-${width}`, {
      body: await tile.screenshot({ animations: 'disabled' }),
      contentType: 'image/png',
    });
  }
});

for (const width of [1280, 390]) {
  test.describe(width < 640 ? 'Touch' : 'Desktop', () => {
    test.use({ hasTouch: width < 640 });

    test(`clear all insights from the card at ${width}px`, async ({ page }, testInfo) => {
      await page.setViewportSize({ width, height: 900 });
      const language = width === 390 ? 'fr' : 'en';
      const labels = (language === 'fr' ? dashboardFr : dashboardEn).insightsCard;
      const common = language === 'fr' ? commonFr : commonEn;
      if (language === 'fr') {
        await api(page, '/users/me/onboarding', {
          country: 'FR',
          baseCurrency: 'EUR',
          secondaryCurrency: '',
          language,
          dateFormat: 'DD/MM/YYYY',
          numberFormat: '1,234.56',
          amountDisplayMode: 'base',
        });
      }
      for (let index = 1; index <= 4; index++) {
        await api(page, '/accounts', {
          name: `Card checking ${index}`,
          type: 'CHECKING',
          currency: 'EUR',
          initialBalance: 25,
        });
      }
      const generated = await api<{ type: string }[]>(page, '/insights/generate', {});
      expect(generated).toHaveLength(4);
      let clearRequests = 0;
      page.on('request', request => {
        if (request.method() === 'POST' && request.url().endsWith('/insights/dismiss-all'))
          clearRequests++;
      });
      await page.reload();
      const title = language === 'fr' ? 'Solde de compte faible' : 'Low Account Balance';
      const rows = page.getByRole('heading', { name: title, exact: true });
      await expect(rows).toHaveCount(3);
      await rows
        .first()
        .evaluate(row => row.scrollIntoView({ block: 'center', behavior: 'instant' }));
      await expect(
        page.getByText(language === 'fr' ? '3 sur 4' : '3 of 4', { exact: true })
      ).toBeVisible();
      await rows.first().click();
      const detail = page.getByRole('dialog', { name: title });
      await expect(detail).toContainText(
        language === 'fr' ? 'Le solde de votre compte' : 'account balance is low'
      );
      await page.keyboard.press('Escape');
      await page
        .getByRole('heading', { name: labels.title, exact: true })
        .evaluate(heading => heading.scrollIntoView({ block: 'center' }));
      await page.screenshot({
        path: testInfo.outputPath('insights-card.png'),
        animations: 'disabled',
      });

      await page.getByRole('button', { name: labels.dismiss, exact: true }).first().click();
      await expect(
        page.getByText(language === 'fr' ? '3 au total' : '3 total', { exact: true })
      ).toBeVisible();
      const clear = page.getByRole('button', { name: labels.clearAll, exact: true });
      await clear.click();
      const confirmation = page.getByRole('dialog');
      await expect(confirmation).toContainText(labels.clearDescription);
      await page.screenshot({
        path: testInfo.outputPath('insights-confirmation.png'),
        animations: 'disabled',
      });
      await confirmation.getByRole('button', { name: common.cancel, exact: true }).click();
      expect(clearRequests).toBe(0);
      await clear.click();
      await confirmation.getByRole('button', { name: labels.clearConfirm, exact: true }).click();
      await expect(page.getByText(labels.noInsights, { exact: true })).toBeVisible();
      expect(clearRequests).toBe(1);
      await page.screenshot({
        path: testInfo.outputPath('insights-cleared.png'),
        animations: 'disabled',
      });
    });

    test(`institution settings link and None persist at ${width}px`, async ({ page }, testInfo) => {
      await page.setViewportSize({ width, height: 900 });
      const name = 'Card Bank & Trust';
      const institution = await api<{ id: number }>(page, '/institutions', { name, country: 'FR' });
      const account = await api<{ id: number }>(page, '/accounts', {
        name: 'Card checking',
        type: 'CHECKING',
        currency: 'EUR',
        initialBalance: 1000,
        institutionId: institution.id,
      });
      const openAccount = async (): Promise<void> => {
        await page.goto('/accounts');
        await page.getByText('Card checking', { exact: true }).click();
        await expect(
          page.getByRole('dialog', { name: 'Card checking', exact: true })
        ).toBeVisible();
      };
      await openAccount();
      await page.screenshot({
        path: testInfo.outputPath('account-institution.png'),
        animations: 'disabled',
      });
      const links = page.getByRole('link', { name, exact: true });
      await expect(links).toHaveCount(2);
      await links.first().click();
      await expect(page).toHaveURL(/\/institutions\?/);
      await expect(
        page.getByRole('textbox', { name: 'Search institution', exact: true })
      ).toHaveValue(name);
      await expect(page.getByRole('heading', { name, exact: true })).toBeVisible();

      await openAccount();
      if (width < 640) {
        const details = page.getByRole('dialog', { name: 'Card checking', exact: true });
        await details.getByRole('button', { name: commonEn.aria.closeModal, exact: true }).click();
        await expect(details).not.toBeVisible();
        await page.getByRole('button', { name: 'Edit account', exact: true }).click();
      } else {
        await page.getByRole('button', { name: 'Edit', exact: true }).click();
      }
      const dialog = page.getByRole('dialog');
      const selector = dialog.getByRole('combobox', { name: 'Institution (Optional)' });
      await selector.click();
      await page.getByRole('option', { name: 'None', exact: true }).click();
      await expect(selector).toContainText('None');
      const saved = page.waitForResponse(
        response =>
          response.url().endsWith(`/accounts/${account.id}`) &&
          response.request().method() === 'PUT'
      );
      await dialog.getByRole('button', { name: 'Update Account', exact: true }).click();
      const response = await saved;
      expect(response.status()).toBe(200);
      expect(response.request().postDataJSON().institutionId).toBeNull();
      const persisted = await api<{ institution: unknown; balance: number }>(
        page,
        `/accounts/${account.id}`
      );
      expect(persisted.institution).toBeNull();
      expect(persisted.balance).toBe(1000);
      await page.reload();
      await page.getByText('Card checking', { exact: true }).click();
      await expect(page.getByRole('link', { name, exact: true })).toHaveCount(0);
      await page.screenshot({
        path: testInfo.outputPath('account-no-institution.png'),
        animations: 'disabled',
      });
    });
  });
}
