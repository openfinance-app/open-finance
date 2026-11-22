import { useTranslation } from 'react-i18next';
import { FilterChip } from '@/components/ui/FilterChip';
import type { AccountFilters } from '@/types/account';

export function AccountScope({
  filters,
  onChange,
}: {
  filters: AccountFilters;
  onChange: (filters: AccountFilters) => void;
}) {
  const { t } = useTranslation(['accounts', 'dashboard']);
  return (
    <>
      {filters.lowBalance && (
        <FilterChip
          label={t('filters.lowBalance')}
          onRemove={() => onChange({ ...filters, lowBalance: undefined })}
        />
      )}
      {filters.capital && (
        <FilterChip
          label={t(`dashboard:accountLiquidity.${filters.capital}`)}
          onRemove={() => onChange({ ...filters, capital: undefined })}
        />
      )}
    </>
  );
}
