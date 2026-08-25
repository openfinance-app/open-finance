/**
 * RuleForm Component
 *
 * Dialog/modal for creating and editing a transaction rule. Wraps
 * RuleConditionBuilder and RuleActionBuilder for composing the full rule.
 *
 * Validation:
 * - name is required (non-empty)
 * - at least one condition must be present
 * - at least one action must be present
 *
 * Requirement: REQ-TR-6.3, REQ-TR-6.4
 */
import { useState, useLayoutEffect } from 'react';
import { AlertCircle } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/Dialog';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { NumberInput } from '@/components/ui/NumberInput';
import { HelpTooltip } from '@/components/ui/HelpTooltip';
import { Alert, AlertDescription } from '@/components/ui/Alert';
import { RuleConditionBuilder, type ConditionDraft } from './RuleConditionBuilder';
import { RuleActionBuilder, type ActionDraft } from './RuleActionBuilder';
import type { TransactionRule, TransactionRuleRequest } from '@/types/transactionRules';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

interface RuleFormProps {
  /** Whether the dialog is visible */
  open: boolean;
  /** Called when the dialog open state changes */
  onOpenChange: (open: boolean) => void;
  /**
   * Existing rule to edit. When undefined, the form is in "create" mode.
   */
  rule?: TransactionRule | null;
  /**
   * Called with the final request payload when the user clicks Save.
   * The parent is responsible for calling the create/update mutation.
   */
  onSubmit: (data: TransactionRuleRequest) => void;
  /** Whether a save operation is in progress */
  isLoading?: boolean;
  /** Error message from the parent mutation (e.g. API failure) */
  submitError?: string | null;
  /** All existing rules, used to prevent duplicate names */
  existingRules?: TransactionRule[];
}

// ---------------------------------------------------------------------------
// Default form state helpers
// ---------------------------------------------------------------------------

function emptyForm(): {
  name: string;
  priority: number;
  isEnabled: boolean;
  conditionMatch: 'AND' | 'OR';
  conditions: ConditionDraft[];
  actions: ActionDraft[];
} {
  return {
    name: '',
    priority: 0,
    isEnabled: true,
    conditionMatch: 'AND',
    conditions: [],
    actions: [],
  };
}

