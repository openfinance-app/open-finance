/**
 * RuleConditionBuilder Component
 *
 * Renders a list of condition rows for a transaction rule. Each row allows
 * selecting a field, an operator (filtered to compatible operators for the
 * chosen field), and a value. Rows can be added or removed dynamically.
 *
 * Requirement: REQ-TR-6.4
 */
import { Plus, Trash2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import type {
  RuleCondition,
  RuleConditionField,
  RuleConditionOperator,
} from '@/types/transactionRules';

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/** Operators available for string-based fields (DESCRIPTION) */
const STRING_OPERATORS: RuleConditionOperator[] = [
  'CONTAINS',
  'NOT_CONTAINS',
  'EQUALS',
  'NOT_EQUALS',
];

/** Operators available for numeric fields (AMOUNT) */
const NUMERIC_OPERATORS: RuleConditionOperator[] = [
  'EQUALS',
  'NOT_EQUALS',
  'GREATER_THAN',
  'LESS_THAN',
  'GREATER_OR_EQUAL',
  'LESS_OR_EQUAL',
];

/** Operators available for TRANSACTION_TYPE field */
const TYPE_OPERATORS: RuleConditionOperator[] = ['EQUALS', 'NOT_EQUALS'];



function operatorsForField(field: RuleConditionField): RuleConditionOperator[] {
  switch (field) {
    case 'AMOUNT':
      return NUMERIC_OPERATORS;
    case 'TRANSACTION_TYPE':
      return TYPE_OPERATORS;
    case 'DESCRIPTION':
    default:
      return STRING_OPERATORS;
  }
}

function defaultOperatorForField(field: RuleConditionField): RuleConditionOperator {
  return operatorsForField(field)[0];
}

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

/** A condition row without the optional id — used during rule creation/editing */
export type ConditionDraft = Omit<RuleCondition, 'id'>;

interface RuleConditionBuilderProps {
  /** Current list of condition drafts */
  conditions: ConditionDraft[];
  /** Called whenever the conditions list changes */
  onChange: (conditions: ConditionDraft[]) => void;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

/**
 * Renders a dynamic list of condition rows for building rule conditions.
 * Requirement: REQ-TR-6.4
 */
export function RuleConditionBuilder({ conditions, onChange }: RuleConditionBuilderProps) {
  const { t } = useTranslation('rules');

  const FIELD_LABELS: Record<RuleConditionField, string> = {
    DESCRIPTION: t('form.conditions.fields.DESCRIPTION'),
    AMOUNT: t('form.conditions.fields.AMOUNT'),
    TRANSACTION_TYPE: t('form.conditions.fields.TRANSACTION_TYPE'),
  };

  const OPERATOR_LABELS: Record<RuleConditionOperator, string> = {
    CONTAINS: t('form.conditions.operators.CONTAINS'),
    NOT_CONTAINS: t('form.conditions.operators.NOT_CONTAINS'),
    EQUALS: t('form.conditions.operators.EQUALS'),
    NOT_EQUALS: t('form.conditions.operators.NOT_EQUALS'),
    GREATER_THAN: t('form.conditions.operators.GREATER_THAN'),
    LESS_THAN: t('form.conditions.operators.LESS_THAN'),
    GREATER_OR_EQUAL: t('form.conditions.operators.GREATER_OR_EQUAL'),
    LESS_OR_EQUAL: t('form.conditions.operators.LESS_OR_EQUAL'),
  };

  const TRANSACTION_TYPE_VALUES = [
    { value: 'CREDIT', label: t('form.conditions.typeValues.CREDIT') },
    { value: 'DEBIT', label: t('form.conditions.typeValues.DEBIT') },
  ];

  const handleAdd = () => {
    const newCondition: ConditionDraft = {
      field: 'DESCRIPTION',
      operator: 'CONTAINS',
      value: '',
      sortOrder: conditions.length,
    };
    onChange([...conditions, newCondition]);
  };

  const handleRemove = (index: number) => {
    const updated = conditions
      .filter((_, i) => i !== index)
      .map((c, i) => ({ ...c, sortOrder: i }));
    onChange(updated);
  };

  const handleFieldChange = (index: number, field: RuleConditionField) => {
    const updated = conditions.map((c, i) => {
      if (i !== index) return c;
      // BUG #2: always reset operator to the default for the new field type
      const operator = defaultOperatorForField(field);
      return { ...c, field, operator, value: '' };
    });
    onChange(updated);
  };

  const handleOperatorChange = (index: number, operator: RuleConditionOperator) => {
    const updated = conditions.map((c, i) =>
      i === index ? { ...c, operator } : c
    );
    onChange(updated);
  };

  const handleValueChange = (index: number, value: string) => {
    const updated = conditions.map((c, i) =>
      i === index ? { ...c, value } : c
    );
    onChange(updated);
  };

  return (
    <div className="space-y-3">
      {conditions.length === 0 && (
        <p className="text-sm text-text-secondary italic">
          {t('form.conditions.empty')}
        </p>
      )}

      {conditions.map((condition, index) => {
        const availableOperators = operatorsForField(condition.field);
        const isTypeField = condition.field === 'TRANSACTION_TYPE';
        const isAmountField = condition.field === 'AMOUNT';

        return (
          <div
            key={index}
            className="flex items-center gap-2 p-3 bg-surface rounded-lg border border-border"
          >
            {/* Field selector */}
            <select
              value={condition.field}
              onChange={(e) => handleFieldChange(index, e.target.value as RuleConditionField)}
              className="flex-1 min-w-[160px] h-9 rounded-md border border-border bg-background px-3 text-sm text-text-primary focus:outline-none focus:ring-1 focus:ring-primary"
              aria-label="Condition field"
            >
              {(Object.keys(FIELD_LABELS) as RuleConditionField[]).map((field) => (
                <option key={field} value={field}>
                  {FIELD_LABELS[field]}
                </option>
              ))}
            </select>

            {/* Operator selector */}
            <select
              value={condition.operator}
              onChange={(e) =>
                handleOperatorChange(index, e.target.value as RuleConditionOperator)
              }
              className="flex-1 min-w-[180px] h-9 rounded-md border border-border bg-background px-3 text-sm text-text-primary focus:outline-none focus:ring-1 focus:ring-primary"
              aria-label="Condition operator"
            >
              {availableOperators.map((op) => (
                <option key={op} value={op}>
                  {OPERATOR_LABELS[op]}
                </option>
              ))}
            </select>

            {/* Value input */}
            {isTypeField ? (
              <select
                value={condition.value}
                onChange={(e) => handleValueChange(index, e.target.value)}
                className="flex-1 h-9 rounded-md border border-border bg-background px-3 text-sm text-text-primary focus:outline-none focus:ring-1 focus:ring-primary"
                aria-label="Condition value"
              >
                <option value="">{t('form.conditions.selectType')}</option>
                {TRANSACTION_TYPE_VALUES.map(({ value, label }) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            ) : (
              <Input
                type={isAmountField ? 'number' : 'text'}
                step={isAmountField ? 'any' : undefined}
                min={isAmountField ? '0' : undefined}
                placeholder={isAmountField ? '0.00' : t('form.conditions.enterValue')}
                value={condition.value}
                onChange={(e) => handleValueChange(index, e.target.value)}
                className="flex-1"
                aria-label="Condition value"
              />
            )}

            {/* Remove button */}
            <button
              type="button"
              onClick={() => handleRemove(index)}
              className="p-2 rounded-lg hover:bg-surface-elevated text-red-500 shrink-0"
              aria-label={t('form.removeCondition')}
            >
              <Trash2 size={16} />
            </button>
          </div>
        );
      })}

      <Button type="button" variant="outline" size="sm" onClick={handleAdd}>
        <Plus size={16} className="mr-1" />
        {t('form.addCondition')}
      </Button>
    </div>
  );
}
