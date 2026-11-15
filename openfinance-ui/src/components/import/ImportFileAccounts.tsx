import { useTranslation } from 'react-i18next';
import { SimpleSelect } from '@/components/ui/SimpleSelect';
import type { ImportSourceFile } from '@/types/import';

interface ImportFileAccountsProps {
  files: ImportSourceFile[];
  accounts: { id: number; name: string; isActive?: boolean }[];
  overrides: Record<string, number | null>;
  onChange?: (uploadId: string, accountId: number | null) => void;
  disabled?: boolean;
}

export function ImportFileAccounts({
  files,
  accounts,
  overrides,
  onChange,
  disabled,
}: ImportFileAccountsProps) {
  const { t } = useTranslation('import');
  return (
    <ul className="divide-y divide-border">
      {files.map(file => {
        const accountId = file.uploadId in overrides ? overrides[file.uploadId] : file.accountId;
        const fallback = t('wizard.accountSelection.autoCreateLabel', {
          name: file.suggestedAccountName || file.fileName.replace(/\.[^.]+$/, ''),
        });
        return (
          <li key={file.uploadId} className="grid gap-3 py-4 sm:grid-cols-2 sm:items-center">
            <div className="min-w-0">
              <p className="break-all font-medium text-text-primary">{file.fileName}</p>
              <p className="mt-1 text-sm text-text-secondary">
                {t('wizard.files.transactions', { count: file.totalTransactions })}
                {file.suggestedAccountName && ` · ${file.suggestedAccountName}`}
              </p>
            </div>
            {onChange ? (
              <div className="min-w-0">
                <label
                  className="mb-1.5 block text-sm text-text-secondary"
                  htmlFor={`account-${file.uploadId}`}
                >
                  {t('wizard.files.accountFor', { name: file.fileName })}
                </label>
                <SimpleSelect
                  id={`account-${file.uploadId}`}
                  value={accountId?.toString() ?? ''}
                  disabled={disabled}
                  onChange={event =>
                    onChange(file.uploadId, event.target.value ? Number(event.target.value) : null)
                  }
                >
                  <option value="">{fallback}</option>
                  {accounts
                    .filter(account => account.isActive !== false)
                    .map(account => (
                      <option key={account.id} value={account.id}>
                        {account.name}
                      </option>
                    ))}
                </SimpleSelect>
              </div>
            ) : (
              <p className="break-words text-sm text-text-secondary">
                {accounts.find(account => account.id === accountId)?.name ?? fallback}
              </p>
            )}
          </li>
        );
      })}
    </ul>
  );
}
