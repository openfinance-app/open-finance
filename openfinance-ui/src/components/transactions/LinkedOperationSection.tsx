import type { ReactNode } from 'react';
import { ChevronDown } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import type { TransactionType } from '@/types/transaction';
import {
  isInvestmentOperation,
  linkedOperationLabel,
  type LinkedOperation,
} from '@/utils/linked-operation';

interface Props {
  type: TransactionType;
  operation: LinkedOperation;
  options: LinkedOperation[];
  onChange: (operation: LinkedOperation) => void;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  targetName?: string;
  hasError: boolean;
  splitMode: boolean;
  disabled?: boolean;
  children: ReactNode;
}

export function LinkedOperationSection({
  type,
  operation,
  options,
  onChange,
  open,
  onOpenChange,
  targetName,
  hasError,
  splitMode,
  disabled,
  children,
}: Props) {
  const { t } = useTranslation('transactions');
  return (
    <details
      className="group border-t border-border/60"
      open={open}
      onToggle={event => onOpenChange(event.currentTarget.open)}
    >
      <summary className="flex min-h-11 cursor-pointer list-none items-center justify-between gap-3 rounded-lg py-2 text-sm font-medium text-text-secondary hover:text-text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary [&::-webkit-details-marker]:hidden">
        <span className="flex min-w-0 flex-col gap-x-2 sm:flex-row sm:items-baseline">
          <span className="shrink-0">{t('linkedOperation.title')}</span>
          {operation !== 'NONE' && (
            <span className="min-w-0 truncate font-normal text-text-secondary">
              {t(linkedOperationLabel(operation, type))}
              {targetName ? ` · ${targetName}` : ''}
            </span>
          )}
          {hasError && (
            <span className="shrink-0 text-error">{t('linkedOperation.checkDetails')}</span>
          )}
        </span>
        <ChevronDown
          aria-hidden="true"
          className="h-4 w-4 shrink-0 transition-transform duration-150 group-open:rotate-180 motion-reduce:transition-none"
        />
      </summary>
      <div className="space-y-4 pt-3">
        <div className="space-y-1.5">
          <label
            htmlFor="linkedOperation"
            className="block text-sm font-medium text-text-secondary"
          >
            {t(type === 'TRANSFER' ? 'linkedOperation.fundingSource' : 'linkedOperation.operation')}
          </label>
          <select
            id="linkedOperation"
            value={operation}
            disabled={disabled}
            onChange={event => onChange(event.target.value as LinkedOperation)}
            aria-describedby={splitMode ? 'linked-operation-split-hint' : undefined}
            className="h-10 w-full rounded-lg border border-border bg-surface px-3 text-sm text-text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary disabled:opacity-50"
          >
            {operation === 'EXISTING' && (
              <option value="EXISTING">{t('linkedOperation.actions.EXISTING')}</option>
            )}
            {options.map(option => (
              <option
                key={option}
                value={option}
                disabled={splitMode && isInvestmentOperation(option)}
              >
                {t(linkedOperationLabel(option, type))}
              </option>
            ))}
          </select>
          {splitMode && (
            <p id="linked-operation-split-hint" className="text-sm text-text-secondary">
              {t('linkedOperation.splitHint')}
            </p>
          )}
        </div>
        {children}
      </div>
    </details>
  );
}