function formFromRule(rule: TransactionRule) {
  return {
    name: rule.name,
    priority: rule.priority,
    isEnabled: rule.isEnabled,
    conditionMatch: (rule.conditionMatch ?? 'AND') as 'AND' | 'OR',
    conditions: rule.conditions.map(({ field, operator, value, sortOrder }) => ({
      field,
      operator,
      value,
      sortOrder,
    })),
    actions: rule.actions.map(
      ({ actionType, actionValue, actionValue2, actionValue3, sortOrder }) => ({
        actionType,
        actionValue,
        actionValue2,
        actionValue3,
        sortOrder,
      })
    ),
  };
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

/**
 * Dialog form for creating or editing a transaction rule.
 * Requirement: REQ-TR-6.3, REQ-TR-6.4
 */
export function RuleForm({ open, onOpenChange, rule, onSubmit, isLoading, submitError, existingRules = [] }: RuleFormProps) {
  const [formState, setFormState] = useState(emptyForm);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const { t } = useTranslation('rules');

  // Sync form state when the dialog opens or the target rule changes
  useLayoutEffect(() => {
    if (open) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setFormState(rule ? formFromRule(rule) : emptyForm());
      setErrors({});
    }
  }, [open, rule]);

  // ---------------------------------------------------------------------------
  // Validation
  // ---------------------------------------------------------------------------

  const validate = (): boolean => {
    const newErrors: Record<string, string> = {};

    if (!formState.name.trim()) {
      newErrors.name = t('form.errors.nameRequired');
    } else if (formState.name.trim().length > 100) {
      newErrors.name = t('form.errors.nameTooLong');
    } else {
      // BUG #4: duplicate name check (skip current rule when editing)
      const isDuplicate = existingRules.some(
        (r) => r.name.toLowerCase() === formState.name.trim().toLowerCase() && r.id !== rule?.id
      );
      if (isDuplicate) {
        newErrors.name = t('form.errors.nameDuplicate');
      }
    }

    // BUG #3: negative priority
    if (formState.priority < 0) {
      newErrors.priority = t('form.errors.priorityNegative');
    }

    if (formState.conditions.length === 0) {
      newErrors.conditions = t('form.errors.conditionRequired');
    } else {
      // BUG #1: validate condition values
      const hasEmptyConditionValue = formState.conditions.some((c) => !c.value.trim());
      if (hasEmptyConditionValue) {
        newErrors.conditions = t('form.errors.conditionValueRequired');
      }
    }

    if (formState.actions.length === 0) {
      newErrors.actions = t('form.errors.actionRequired');
    } else {
      // BUG #1: validate action values (SKIP_TRANSACTION needs no value)
      const hasEmptyActionValue = formState.actions.some(
        (a) => a.actionType !== 'SKIP_TRANSACTION' && !a.actionValue?.trim()
      );
      if (hasEmptyActionValue) {
        newErrors.actions = t('form.errors.actionValueRequired');
      }
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  // ---------------------------------------------------------------------------
  // Handlers
  // ---------------------------------------------------------------------------

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate()) return;

    const payload: TransactionRuleRequest = {
      name: formState.name.trim(),
      priority: formState.priority,
      isEnabled: formState.isEnabled,
      conditionMatch: formState.conditionMatch,
      conditions: formState.conditions,
      actions: formState.actions,
    };

    onSubmit(payload);
  };

  const handleClose = () => {
    if (!isLoading) {
      onOpenChange(false);
    }
  };

  // ---------------------------------------------------------------------------
  // Render
  // ---------------------------------------------------------------------------

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[640px] max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{rule ? t('form.editTitle') : t('form.createTitle')}</DialogTitle>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-6">
          {/* Name */}
          <div className="space-y-1">
            <label className="text-sm font-medium text-text-primary" htmlFor="rule-name">
              {t('form.name')} <span className="text-red-500">*</span>
            </label>
            <Input
              id="rule-name"
              value={formState.name}
              onChange={(e) =>
                setFormState((prev) => ({ ...prev, name: e.target.value }))
              }
              placeholder={t('form.namePlaceholder')}
              maxLength={100}
            />
            {errors.name && (
              <p className="text-xs text-red-500">{errors.name}</p>
            )}
          </div>

          {/* Priority & Enabled row */}
          <div className="flex items-center gap-6">
            <div className="space-y-1 flex-1">
              <div className="flex items-center gap-1">
                <label className="text-sm font-medium text-text-primary" htmlFor="rule-priority">
                  {t('form.priority')}
                </label>
                <HelpTooltip text={t('form.priorityHint')} side="right" />
              </div>
              <NumberInput
                id="rule-priority"
                min={0}
                value={formState.priority}
                onChange={(value) =>
                  setFormState((prev) => ({
                    ...prev,
                    priority: Number(value) || 0,
                  }))
                }
              />
              {errors.priority && (
                <p className="text-xs text-red-500">{errors.priority}</p>
              )}
            </div>

            <div className="space-y-1">
              <label className="text-sm font-medium text-text-primary">
                {t('form.enabled')}
              </label>
              <div className="flex items-center gap-2 pt-1">
                <button
                  type="button"
                  role="switch"
                  aria-checked={formState.isEnabled}
                  onClick={() =>
                    setFormState((prev) => ({
                      ...prev,
                      isEnabled: !prev.isEnabled,
                    }))
                  }
                  className={`
                    relative inline-flex h-6 w-11 items-center rounded-full transition-colors
                    focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2
                    ${formState.isEnabled ? 'bg-primary' : 'bg-border'}
                  `}
                >
                  <span
                    className={`
                      inline-block h-4 w-4 transform rounded-full bg-white transition-transform
                      ${formState.isEnabled ? 'translate-x-6' : 'translate-x-1'}
                    `}
                  />
                </button>
                <span className="text-sm text-text-secondary">
                  {formState.isEnabled ? t('form.active') : t('form.inactive')}
                </span>
              </div>
            </div>
          </div>

          {/* Conditions */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-1">
                <h3 className="text-sm font-medium text-text-primary">
                  {t('form.conditionsLabel')} <span className="text-red-500">*</span>
                </h3>
                <HelpTooltip
                  text={
                    formState.conditionMatch === 'OR'
                      ? t('form.conditionsHintOr', 'Transaction matches if any condition is met.')
                      : t('form.conditionsHint')
                  }
                  side="right"
                />
              </div>
              <div className="flex items-center gap-1 rounded-md border border-border p-0.5">
                <button
                  type="button"
                  onClick={() => setFormState((prev) => ({ ...prev, conditionMatch: 'AND' }))}
                  className={`rounded px-2 py-0.5 text-xs font-medium transition-colors ${
                    formState.conditionMatch === 'AND'
                      ? 'bg-primary text-white'
                      : 'text-text-secondary hover:text-text-primary'
                  }`}
                >
                  {t('form.conditionMatchAnd', 'ALL (AND)')}
                </button>
                <button
                  type="button"
                  onClick={() => setFormState((prev) => ({ ...prev, conditionMatch: 'OR' }))}
                  className={`rounded px-2 py-0.5 text-xs font-medium transition-colors ${
                    formState.conditionMatch === 'OR'
                      ? 'bg-primary text-white'
                      : 'text-text-secondary hover:text-text-primary'
                  }`}
                >
                  {t('form.conditionMatchOr', 'ANY (OR)')}
                </button>
              </div>
            </div>
            <RuleConditionBuilder
              conditions={formState.conditions}
              onChange={(conditions) =>
                setFormState((prev) => ({ ...prev, conditions }))
              }
            />
            {errors.conditions && (
              <p className="text-xs text-red-500">{errors.conditions}</p>
            )}
          </div>

          {/* Actions */}
          <div className="space-y-2">
            <div className="flex items-center gap-1">
              <h3 className="text-sm font-medium text-text-primary">
                {t('form.actionsLabel')} <span className="text-red-500">*</span>
              </h3>
              <HelpTooltip text={t('form.actionsHint')} side="right" />
            </div>
            <RuleActionBuilder
              actions={formState.actions}
              onChange={(actions) =>
                setFormState((prev) => ({ ...prev, actions }))
              }
            />
            {errors.actions && (
              <p className="text-xs text-red-500">{errors.actions}</p>
            )}
          </div>

          {/* Form buttons */}
          <div className="space-y-3 pt-2 border-t border-border">
            {/* BUG #1: API / save error displayed inline */}
            {submitError && (
              <Alert variant="error" className="flex items-start gap-2">
                <AlertCircle size={16} className="mt-0.5 shrink-0" />
                <AlertDescription>{submitError}</AlertDescription>
              </Alert>
            )}
            <div className="flex justify-end gap-3">
              <Button
                type="button"
                variant="outline"
                onClick={handleClose}
                disabled={isLoading}
              >
                {t('form.cancel')}
              </Button>
              <Button type="submit" isLoading={isLoading}>
                {rule ? t('form.update') : t('form.create')}
              </Button>
            </div>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
