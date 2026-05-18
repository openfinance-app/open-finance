/**
 * RuleActionBuilder Component
 *
 * Renders a list of action rows for a transaction rule. Each row allows
 * selecting an action type and providing context-sensitive parameter inputs
 * based on the selected action. Rows can be added or removed dynamically.
 *
 * Action type parameter mapping:
 * - SET_CATEGORY  → actionValue: category name
 * - SET_PAYEE     → actionValue: payee name
 * - ADD_TAG       → actionValue: tag name
 * - SET_DESCRIPTION → actionValue: description text
 * - SET_AMOUNT    → actionValue: amount (number)
 * - ADD_SPLIT     → actionValue: category, actionValue2: amount, actionValue3: description (optional)
 * - SKIP_TRANSACTION → no parameters
 *
 * Requirement: REQ-TR-6.4, REQ-TR-6.5
 */
import { Plus, Trash2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { CategorySelect } from '@/components/ui/CategorySelect';
import { PayeeSelector } from '@/components/ui/PayeeSelector';
import { TagInput } from '@/components/transactions/TagInput';
import { useCategories } from '@/hooks/useCategories';
import { usePopularTags } from '@/hooks/useTransactionTags';
import type { RuleAction, RuleActionType } from '@/types/transactionRules';

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const ALL_ACTION_TYPES: RuleActionType[] = [
  'SET_CATEGORY',
  'SET_PAYEE',
  'ADD_TAG',
  'SET_DESCRIPTION',
  'SET_AMOUNT',
  'ADD_SPLIT',
  'SKIP_TRANSACTION',
];

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

/** An action row without the optional id — used during rule creation/editing */
export type ActionDraft = Omit<RuleAction, 'id'>;

interface RuleActionBuilderProps {
  /** Current list of action drafts */
  actions: ActionDraft[];
  /** Called whenever the actions list changes */
  onChange: (actions: ActionDraft[]) => void;
}

// ---------------------------------------------------------------------------
// Helper: parameter inputs per action type
// ---------------------------------------------------------------------------

interface ActionParamsProps {
  action: ActionDraft;
  index: number;
  onChange: (index: number, updated: Partial<ActionDraft>) => void;
  t: (key: string) => string;
}

function ActionParams({ action, index, onChange, t }: ActionParamsProps) {
  const { data: categories = [] } = useCategories();
  const { data: popularTags = [] } = usePopularTags();

  switch (action.actionType) {
    case 'SET_CATEGORY': {
      // CategorySelect uses numeric IDs; actionValue stores category name
      const selectedId = categories.find((c) => c.name === action.actionValue)?.id;
      return (
        <div className="flex-1">
          <CategorySelect
            value={selectedId}
            onValueChange={(id) => {
              const cat = categories.find((c) => c.id === id);
              onChange(index, { actionValue: cat?.name ?? '' });
            }}
            placeholder={t('form.actions.placeholders.category')}
            className="w-full"
          />
        </div>
      );
    }

    case 'SET_PAYEE':
      return (
        <div className="flex-1">
          <PayeeSelector
            value={action.actionValue || undefined}
            onValueChange={(val) => onChange(index, { actionValue: val ?? '' })}
            placeholder={t('form.actions.placeholders.payee')}
            allowNewPayee
            className="w-full"
          />
        </div>
      );

    case 'ADD_TAG': {
      const tags = action.actionValue
        ? action.actionValue.split(',').map((s) => s.trim()).filter(Boolean)
        : [];
      return (
        <div className="flex-1">
          <TagInput
            value={tags}
            onChange={(newTags) => onChange(index, { actionValue: newTags.join(',') })}
            suggestions={popularTags}
            placeholder={t('form.actions.placeholders.tag')}
            maxTags={10}
          />
        </div>
      );
    }

    case 'SET_DESCRIPTION':
      return (
        <Input
          placeholder={t('form.actions.placeholders.description')}
          value={action.actionValue ?? ''}
          onChange={(e) => onChange(index, { actionValue: e.target.value })}
          className="flex-1"
          aria-label="Description"
        />
      );

    case 'SET_AMOUNT':
      return (
        <Input
          type="number"
          step="0.01"
          placeholder="0.00"
          value={action.actionValue ?? ''}
          onChange={(e) => onChange(index, { actionValue: e.target.value })}
          className="flex-1"
          aria-label="Amount"
        />
      );

    case 'ADD_SPLIT':
      return (
        <div className="flex-1 flex flex-col gap-2">
          <Input
            placeholder={t('form.actions.placeholders.splitCategory')}
            value={action.actionValue ?? ''}
            onChange={(e) => onChange(index, { actionValue: e.target.value })}
            aria-label="Split category"
          />
          <Input
            type="number"
            step="0.01"
            placeholder={t('form.actions.placeholders.splitAmount')}
            value={action.actionValue2 ?? ''}
            onChange={(e) => onChange(index, { actionValue2: e.target.value })}
            aria-label="Split amount"
          />
          <Input
            placeholder={t('form.actions.placeholders.splitDescription')}
            value={action.actionValue3 ?? ''}
            onChange={(e) => onChange(index, { actionValue3: e.target.value })}
            aria-label="Split description"
          />
        </div>
      );

    case 'SKIP_TRANSACTION':
      return (
        <p className="flex-1 text-sm text-text-secondary italic">
          {t('form.actions.skipTransactionHint')}
        </p>
      );

    default:
      return null;
  }
}

// ---------------------------------------------------------------------------
// Main component
// ---------------------------------------------------------------------------

/**
 * Renders a dynamic list of action rows for building rule actions.
 * Requirement: REQ-TR-6.4, REQ-TR-6.5
 */
export function RuleActionBuilder({ actions, onChange }: RuleActionBuilderProps) {
  const { t } = useTranslation('rules');

  const ACTION_TYPE_LABELS: Record<RuleActionType, string> = {
    SET_CATEGORY: t('form.actions.types.SET_CATEGORY'),
    SET_PAYEE: t('form.actions.types.SET_PAYEE'),
    ADD_TAG: t('form.actions.types.ADD_TAG'),
    SET_DESCRIPTION: t('form.actions.types.SET_DESCRIPTION'),
    SET_AMOUNT: t('form.actions.types.SET_AMOUNT'),
    ADD_SPLIT: t('form.actions.types.ADD_SPLIT'),
    SKIP_TRANSACTION: t('form.actions.types.SKIP_TRANSACTION'),
  };

  const handleAdd = () => {
    const newAction: ActionDraft = {
      actionType: 'SET_CATEGORY',
      actionValue: '',
      actionValue2: undefined,
      actionValue3: undefined,
      sortOrder: actions.length,
    };
    onChange([...actions, newAction]);
  };

  const handleRemove = (index: number) => {
    const updated = actions
      .filter((_, i) => i !== index)
      .map((a, i) => ({ ...a, sortOrder: i }));
    onChange(updated);
  };

  const handleTypeChange = (index: number, actionType: RuleActionType) => {
    const updated = actions.map((a, i) =>
      i === index
        ? {
            ...a,
            actionType,
            actionValue: '',
            actionValue2: undefined,
            actionValue3: undefined,
          }
        : a
    );
    onChange(updated);
  };

  const handleParamChange = (index: number, patch: Partial<ActionDraft>) => {
    const updated = actions.map((a, i) =>
      i === index ? { ...a, ...patch } : a
    );
    onChange(updated);
  };

  return (
    <div className="space-y-3">
      {actions.length === 0 && (
        <p className="text-sm text-text-secondary italic">
          {t('form.actions.empty')}
        </p>
      )}

      {actions.map((action, index) => (
        <div
          key={index}
          className="flex items-start gap-2 p-3 bg-surface rounded-lg border border-border"
        >
          {/* Action type selector */}
          <select
            value={action.actionType}
            onChange={(e) => handleTypeChange(index, e.target.value as RuleActionType)}
            className="min-w-[160px] h-9 rounded-md border border-border bg-background px-3 text-sm text-text-primary focus:outline-none focus:ring-1 focus:ring-primary shrink-0"
            aria-label="Action type"
          >
            {ALL_ACTION_TYPES.map((type) => (
              <option key={type} value={type}>
                {ACTION_TYPE_LABELS[type]}
              </option>
            ))}
          </select>

          {/* Context-sensitive parameter inputs */}
          <ActionParams action={action} index={index} onChange={handleParamChange} t={t} />

          {/* Remove button */}
          <button
            type="button"
            onClick={() => handleRemove(index)}
            className="p-2 rounded-lg hover:bg-surface-elevated text-red-500 shrink-0 mt-0.5"
            aria-label={t('form.removeAction')}
          >
            <Trash2 size={16} />
          </button>
        </div>
      ))}

      <Button type="button" variant="outline" size="sm" onClick={handleAdd}>
        <Plus size={16} className="mr-1" />
        {t('form.addAction')}
      </Button>
    </div>
  );
}
