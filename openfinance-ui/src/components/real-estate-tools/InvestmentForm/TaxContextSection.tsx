import { useTranslation } from 'react-i18next';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import {
  DEFAULT_RENTAL_TAX_CONTEXT,
  REGIME_LIMITS,
  type RentalTaxContext,
} from '@/types/realEstateTools';

interface TaxContextSectionProps {
  inputs?: RentalTaxContext;
  onUpdate: (field: keyof RentalTaxContext, value: number | null) => void;
}

export function TaxContextSection({ inputs, onUpdate }: TaxContextSectionProps) {
  const { t } = useTranslation('realEstate');
  const tax = { ...DEFAULT_RENTAL_TAX_CONTEXT, ...inputs };
  const amountFields = [
    'otherHouseholdIncome',
    'otherFurnishedReceipts',
    'otherUnfurnishedRent',
  ] as const;
  return (
    <Card className="mb-6">
      <CardHeader>
        <CardTitle>{t('taxContext.title')}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-sm text-muted-foreground">{t('taxContext.scope')}</p>
        <p className="text-sm">
          {t('taxContext.rates', {
            year: tax.incomeYear,
            threshold: REGIME_LIMITS.MICRO_BIC[tax.incomeYear],
          })}
        </p>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label htmlFor="rental-income-year">{t('taxContext.incomeYear')}</Label>
            <select
              id="rental-income-year"
              value={tax.incomeYear}
              className="block w-full rounded border p-2 bg-background"
              onChange={event => onUpdate('incomeYear', Number(event.target.value))}
            >
              <option value={2026}>2026</option>
              <option value={2025}>2025</option>
            </select>
          </div>
          {amountFields.map(field => (
            <div className="space-y-2" key={field}>
              <Label htmlFor={`rental-tax-${field}`}>{t(`taxContext.${field}`)}</Label>
              <Input
                id={`rental-tax-${field}`}
                type="number"
                min={0}
                step="0.01"
                value={tax[field] ?? ''}
                onChange={event =>
                  onUpdate(
                    field,
                    event.target.value === '' && field === 'otherHouseholdIncome'
                      ? null
                      : Number(event.target.value)
                  )
                }
              />
              {field === 'otherHouseholdIncome' && (
                <p className="text-xs text-muted-foreground">{t('taxContext.incomeHelp')}</p>
              )}
            </div>
          ))}
        </div>
        <p className="text-xs text-muted-foreground">{t('taxContext.assumptions')}</p>
        <a
          className="text-sm underline"
          href="https://www.impots.gouv.fr/particulier/location-meublee"
          target="_blank"
          rel="noreferrer"
        >
          {t('taxContext.sources')}
        </a>
      </CardContent>
    </Card>
  );
}
