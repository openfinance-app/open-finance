import { expect, test, type Page } from '@playwright/test';
import { loginAs, registerWithRetry } from './helpers/auth';
import type { TransactionRule } from '../src/types/transactionRules';
import importEn from '../public/locales/en/import.json' with { type: 'json' };
import importFr from '../public/locales/fr/import.json' with { type: 'json' };
import rulesEn from '../public/locales/en/rules.json' with { type: 'json' };
import rulesFr from '../public/locales/fr/rules.json' with { type: 'json' };
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
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      return response.json();
    },
    { path, method, body }
  );
}

for (const language of ['en', 'fr'] as const) {
  test(`Skrooge rule completion counts and editing nested actions (${language})`, async ({
    page,
    request,
  }) => {
    test.setTimeout(180_000);
    await page.setViewportSize({ width: 1280, height: 1100 });
    const labels = language === 'fr' ? importFr : importEn;
    const ruleLabels = language === 'fr' ? rulesFr : rulesEn;
    const common = language === 'fr' ? commonFr : commonEn;
    const username = `rules_${Date.now()}`;
    const credentials = { username, password: 'Password123!', masterPassword: 'RealMaster123!' };
    const registered = await registerWithRetry(request, {
      ...credentials,
      email: `${username}@example.invalid`,
      skipSeeding: true,
    });
    expect(registered.status()).toBe(201);
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
    const options = await api<{ skroogeJsonEnabled: boolean }>(page, '/config/import');
    test.skip(!options.skroogeJsonEnabled, 'Skrooge JSON import must be enabled for this flow');

    const expression = (field: string, value: string): string =>
      `<element><!--OR--><element><!--AND--><element attribute="${field}" operator="#ATT#='#V1S#'" value="${value}"/></element></element>`;
    const source = {
      bank: [{ id: 1, t_name: 'Import bank' }],
      account: [
        {
          id: 10,
          rd_bank_id: 1,
          t_name: 'Imported checking',
          t_type: 'C',
          t_number: 'TEST',
          t_close: false,
          t_comment: 'Account notes',
          t_agency_address: '12 Main Street',
        },
      ],
      category: [
        { id: 100, rd_category_id: 0, t_name: 'Maison', t_fullname: 'Maison' },
        { id: 101, rd_category_id: 100, t_name: 'Ménage', t_fullname: 'Maison > Ménage' },
      ],
      payee: [{ id: 1, t_name: 'Coffee shop' }],
      unit: [{ id: 1, t_name: 'Euro (EUR)', t_symbol: '€', t_type: '1', rd_unit_id: 0 }],
      unitvalue: [{ id: 1, rd_unit_id: 1, d_date: '2020-01-01', f_quantity: 1 }],
      operation: [
        {
          id: 1,
          rd_account_id: 10,
          d_date: '2026-01-01',
          i_group_id: 0,
          r_payee_id: 1,
          t_status: 'Y',
          rc_unit_id: 1,
        },
      ],
      suboperation: [{ id: 11, rd_operation_id: 1, r_category_id: 101, f_value: '-10.00' }],
      rule: [
        {
          id: 1,
          f_sortorder: 1,
          t_action_type: 'U',
          t_action_description: 'Catégorie = « Maison > Ménage »',
          t_definition: expression('t_PAYEE', 'Coffee shop'),
          t_action_definition: expression('t_REALCATEGORY', 'Maison > Ménage'),
        },
        {
          id: 2,
          f_sortorder: 2,
          t_action_type: 'U',
          t_action_description: 'Payment mode',
          t_definition: expression('t_PAYEE', 'Coffee shop'),
          t_action_definition: expression('t_mode', 'CARD'),
        },
      ],
    };
    await page.goto('/import');
    await page.locator('input[type="file"]').setInputFiles({
      name: 'rules.json',
      mimeType: 'application/json',
      buffer: Buffer.from(JSON.stringify(source)),
    });
    await page.getByRole('button', { name: common.buttons.upload, exact: true }).click();
    const notice = page.getByRole('region', { name: labels.skroogeRules.title });
    await expect(notice).toContainText(
      labels.skroogeRules.preview
        .replace('{{source}}', '2')
        .replace('{{ready}}', '1')
        .replace('{{skipped}}', '1')
    );
    await notice.getByText(labels.skroogeRules.details, { exact: true }).click();
    await expect(notice).toContainText(labels.skroogeRules.warnings.unsupportedAction);
    expect(await api<unknown[]>(page, '/transaction-rules')).toHaveLength(0);
    const next = common.buttons.next;
    await page.getByRole('button', { name: next, exact: true }).click();
    await page.getByRole('button', { name: next, exact: true }).click();
    await page.getByRole('button', { name: labels.summary.confirm, exact: true }).click();
    await expect(page.getByRole('status').filter({ hasText: labels.progress.success })).toBeVisible(
      { timeout: 90_000 }
    );
    await expect(notice).toHaveCount(1);
    // Counts must share the completion card with the success message and transaction totals.
    const completionCard = page
      .getByRole('status')
      .filter({ hasText: labels.progress.success })
      .locator('../..');
    await expect(
      completionCard.getByRole('region', { name: labels.skroogeRules.title })
    ).toBeVisible();
    for (const [label, count] of [
      [labels.skroogeRules.stats.source, '2'],
      [labels.skroogeRules.stats.imported, '1'],
      [labels.skroogeRules.stats.existing, '0'],
      [labels.skroogeRules.stats.skipped, '1'],
    ]) {
      await expect(
        notice
          .locator('div')
          .filter({ has: page.getByText(label, { exact: true }) })
          .locator('dd')
      ).toHaveText(count);
    }
    const imported = await api<TransactionRule[]>(page, '/transaction-rules');
    expect(imported).toHaveLength(1);
    expect(imported[0].actions[0].actionValue).toBe('Maison:Ménage');
    const accounts = await api<{ name: string; description: string }[]>(page, '/accounts');
    expect(accounts.find(account => account.name === 'Imported checking')?.description).toBe(
      'Account notes\n12 Main Street'
    );
    await completionCard.screenshot({
      path: `../target/browser-tests/skrooge-rules-import-${language}.png`,
    });
    if (language === 'fr') {
      await page.setViewportSize({ width: 390, height: 844 });
      await expect(notice).toBeVisible();
      expect(await notice.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(
        true
      );
      await completionCard.screenshot({
        path: '../target/browser-tests/skrooge-rules-import-fr-mobile.png',
      });
      await page.setViewportSize({ width: 1280, height: 1100 });
    }
    for (const actionValue of ['Maison:Ménage', 'Maison > Ménage']) {
      if (actionValue.includes(' > ')) {
        // Simulate a rule already saved by the earlier importer; no reimport should be necessary.
        await api(page, `/transaction-rules/${imported[0].id}`, 'PUT', {
          ...imported[0],
          actions: [{ ...imported[0].actions[0], actionValue }],
        });
      }
      await page.goto('/transaction-rules');
      await page.getByRole('button', { name: ruleLabels.list.editRule, exact: true }).click();
      const dialog = page.getByRole('dialog', { name: ruleLabels.form.editTitle });
      await expect(dialog.getByRole('combobox').filter({ hasText: 'Ménage' })).toBeVisible();
      await dialog.screenshot({
        path: `../target/browser-tests/skrooge-rule-editor-${language}-${actionValue.includes(' > ') ? 'legacy' : 'new'}.png`,
      });
      await dialog.getByRole('button', { name: ruleLabels.form.update, exact: true }).click();
      await expect(dialog).not.toBeVisible();
      const saved = await api<TransactionRule>(page, `/transaction-rules/${imported[0].id}`);
      expect(saved.actions[0]).toMatchObject({ actionType: 'SET_CATEGORY', actionValue });
    }
  });
}
