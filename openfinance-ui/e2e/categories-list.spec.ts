import { expect, test, type Page } from '@playwright/test';
import { loginAs, registerWithRetry } from './helpers/auth';
import en from '../public/locales/en/categories.json' with { type: 'json' };
import fr from '../public/locales/fr/categories.json' with { type: 'json' };

async function create<T>(page: Page, path: string, body: unknown): Promise<T> {
  return page.evaluate(
    async ({ path, body }) => {
      const response = await fetch(`/api/v1${path}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${localStorage.getItem('auth_token') || sessionStorage.getItem('auth_token')}`,
          'X-Encryption-Session': sessionStorage.getItem('encryption_session') || '',
        },
        body: JSON.stringify(body),
      });
      if (!response.ok) throw new Error(`${path}: HTTP ${response.status}`);
      return response.json();
    },
    { path, body }
  );
}

for (const language of ['en', 'fr'] as const) {
  test(`category totals, direct activity and list controls (${language})`, async ({
    page,
    request,
  }) => {
    test.setTimeout(180_000);
    const labels = language === 'fr' ? fr : en;
    const username = `categories_${language}_${Date.now()}`;
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
    await create(page, '/users/me/onboarding', {
      country: 'FR',
      baseCurrency: 'EUR',
      secondaryCurrency: '',
      language,
      dateFormat: 'DD/MM/YYYY',
      numberFormat: '1,234.56',
      amountDisplayMode: 'base',
    });
    const account = await create<{ id: number }>(page, '/accounts', {
      name: 'Category layout fixture',
      type: 'CHECKING',
      currency: 'EUR',
      initialBalance: 0,
      openingDate: '2026-01-01',
    });
    const category = async (name: string, amount: number, color: string, parentId?: number) => {
      const result = await create<{ id: number }>(page, '/categories', { name, parentId, color });
      if (amount !== 0)
        await create(page, '/transactions', {
          accountId: account.id,
          categoryId: result.id,
          amount: Math.abs(amount),
          type: amount > 0 ? 'INCOME' : 'EXPENSE',
          currency: 'EUR',
          date: '2026-10-01',
          payee: 'Layout fixture',
        });
      return result;
    };
    const home = await category('Maison', -100, '#5e84b0');
    await category('Énergie', -400, '#5e84b0', home.id);
    await category('Loyer', -600, '#5e84b0', home.id);
    const food = await category('Alimentation', 0, '#c07a45');
    await category('Courses', -217.8, '#c07a45', food.id);
    await category('Restaurants', -64.5, '#c07a45', food.id);
    const health = await category('Santé', -80, '#45a191');
    const care = await category('Soins', 0, '#45a191', health.id);
    await category('Consultations et soins spécialisés', -125, '#45a191', care.id);
    await category('Pharmacie', -33, '#45a191', health.id);
    const income = await category('Revenus', 0, '#8a80cc');
    await category('Salaire', 3964.79, '#8a80cc', income.id);
    await category('Remboursements', 45, '#8a80cc', income.id);
    await category('Déplacements', -58.7, '#7d8fa3');
    await category('Voyages et séjours de longue durée', -1234567.89, '#c96f7d');
    await category('Épargne', 0, '#8f7a5e');

    const actions = (name: string) => labels.aria.categoryActions.replace('{{name}}', name);
    const collapse = (name: string) => `${labels.badges.collapse} ${name}`;
    const directLink = () =>
      page.getByRole('link', {
        name: labels.aria.viewDirectTransactions.replace('{{name}}', 'Maison'),
      });
    const totalLink = () =>
      page.getByRole('link', {
        name: labels.aria.viewSubtreeTransactions.replace('{{name}}', 'Maison'),
      });

    for (const width of [1280, 1024, 768, 390, 320]) {
      await page.setViewportSize({ width, height: 1000 });
      await page.goto('/categories');
      const list = page.getByRole('region', { name: labels.list.label });
      await expect(page.getByRole('button', { name: actions('Loyer') })).toHaveCount(0);
      await expect(page.getByRole('button', { name: actions('Soins') })).toHaveCount(0);
      await expect(directLink()).toContainText('1');
      await expect(totalLink()).toContainText('3');
      await expect(directLink()).toHaveAttribute(
        'href',
        `/transactions?categoryId=${home.id}&excludeTransfers=true`
      );
      await expect(totalLink()).toHaveAttribute(
        'href',
        `/transactions?categoryId=${home.id}&includeSubcategories=true&excludeTransfers=true`
      );
      const parent = totalLink().locator('xpath=ancestor::li[1]');
      await expect(parent.getByTitle(labels.includingSubcategories)).toHaveText(/1,100\.00/);
      for (const amount of [
        parent.getByTitle(labels.includingSubcategories),
        parent.getByTitle(labels.directAmount).filter({ hasText: /100\.00/ }),
      ]) {
        expect(await amount.evaluate(element => getComputedStyle(element).fontFamily)).toContain(
          'JetBrains Mono'
        );
      }
      for (const name of [labels.list.group, labels.sort.label]) {
        const fit = await page
          .getByRole('combobox', { name })
          .evaluate((element: HTMLSelectElement) => {
            const style = getComputedStyle(element);
            const canvas = document.createElement('canvas');
            const context = canvas.getContext('2d')!;
            context.font = style.font;
            const textWidth = context.measureText(
              element.selectedOptions[0].textContent || ''
            ).width;
            return {
              required:
                textWidth + parseFloat(style.paddingLeft) + parseFloat(style.paddingRight) + 24,
              available: element.clientWidth,
            };
          });
        expect(fit.required, `${name} at ${width}px`).toBeLessThanOrEqual(fit.available);
      }
      await expect(
        parent.getByTitle(labels.directAmount).filter({ hasText: /100\.00/ })
      ).toHaveCount(1);
      const expandAll = page.getByRole('button', { name: labels.list.expandAll, exact: true });
      if (width === 1280) {
        await expandAll.focus();
        await expandAll.press('Enter');
      } else {
        await expandAll.click();
      }
      await expect(
        page.getByRole('button', { name: actions('Consultations et soins spécialisés') })
      ).toBeVisible();
      await page.getByRole('button', { name: labels.list.collapseAll, exact: true }).click();
      await expect(page.getByRole('button', { name: actions('Soins') })).toHaveCount(0);
      await expect(page.getByRole('button', { name: actions('Loyer') })).toHaveCount(0);
      await expect(directLink()).toBeVisible();
      await page.getByRole('button', { name: labels.list.expandAll, exact: true }).click();
      await expect(
        page.getByRole('button', { name: actions('Consultations et soins spécialisés') })
      ).toBeVisible();
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)
      ).toBe(true);
      expect(await list.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
      if (width === 1280 || width === 390 || width === 320) {
        await page.getByRole('main').evaluate(element => {
          element.scrollTop = 0;
        });
        await page.screenshot({
          path: `../target/browser-tests/categories-list-${language}-${width}.png`,
          fullPage: true,
          animations: 'disabled',
        });
        if (language === 'fr') {
          if (width === 390) {
            await page
              .getByRole('combobox', { name: labels.list.group })
              .selectOption(String(home.id));
            await expect(page.getByRole('button', { name: actions('Loyer') })).toHaveCount(0);
            await page.getByRole('button', { name: labels.list.expandAll, exact: true }).click();
            await page.getByRole('main').evaluate(element => {
              element.scrollTop = 0;
            });
            await page.screenshot({
              path: '../target/browser-tests/categories-list-fr-mobile-group.png',
              fullPage: true,
              animations: 'disabled',
            });
            await page.getByRole('combobox', { name: labels.list.group }).selectOption('all');
            await page.getByRole('button', { name: labels.list.expandAll, exact: true }).click();
          } else if (width === 1280) {
            await page.evaluate(() => {
              document.documentElement.classList.remove('dark');
              document.documentElement.classList.add('light');
            });
            await page.screenshot({
              path: '../target/browser-tests/categories-list-fr-light.png',
              fullPage: true,
              animations: 'disabled',
            });
            await page.evaluate(() => {
              document.documentElement.classList.remove('light');
              document.documentElement.classList.add('dark');
            });
          }
        }
      }

      await page.getByRole('button', { name: collapse('Maison'), exact: true }).click();
      await expect(page.getByRole('button', { name: actions('Loyer') })).toHaveCount(0);
      await expect(directLink()).toBeVisible();
      const actionMenu = page.getByRole('button', { name: actions('Maison') });
      if (width === 1280) {
        await actionMenu.focus();
        await actionMenu.press('Enter');
        await page.getByRole('menuitem', { name: labels.aria.editCategory }).press('Enter');
      } else {
        await actionMenu.click();
        await page.getByRole('menuitem', { name: labels.aria.editCategory }).click();
      }
      const dialog = page.getByRole('dialog', { name: labels.form.editTitle });
      await expect(dialog).toBeVisible();
      await dialog.getByRole('button', { name: labels.form.cancel, exact: true }).click();

      await page.getByRole('textbox', { name: labels.search.placeholder }).fill('Maison');
      await expect(page.getByRole('button', { name: actions('Loyer') })).toBeVisible();
      await expect(page.getByRole('button', { name: actions('Énergie') })).toHaveCount(1);
      await page.getByRole('button', { name: labels.list.collapseAll, exact: true }).click();
      await expect(page.getByRole('button', { name: actions('Loyer') })).toHaveCount(0);
      await expect(directLink()).toBeVisible();
      await page.getByRole('button', { name: labels.list.expandAll, exact: true }).click();
      await expect(page.getByRole('button', { name: actions('Loyer') })).toBeVisible();
      if (language === 'fr' && (width === 1280 || width === 390)) {
        await page.getByRole('main').evaluate(element => {
          element.scrollTop = 0;
        });
        await page.screenshot({
          path: `../target/browser-tests/categories-search-fr-${width}.png`,
          fullPage: true,
          animations: 'disabled',
        });
      }
    }
    await page.setViewportSize({ width: 1280, height: 1000 });
    await page.getByRole('combobox', { name: labels.list.group }).selectOption(String(home.id));
    await expect(page.getByRole('button', { name: actions('Salaire') })).toHaveCount(0);
    await expect(directLink()).toBeVisible();
    const search = page.getByRole('textbox', { name: labels.search.placeholder });
    await search.fill('Maison');
    await expect(totalLink()).toBeVisible();
    await expect(directLink()).toBeVisible();
    await expect(page.getByRole('button', { name: actions('Loyer') })).toBeVisible();
    await search.fill('Salaire');
    await expect(page.getByText(labels.empty.noMatch, { exact: true })).toBeVisible();
    await search.fill('');
    await page.getByRole('combobox', { name: labels.list.group }).selectOption('all');
    await search.fill('Santé');
    await expect(
      page.getByRole('button', { name: actions('Consultations et soins spécialisés') })
    ).toBeVisible();
    await search.fill('');
    await page.getByRole('combobox', { name: labels.sort.label }).selectOption('amount');
    const actionButtons = page.getByRole('button', {
      name: language === 'fr' ? /^Actions pour / : /^Actions for /,
    });
    await expect(actionButtons.first()).toHaveAccessibleName(actions('Revenus'));
  });
}
