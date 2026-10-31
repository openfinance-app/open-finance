import { useId } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/Dialog';
import { Button } from '@/components/ui/Button';
import { BulkReferenceSelect } from '@/components/bulk/BulkReferenceSelect';
import {
  BULK_FIELDS,
  type BulkField,
  type BulkItem,
  type BulkResource,
  type MetadataPatch,
} from '@/types/bulk';

const controlClass =
  'w-full min-h-11 rounded-md border border-border bg-surface px-3 py-2 text-sm text-text-primary focus-visible:outline-2 focus-visible:outline-primary disabled:opacity-50';
const accountTypes = ['CHECKING', 'SAVINGS', 'CREDIT_CARD', 'INVESTMENT', 'CASH', 'OTHER'];
const paymentMethods = [
  'CASH',
  'CHEQUE',
  'CREDIT_CARD',
  'DEBIT_CARD',
  'BANK_TRANSFER',
  'DEPOSIT',
  'STANDING_ORDER',
  'DIRECT_DEBIT',
  'ONLINE',
  'OTHER',
];

interface FormValues {
  changes: Record<string, { mode: 'unchanged' | 'set' | 'clear'; value: string }>;
}

export function BulkEditDialog({
  resource,
  items,
  busy,
  progress,
  onClose,
  onSubmit,
}: {
  resource: BulkResource;
  items: BulkItem[];
  busy: boolean;
  progress: number;
  onClose: () => void;
  onSubmit: (patch: MetadataPatch) => Promise<void>;
}) {
  const { t } = useTranslation('common');
  const { t: ta } = useTranslation('accounts');
  const { t: tt } = useTranslation('transactions');
  const formId = useId();
  const fields = (BULK_FIELDS[resource] ?? []).filter(
    field => !field.physicalOnly || items.every(item => item.physical)
  );
  const schema = z
    .object({
      changes: z.record(
        z.string(),
        z.object({
          mode: z.enum(['unchanged', 'set', 'clear']),
          value: z.string(),
        })
      ),
    })
    .superRefine((data, context) => {
      for (const field of fields) {
        const change = data.changes[field.name];
        if (change?.mode !== 'set') continue;
        if (!change.value.trim()) {
          context.addIssue({
            code: 'custom',
            path: ['changes', field.name, 'value'],
            message: t('bulk.valueRequired'),
          });
        } else if (field.maxLength && change.value.length > field.maxLength) {
          context.addIssue({
            code: 'custom',
            path: ['changes', field.name, 'value'],
            message: t('bulk.tooLong', { count: field.maxLength }),
          });
        }
      }
    });
  const {
    register,
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      changes: Object.fromEntries(
        fields.map(field => [field.name, { mode: 'unchanged', value: '' }])
      ),
    },
  });
  const changes = useWatch({ control, name: 'changes' });
  const changedCount = fields.filter(field => changes[field.name]?.mode !== 'unchanged').length;
  const submit = async (values: FormValues) => {
    const patch: MetadataPatch = {};
    for (const field of fields) {
      const change = values.changes[field.name];
      if (!change || change.mode === 'unchanged') continue;
      const textField = field.kind === 'text' || field.kind === 'textarea';
      patch[field.name] =
        change.mode === 'clear'
          ? textField
            ? ''
            : null
          : field.kind === 'boolean'
            ? change.value === 'true'
            : ['account', 'institution', 'category'].includes(field.kind)
              ? Number(change.value)
              : change.value;
    }
    if (Object.keys(patch).length > 0) await onSubmit(patch);
  };
  const options = (field: BulkField) => {
    switch (field.kind) {
      case 'accountType':
        return accountTypes.map(value => (
          <option key={value} value={value}>
            {ta(`form.types.${value}`)}
          </option>
        ));
      case 'paymentMethod':
        return paymentMethods.map(value => (
          <option key={value} value={value}>
            {tt(`form.paymentMethods.${value}`)}
          </option>
        ));
      case 'boolean':
        return (
          <>
            <option value="true">{t('yes')}</option>
            <option value="false">{t('no')}</option>
          </>
        );
      default:
        return null;
    }
  };
  return (
    <Dialog
      open
      onOpenChange={open => {
        if (!open && !busy) onClose();
      }}
    >
      <DialogContent className="sm:max-w-xl max-h-[90dvh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{t('bulk.editTitle', { count: items.length })}</DialogTitle>
          <DialogDescription>{t('bulk.editHint')}</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit(submit)} className="space-y-5">
          <fieldset disabled={busy} className="min-w-0 space-y-4">
            {fields.map(field => {
              const label = t(`bulk.fields.${field.name}`);
              const id = `${formId}-${field.name}`;
              const error = errors.changes?.[field.name]?.value?.message;
              return (
                <div key={field.name} className="space-y-2">
                  <div className="grid grid-cols-1 items-center gap-2 sm:grid-cols-[1fr_12rem]">
                    <label htmlFor={`${id}-mode`} className="text-sm font-medium text-text-primary">
                      {label}
                    </label>
                    <select
                      id={`${id}-mode`}
                      className={controlClass}
                      aria-label={t('bulk.fieldAction', { field: label })}
                      {...register(`changes.${field.name}.mode`)}
                    >
                      <option value="unchanged">{t('bulk.unchanged')}</option>
                      <option value="set">{t('bulk.set')}</option>
                      {field.clearable && <option value="clear">{t('bulk.clearValue')}</option>}
                    </select>
                  </div>
                  {changes[field.name]?.mode === 'set' && (
                    <>
                      {field.kind === 'textarea' ? (
                        <textarea
                          id={id}
                          aria-label={label}
                          aria-invalid={!!error}
                          aria-describedby={error ? `${id}-error` : undefined}
                          rows={3}
                          className={controlClass}
                          maxLength={field.maxLength}
                          {...register(`changes.${field.name}.value`)}
                        />
                      ) : field.kind === 'text' ? (
                        <input
                          id={id}
                          aria-label={label}
                          aria-invalid={!!error}
                          aria-describedby={error ? `${id}-error` : undefined}
                          className={controlClass}
                          maxLength={field.maxLength}
                          {...register(`changes.${field.name}.value`)}
                        />
                      ) : field.kind === 'account' ||
                        field.kind === 'institution' ||
                        field.kind === 'category' ? (
                        <BulkReferenceSelect
                          kind={field.kind}
                          id={id}
                          aria-label={label}
                          aria-invalid={!!error}
                          aria-describedby={error ? `${id}-error` : undefined}
                          className={controlClass}
                          {...register(`changes.${field.name}.value`)}
                        />
                      ) : (
                        <select
                          id={id}
                          aria-label={label}
                          aria-invalid={!!error}
                          aria-describedby={error ? `${id}-error` : undefined}
                          className={controlClass}
                          {...register(`changes.${field.name}.value`)}
                        >
                          <option value="">{t('bulk.chooseValue')}</option>
                          {options(field)}
                        </select>
                      )}
                      {field.name === 'tags' && (
                        <p className="text-xs text-text-secondary">{t('bulk.tagsHint')}</p>
                      )}
                      {error && (
                        <p id={`${id}-error`} role="alert" className="text-sm text-error">
                          {error}
                        </p>
                      )}
                    </>
                  )}
                </div>
              );
            })}
          </fieldset>
          {resource === 'transactions' && (
            <p className="text-sm text-text-secondary">{t('bulk.transactionHint')}</p>
          )}
          {resource === 'payees' && (
            <p className="text-sm text-text-secondary">{t('bulk.payeeHint')}</p>
          )}
          {busy && (
            <p role="status" className="text-sm text-text-secondary">
              {t('bulk.progress', { completed: progress, count: items.length })}
            </p>
          )}
          <DialogFooter>
            <Button type="button" variant="ghost" onClick={onClose} disabled={busy}>
              {t('cancel')}
            </Button>
            <Button type="submit" disabled={busy || changedCount === 0 || items.length === 0}>
              {t('bulk.apply', { count: items.length })}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
