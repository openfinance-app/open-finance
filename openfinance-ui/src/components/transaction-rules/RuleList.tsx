/**
 * RuleList Component
 *
 * Displays a table of transaction rules with name, priority, enabled status,
 * condition count, action count, and controls to edit, delete, or toggle each rule.
 * Shows an empty state with a CTA when no rules exist.
 * Supports column sorting and shows condition/action details in tooltips.
 *
 * Requirement: REQ-TR-6.2, REQ-TR-6.6
 */
import { useState } from 'react';
import { Edit2, Trash2, Sliders, ChevronUp, ChevronDown, ChevronsUpDown } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Badge } from '@/components/ui/Badge';
import { EmptyState } from '@/components/layout/EmptyState';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/Tooltip';
import type {
  TransactionRule,
  RuleConditionField,
  RuleConditionOperator,
  RuleActionType,
} from '@/types/transactionRules';

// ---------------------------------------------------------------------------
// Sorting
// ---------------------------------------------------------------------------

type SortKey = 'name' | 'priority' | 'status' | 'conditions' | 'actions';
type SortDir = 'asc' | 'desc';

function sortRules(rules: TransactionRule[], key: SortKey, dir: SortDir): TransactionRule[] {
  return [...rules].sort((a, b) => {
    let cmp = 0;
    switch (key) {
      case 'name':
        cmp = a.name.localeCompare(b.name);
        break;
      case 'priority':
        cmp = a.priority - b.priority;
        break;
      case 'status':
        cmp = Number(b.isEnabled) - Number(a.isEnabled);
        break;
      case 'conditions':
        cmp = a.conditions.length - b.conditions.length;
        break;
      case 'actions':
        cmp = a.actions.length - b.actions.length;
        break;
    }
    return dir === 'asc' ? cmp : -cmp;
  });
}

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

interface RuleListProps {
  /** List of rules to display */
  rules: TransactionRule[];
  /** Called when the user clicks the Edit button for a rule */
  onEdit: (rule: TransactionRule) => void;
  /** Called when the user clicks the Delete button for a rule */
  onDelete: (rule: TransactionRule) => void;
  /** Called when the user clicks the enabled toggle for a rule */
  onToggle: (rule: TransactionRule) => void;
  /** Called when the user clicks the "Create your first rule" CTA */
  onCreateFirst: () => void;
  /** Whether any toggle/delete mutations are in progress */
  isMutating?: boolean;
}

// ---------------------------------------------------------------------------
// Sort header button
// ---------------------------------------------------------------------------

interface SortHeaderProps {
  label: string;
  sortKey: SortKey;
  currentKey: SortKey | null;
  currentDir: SortDir;
  onSort: (key: SortKey) => void;
  className?: string;
}

