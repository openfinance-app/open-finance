import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';

interface RuleImportMetadata {
  sourceRuleCount: number;
  rules: unknown[];
  skippedRuleCount: number;
  importedRuleCount: number;
  existingRuleCount: number;
  ruleWarnings: { sourceId: number | null; code: string }[];
}

function readRules(metadata?: string): RuleImportMetadata | null {
  if (!metadata) return null;
  try {
    const value = JSON.parse(metadata)?.skroogeMetadata as RuleImportMetadata | undefined;
    return value && value.sourceRuleCount > 0 && Array.isArray(value.rules) ? value : null;
  } catch {
    return null;
  }
}

export function SkroogeRulesNotice({
  metadata,
  completed,
}: {
  metadata?: string;
  completed: boolean;
}) {
  const { t } = useTranslation('import');
  const rules = useMemo(() => readRules(metadata), [metadata]);
  if (!rules) return null;
  return (
    <section
      aria-label={t('skroogeRules.title')}
      className="mb-4 rounded-lg border border-border bg-surface p-4"
    >
      <h3 className="font-semibold">{t('skroogeRules.title')}</h3>
      {completed ? (
        <dl className="my-4 grid grid-cols-2 gap-4 md:grid-cols-4">
          {[
            { label: 'source', count: rules.sourceRuleCount, color: 'text-text-primary' },
            { label: 'imported', count: rules.importedRuleCount, color: 'text-green-500' },
            { label: 'existing', count: rules.existingRuleCount, color: 'text-text-secondary' },
            { label: 'skipped', count: rules.skippedRuleCount, color: 'text-amber-500' },
          ].map(({ label, count, color }) => (
            <div key={label} className="flex flex-col gap-1 text-center">
              <dt className="order-last text-xs text-text-secondary">
                {t(`skroogeRules.stats.${label}`)}
              </dt>
              <dd className={`text-2xl font-bold ${color}`}>{count}</dd>
            </div>
          ))}
        </dl>
      ) : (
        <p className="mt-1 text-sm">
          {t('skroogeRules.preview', {
            source: rules.sourceRuleCount,
            ready: rules.rules.length,
            skipped: rules.skippedRuleCount,
          })}
        </p>
      )}
      <p className="mt-1 text-xs text-text-secondary">{t('skroogeRules.scope')}</p>
      {rules.ruleWarnings?.length > 0 && (
        <details className="mt-2 text-sm">
          <summary className="cursor-pointer text-primary">{t('skroogeRules.details')}</summary>
          <ul className="mt-2 space-y-1 text-text-secondary">
            {rules.ruleWarnings.map((warning, index) => (
              <li key={index}>
                {warning.sourceId != null &&
                  `${t('skroogeRules.rule', { id: warning.sourceId })}: `}
                {t(`skroogeRules.warnings.${warning.code}`)}
              </li>
            ))}
          </ul>
        </details>
      )}
    </section>
  );
}
