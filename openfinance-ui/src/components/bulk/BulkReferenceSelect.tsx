import type { ComponentProps } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { useAccounts } from '@/hooks/useAccounts';
import { useInstitutions } from '@/hooks/useInstitutions';
import { useCategories } from '@/hooks/useTransactions';

type SelectProps = ComponentProps<'select'>;
type ReferenceKind = 'account' | 'institution' | 'category';

function ReferenceSelect({
  query,
  options,
  ...props
}: SelectProps & {
  query: {
    isPending: boolean;
    isError: boolean;
    isFetching: boolean;
    refetch: () => Promise<unknown>;
  };
  options: { value: number; label: string }[];
}) {
  const { t } = useTranslation('common');
  const statusId = `${props.id}-lookup`;
  const empty = !query.isPending && !query.isError && options.length === 0;
  return (
    <div className="space-y-2">
      <select
        {...props}
        disabled={props.disabled || query.isPending || query.isError || empty}
        aria-invalid={query.isError || props['aria-invalid']}
        aria-describedby={
          [props['aria-describedby'], query.isError ? statusId : null].filter(Boolean).join(' ') ||
          undefined
        }
      >
        <option value="">
          {query.isPending ? t('loading') : empty ? t('bulk.noChoices') : t('bulk.chooseValue')}
        </option>
        {options.map(option => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      {query.isError && (
        <div className="flex flex-wrap items-center gap-2">
          <p id={statusId} role="alert" className="text-sm text-error">
            {t('bulk.choicesLoadFailed', { field: props['aria-label'] })}
          </p>
          <Button
            type="button"
            variant="outline"
            disabled={query.isFetching}
            onClick={() => void query.refetch()}
          >
            {query.isFetching ? t('loading') : t('bulk.retry')}
          </Button>
        </div>
      )}
    </div>
  );
}

function AccountSelect(props: SelectProps) {
  const query = useAccounts('all');
  return (
    <ReferenceSelect
      {...props}
      query={query}
      options={(query.data ?? []).map(item => ({
        value: item.id,
        label: `${item.name} · ${item.currency}`,
      }))}
    />
  );
}

function InstitutionSelect(props: SelectProps) {
  const query = useInstitutions();
  return (
    <ReferenceSelect
      {...props}
      query={query}
      options={(query.data ?? []).map(item => ({ value: item.id, label: item.name }))}
    />
  );
}

function CategorySelect(props: SelectProps) {
  const query = useCategories();
  const categories = new Map((query.data ?? []).map(item => [item.id, item]));
  const options = (query.data ?? []).map(item => {
    const names: string[] = [];
    const seen = new Set<number>();
    let current: typeof item | undefined = item;
    while (current && !seen.has(current.id)) {
      seen.add(current.id);
      names.unshift(current.name);
      current = current.parentId == null ? undefined : categories.get(current.parentId);
    }
    return { value: item.id, label: names.join(' / ') };
  });
  return <ReferenceSelect {...props} query={query} options={options} />;
}

export function BulkReferenceSelect({ kind, ...props }: SelectProps & { kind: ReferenceKind }) {
  switch (kind) {
    case 'account':
      return <AccountSelect {...props} />;
    case 'institution':
      return <InstitutionSelect {...props} />;
    case 'category':
      return <CategorySelect {...props} />;
  }
}
