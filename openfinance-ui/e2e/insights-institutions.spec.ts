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
      let insights = Array.from({ length: 4 }, (_, index) => ({
        id: index + 1,
        type: 'GENERAL_TIP',
        priority: 'LOW',
        title: `Card insight ${index + 1}`,
        description: 'A synthetic insight for the card action.',
        createdAt: new Date().toISOString(),
        dismissed: false,
      }));
      let clearRequests = 0;
      await page.route('**/api/v1/insights/**', async route => {
        if (route.request().url().endsWith('/dismiss-all')) {
          expect(route.request().method()).toBe('POST');
          clearRequests++;
          insights = [];
          await route.fulfill({ status: 204 });
        } else if (route.request().url().endsWith('/top/3')) {
          await route.fulfill({ json: insights.slice(0, 3) });
        } else {
          await route.continue();
        }
      });
      await page.reload();
      await page.getByText('Card insight 1', { exact: true }).scrollIntoViewIfNeeded();
      await page.screenshot({
        path: testInfo.outputPath('insights-card.png'),
        animations: 'disabled',
      });

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
