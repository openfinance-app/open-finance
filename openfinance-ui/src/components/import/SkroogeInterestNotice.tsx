import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';

interface InterestImportMetadata {
  sourceInterestRateCount: number;
  interestRates: unknown[];
  skippedInterestRateCount: number;
  importedInterestRateCount: number;
  existingInterestRateCount: number;
  interestWarnings: { sourceId: number | null; code: string }[];
}

function readInterest(metadata?: string): InterestImportMetadata | null {
  if (!metadata) return null;
  try {
    const value = JSON.parse(metadata)?.skroogeMetadata as InterestImportMetadata | undefined;
    return value && value.sourceInterestRateCount > 0 && Array.isArray(value.interestRates)
      ? value
      : null;
  } catch {
    return null;
  }
}

export function SkroogeInterestNotice({
  metadata,
  completed,
}: {
  metadata?: string;
  completed: boolean;
}) {
  const { t } = useTranslation('import');
  const interestRates = useMemo(() => readInterest(metadata), [metadata]);
  if (!interestRates) return null;
  return (
    <section
      aria-label={t('skroogeInterest.title')}
      className="mb-4 rounded-lg border border-border bg-surface p-4"
    >
      <h3 className="font-semibold">{t('skroogeInterest.title')}</h3>
      {completed ? (
        <dl className="my-4 grid grid-cols-2 gap-4 md:grid-cols-4">
          {[
            {
              label: 'source',
              count: interestRates.sourceInterestRateCount,
              color: 'text-text-primary',
            },
            {
              label: 'imported',
              count: interestRates.importedInterestRateCount,
              color: 'text-green-500',
            },
            {
              label: 'existing',
              count: interestRates.existingInterestRateCount,
              color: 'text-text-secondary',
            },
            {
              label: 'skipped',
              count: interestRates.skippedInterestRateCount,
              color: 'text-amber-500',
            },
          ].map(({ label, count, color }) => (
            <div key={label} className="flex flex-col gap-1 text-center">
              <dt className="order-last text-xs text-text-secondary">
                {t(`skroogeInterest.stats.${label}`)}
              </dt>
              <dd className={`text-2xl font-bold ${color}`}>{count}</dd>
            </div>
          ))}
        </dl>
      ) : (
        <p className="mt-1 text-sm">
          {t('skroogeInterest.preview', {
            source: interestRates.sourceInterestRateCount,
            ready: interestRates.interestRates.length,
            skipped: interestRates.skippedInterestRateCount,
          })}
        </p>
      )}
      <p className="mt-1 text-xs text-text-secondary">{t('skroogeInterest.scope')}</p>
      {interestRates.interestWarnings?.length > 0 && (
        <details className="mt-2 text-sm">
          <summary className="cursor-pointer text-primary">{t('skroogeInterest.details')}</summary>
          <ul className="mt-2 space-y-1 text-text-secondary">
            {interestRates.interestWarnings.map((warning, index) => (
              <li key={index}>
                {warning.sourceId != null &&
                  `${t('skroogeInterest.rate', { id: warning.sourceId })}: `}
                {t(`skroogeInterest.warnings.${warning.code}`)}
              </li>
            ))}
          </ul>
        </details>
      )}
    </section>
  );
}