function SortHeader({
  label,
  sortKey,
  currentKey,
  currentDir,
  onSort,
  className,
}: SortHeaderProps) {
  const { t } = useTranslation('rules');
  const isActive = currentKey === sortKey;
  const ariaLabel = isActive
    ? currentDir === 'asc'
      ? t('list.sortDesc')
      : t('list.sortAsc')
    : t('list.sortAsc');

  return (
    <button
      type="button"
      onClick={() => onSort(sortKey)}
      aria-label={ariaLabel}
      className={`flex items-center gap-1 hover:text-text-primary transition-colors ${className ?? ''}`}
    >
      <span>{label}</span>
      {isActive ? (
        currentDir === 'asc' ? (
          <ChevronUp size={12} />
        ) : (
          <ChevronDown size={12} />
        )
      ) : (
        <ChevronsUpDown size={12} className="opacity-40" />
      )}
    </button>
  );
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

/**
 * Table displaying all transaction rules with management controls.
 * Requirement: REQ-TR-6.2, REQ-TR-6.6
 */
export function RuleList({
  rules,
  onEdit,
  onDelete,
  onToggle,
  onCreateFirst,
  isMutating,
}: RuleListProps) {
  const { t } = useTranslation('rules');
  const [sortKey, setSortKey] = useState<SortKey | null>(null);
  const [sortDir, setSortDir] = useState<SortDir>('asc');

  const handleSort = (key: SortKey) => {
    if (sortKey === key) {
      setSortDir(d => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortKey(key);
      setSortDir('asc');
    }
  };

  if (rules.length === 0) {
    return (
      <EmptyState
        icon={Sliders}
        title={t('list.emptyTitle')}
        description={t('list.emptyDescription')}
        action={{
          label: t('list.createFirst'),
          onClick: onCreateFirst,
        }}
      />
    );
  }

  const sorted = sortKey ? sortRules(rules, sortKey, sortDir) : rules;

  return (
    <TooltipProvider>
      <div className="bg-surface rounded-xl border border-border overflow-hidden">
        {/* Table header */}
        <div className="grid grid-cols-[1fr_80px_80px_70px_70px_120px] items-center gap-4 px-4 py-3 border-b border-border text-xs font-medium text-text-secondary uppercase tracking-wide">
          <SortHeader
            label={t('list.name')}
            sortKey="name"
            currentKey={sortKey}
            currentDir={sortDir}
            onSort={handleSort}
          />
          <SortHeader
            label={t('list.priority')}
            sortKey="priority"
            currentKey={sortKey}
            currentDir={sortDir}
            onSort={handleSort}
            className="justify-center"
          />
          <SortHeader
            label={t('list.status')}
            sortKey="status"
            currentKey={sortKey}
            currentDir={sortDir}
            onSort={handleSort}
            className="justify-center"
          />
          <SortHeader
            label={t('list.conditions')}
            sortKey="conditions"
            currentKey={sortKey}
            currentDir={sortDir}
            onSort={handleSort}
            className="justify-center"
          />
          <SortHeader
            label={t('list.actions')}
            sortKey="actions"
            currentKey={sortKey}
            currentDir={sortDir}
            onSort={handleSort}
            className="justify-center"
          />
          <span className="text-right">{t('list.controls')}</span>
        </div>

        {/* Table rows */}
        <div className="divide-y divide-border">
          {sorted.map(rule => (
            <RuleRow
              key={rule.id}
              rule={rule}
              onEdit={onEdit}
              onDelete={onDelete}
              onToggle={onToggle}
              isMutating={isMutating}
            />
          ))}
        </div>
      </div>
    </TooltipProvider>
  );
}

// ---------------------------------------------------------------------------
// Row sub-component
// ---------------------------------------------------------------------------

interface RuleRowProps {
  rule: TransactionRule;
  onEdit: (rule: TransactionRule) => void;
  onDelete: (rule: TransactionRule) => void;
  onToggle: (rule: TransactionRule) => void;
  isMutating?: boolean;
}

// Human-readable labels for condition fields and operators (used in tooltips)
const FIELD_SHORT: Record<RuleConditionField, string> = {
  DESCRIPTION: 'Desc.',
  AMOUNT: 'Amount',
  TRANSACTION_TYPE: 'Type',
};

const OP_SHORT: Record<RuleConditionOperator, string> = {
  CONTAINS: '∋',
  NOT_CONTAINS: '∌',
  EQUALS: '=',
  NOT_EQUALS: '≠',
  GREATER_THAN: '>',
  LESS_THAN: '<',
  GREATER_OR_EQUAL: '≥',
  LESS_OR_EQUAL: '≤',
};

const ACTION_SHORT: Record<RuleActionType, string> = {
  SET_CATEGORY: 'Cat.',
  SET_PAYEE: 'Payee',
  ADD_TAG: 'Tag',
  SET_DESCRIPTION: 'Desc.',
  SET_AMOUNT: 'Amount',
  ADD_SPLIT: 'Split',
  SKIP_TRANSACTION: 'Skip',
};

function RuleRow({ rule, onEdit, onDelete, onToggle, isMutating }: RuleRowProps) {
  const { t } = useTranslation('rules');

  const conditionTooltip = rule.conditions
    .map(c => `${FIELD_SHORT[c.field]} ${OP_SHORT[c.operator]} "${c.value}"`)
    .join('\n');

  const actionTooltip = rule.actions
    .map(a => {
      const base = ACTION_SHORT[a.actionType];
      return a.actionValue ? `${base}: ${a.actionValue}` : base;
    })
    .join('\n');

  return (
    <div className="grid grid-cols-[1fr_80px_80px_70px_70px_120px] items-center gap-4 px-4 py-3 hover:bg-background/50 transition-colors group">
      {/* Name */}
      <div className="min-w-0">
        <p className="text-sm font-medium text-text-primary truncate">{rule.name}</p>
      </div>

      {/* Priority */}
      <div className="text-center">
        <span className="text-sm text-text-secondary">{rule.priority}</span>
      </div>

      {/* Status badge */}
      <div className="flex justify-center">
        <Badge
          variant={rule.isEnabled ? 'default' : 'outline'}
          className={rule.isEnabled ? 'bg-green-500/10 text-green-600' : ''}
        >
          {rule.isEnabled ? t('list.active') : t('list.inactive')}
        </Badge>
      </div>

      {/* Condition count with tooltip */}
      <div className="flex justify-center">
        <Tooltip>
          <TooltipTrigger asChild>
            <span className="text-sm text-text-secondary cursor-default tabular-nums underline decoration-dotted underline-offset-2">
              {rule.conditions.length}
            </span>
          </TooltipTrigger>
          <TooltipContent side="top" className="whitespace-pre text-xs max-w-[260px]">
            {conditionTooltip || '—'}
          </TooltipContent>
        </Tooltip>
      </div>

      {/* Action count with tooltip */}
      <div className="flex justify-center">
        <Tooltip>
          <TooltipTrigger asChild>
            <span className="text-sm text-text-secondary cursor-default tabular-nums underline decoration-dotted underline-offset-2">
              {rule.actions.length}
            </span>
          </TooltipTrigger>
          <TooltipContent side="top" className="whitespace-pre text-xs max-w-[260px]">
            {actionTooltip || '—'}
          </TooltipContent>
        </Tooltip>
      </div>

      {/* Controls */}
      <div className="flex items-center justify-end gap-1">
        {/* Toggle enabled */}
        <button
          type="button"
          role="switch"
          aria-checked={rule.isEnabled}
          aria-label={rule.isEnabled ? t('list.disableRule') : t('list.enableRule')}
          disabled={isMutating}
          onClick={() => onToggle(rule)}
          className={`
            relative inline-flex h-5 w-9 items-center rounded-full transition-colors
            disabled:opacity-50 disabled:cursor-not-allowed
            focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-1
            ${rule.isEnabled ? 'bg-primary' : 'bg-border'}
          `}
        >
          <span
            className={`
              inline-block h-3.5 w-3.5 transform rounded-full bg-white shadow transition-transform
              ${rule.isEnabled ? 'translate-x-4' : 'translate-x-0.5'}
            `}
          />
        </button>

        {/* Edit */}
        <button
          type="button"
          onClick={() => onEdit(rule)}
          disabled={isMutating}
          className="p-1.5 rounded-lg hover:bg-surface-elevated disabled:opacity-50 disabled:cursor-not-allowed"
          aria-label={t('list.editRule')}
        >
          <Edit2 size={15} className="text-text-secondary" />
        </button>

        {/* Delete */}
        <button
          type="button"
          onClick={() => onDelete(rule)}
          disabled={isMutating}
          className="p-1.5 rounded-lg hover:bg-surface-elevated disabled:opacity-50 disabled:cursor-not-allowed"
          aria-label={t('list.deleteRule')}
        >
          <Trash2 size={15} className="text-red-500" />
        </button>
      </div>
    </div>
  );
}
