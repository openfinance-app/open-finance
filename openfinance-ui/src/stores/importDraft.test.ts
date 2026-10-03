import { beforeEach, describe, expect, it } from 'vitest';
import { readImportRecovery, useImportDraftStore } from '@/stores/importDraft';

describe('import recovery privacy and ownership', () => {
  beforeEach(() => useImportDraftStore.getState().clear());

  it('retains only the owner, session and step across a lost in-memory draft', () => {
    useImportDraftStore.getState().save(7, {
      sessionId: 42,
      selectedStep: 'confirm',
      uploadId: 'upload',
      fileName: 'private-bank.csv',
      accountOverride: 8,
      categoryMappings: { 'Private category': 9 },
      newCategoryNames: ['Private category'],
      skipDuplicates: false,
      editedTransactions: [],
    });
    useImportDraftStore.setState({ userId: null, draft: null });
    expect(readImportRecovery(7)).toEqual({ userId: 7, sessionId: 42, selectedStep: 'confirm' });
    expect(readImportRecovery(8)).toBeNull();
    expect(sessionStorage.getItem('import_recovery')).not.toMatch(
      /private|category|account|upload|transactions|skipDuplicates/i
    );
    useImportDraftStore.getState().clear();
    expect(readImportRecovery(7)).toBeNull();
  });

  it('ignores malformed recovery data', () => {
    for (const raw of [
      'not json',
      'null',
      '{"userId":7,"sessionId":-1,"selectedStep":"confirm"}',
      '{"userId":7,"sessionId":42,"selectedStep":"arbitrary"}',
    ]) {
      sessionStorage.setItem('import_recovery', raw);
      expect(readImportRecovery(7)).toBeNull();
    }
  });
});
