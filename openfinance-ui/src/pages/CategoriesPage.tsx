/**
 * CategoriesPage — the vault filing wall.
 *
 * CONTRACT
 * THESIS: Categories are a filing wall of drawers, not a spreadsheet. A brass
 * root rail pulls one drawer; its slots read as a fixed-column departure
 * board. Refuses the three stat cards and the flat endless tree-table.
 * OWN-WORLD: Blackened-steel plates, hairline-brass dividers, Marcellus plate
 * title, Inter UI, JetBrains Mono tabular figures. Category hues stay a fixed
 * mineral language; brass marks selection and primary action only.
 * STORY: The owner finds a drawer, scans slots by transactions and net, and
 * acts inline. Search flattens drawers with a parent chip; sort reranks rows
 * in place with identity preserved.
 * FIRST VIEWPORT: Header with brass Add Category; one ledger strip (filed /
 * drawers / slots); below, a 272px root rail left and the slot board right
 * with search plus segmented sort.
 * FORM: Grounded structure 7 of 7 (filing-rail + slot list), seed 4a0b83c9,
 * raised by the split-flap row-identity discipline and the gate-board
 * in-place rerank discipline.
 * FINISH: unreviewed and undocumented is unfinished; this build ends with the
 * finish review, the verdict, DESIGN.md, and every shipping raster carrying
 * its provenance
 */
import { useState, useEffect, useMemo, type CSSProperties } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Plus,
  ChevronRight,
  ChevronDown,
  FolderOpen,
  Edit2,
  Trash2,
  Tag,
  Search,
  ArrowDownAZ,
  Hash,
  Coins,
  Layers,
} from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Badge } from '@/components/ui/Badge';
import { CATEGORY_COLOR_SWATCHES, CATEGORY_COLOR_FALLBACK } from '@/constants/colors';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/Dialog';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/layout/EmptyState';
import { LoadingSkeleton } from '@/components/LoadingComponents';
import { ConfirmationDialog } from '@/components/ConfirmationDialog';
import { CategorySelect } from '@/components/ui/CategorySelect';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import {
  useCategoryTree,
  useCreateCategory,
  useUpdateCategory,
  useDeleteCategory,
} from '@/hooks/useTransactions';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { RegexToggle } from '@/components/ui/RegexToggle';
import { matchesQuery } from '@/utils/searchMatch';
import { useSecondaryConversion } from '@/hooks/useSecondaryConversion';
import { useAuthContext } from '@/context/AuthContext';
import type { CategoryTreeNode } from '@/types/transaction';

/** Flat node with its parent's name attached for search-result breadcrumbs. */
interface FlatCategoryNode extends CategoryTreeNode {
  parentName?: string;
}

/**
 * CategoryTree component - displays hierarchical category tree with expand/collapse
 */
interface CategoryTreeProps {
  categories: CategoryTreeNode[];
  onEdit: (category: CategoryTreeNode) => void;
  onDelete: (category: CategoryTreeNode) => void;
  startIndex?: number;
}

function CategoryTree({ categories, onEdit, onDelete, startIndex = 0 }: CategoryTreeProps) {
  return (
    <div>
      {categories.map((category, i) => (
        <TreeNode
          key={category.id}
          node={category}
          index={startIndex + i}
          onEdit={onEdit}
          onDelete={onDelete}
        />
      ))}
    </div>
  );
}

/**
 * TreeNode component - one slot on the departure board.
 * Columns never move (chevron / medallion / name / txns / net / actions) so
 * reranks read as row moves, in the split-flap discipline.
 */
interface TreeNodeProps {
  node: CategoryTreeNode;
  depth?: number;
  index?: number;
  parentName?: string;
  onEdit: (category: CategoryTreeNode) => void;
  onDelete: (category: CategoryTreeNode) => void;
}

function TreeNode({ node, depth = 0, index = 0, parentName, onEdit, onDelete }: TreeNodeProps) {
  const { t } = useTranslation('categories');
  const [isExpanded, setIsExpanded] = useState(depth < 1); // Expand first level by default
  const hasChildren = Array.isArray(node?.subcategories) && node.subcategories.length > 0;
  const { baseCurrency } = useAuthContext();
  const reportingCurrency = node?.currency || baseCurrency;
  const {
    convert,
    secondaryCurrency: secCurrency,
    secondaryExchangeRate,
  } = useSecondaryConversion(reportingCurrency);

  if (!node) return null;

  return (
    <div
      className="select-none stagger-item"
      style={{ '--stagger-index': Math.min(index, 11) } as CSSProperties}
    >
      <div className="group grid grid-cols-[auto_auto_minmax(0,1fr)_auto] items-center gap-x-2 rounded-lg px-3 py-2.5 transition-colors hover:bg-surface-elevated sm:flex sm:gap-3">
        {/* Depth Spacer */}
        {depth > 0 && <div style={{ width: depth * 20 }} className="hidden shrink-0 sm:block" />}

        {/* Expand/Collapse Button */}
        <div className="col-start-1 row-start-1 row-span-2 flex w-6 shrink-0 justify-center self-center sm:col-auto sm:row-auto sm:row-span-1">
          <button
            type="button"
            onClick={() => setIsExpanded(!isExpanded)}
            className={`
              p-0.5 rounded hover:bg-surface-elevated
              ${!hasChildren && 'invisible'}
            `}
            aria-label={isExpanded ? t('badges.collapse') : t('badges.expand')}
            aria-expanded={hasChildren ? isExpanded : undefined}
          >
            {isExpanded ? (
              <ChevronDown size={16} className="text-text-secondary" />
            ) : (
              <ChevronRight size={16} className="text-text-secondary" />
            )}
          </button>
        </div>

        {/* Medallion — the category hue is a fixed language, never a state */}
        <div
          className="col-start-2 row-start-1 row-span-2 w-9 h-9 shrink-0 self-center rounded-[10px] flex items-center justify-center text-sm font-medium text-white ring-1 ring-inset ring-black/25 sm:col-auto sm:row-auto sm:row-span-1"
          style={{ backgroundColor: node.color || CATEGORY_COLOR_FALLBACK }}
          aria-hidden="true"
        >
          {node.icon ? (
            <span className="text-sm leading-none">{node.icon}</span>
          ) : (
            <FolderOpen size={16} />
          )}
        </div>

        {/* Name */}
        <div className="col-start-3 row-start-1 min-w-0 sm:flex-1">
          <div className="flex flex-wrap items-center gap-x-2 gap-y-1 min-w-0">
            <span
              className="font-medium text-text-primary break-words leading-snug"
              title={node.name || undefined}
            >
              {node.name || t('unknownCategory')}
            </span>
            {node.isSystem && (
              <Badge variant="outline" className="text-xs shrink-0">
                {t('badges.system')}
              </Badge>
            )}
          </div>
          <div className="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-xs text-text-secondary">
            {parentName && (
              <span className="inline-flex min-w-0 items-center gap-0.5 text-text-tertiary">
                <ChevronRight size={12} className="shrink-0" />
                <span className="truncate">{parentName}</span>
              </span>
            )}
            {node.mccCode && <span className="shrink-0">MCC: {node.mccCode}</span>}
          </div>
        </div>

        {/* Transaction Count — second line under the name on narrow screens */}
        <div className="col-start-3 row-start-2 mt-0.5 text-xs text-text-secondary tabular-nums sm:mt-0 sm:w-[92px] sm:shrink-0 sm:text-sm sm:text-right lg:w-[120px]">
          {t('transactionCount', { count: node.transactionCount || 0 })}
        </div>

        {/* Total Amount */}
        <div
          className={`col-start-4 row-start-1 self-start sm:col-auto sm:row-auto sm:self-auto w-[104px] shrink-0 text-sm text-right font-medium tabular-nums number-display lg:w-[120px] ${(node.totalAmount ?? 0) > 0 ? 'text-green-600' : (node.totalAmount ?? 0) < 0 ? 'text-red-600' : 'text-text-secondary'}`}
        >
          <ConvertedAmount
            amount={node.totalAmount ?? 0}
            currency={reportingCurrency}
            isConverted={false}
            secondaryAmount={convert(node.totalAmount ?? 0)}
            secondaryCurrency={secCurrency}
            secondaryExchangeRate={secondaryExchangeRate}
            inline
          />
        </div>

        {/* Actions — always reachable by touch and keyboard, revealed on hover for fine pointers */}
        <div className="col-start-4 row-start-2 flex shrink-0 items-center gap-0.5 self-center justify-self-end transition-opacity sm:col-auto sm:row-auto sm:w-[76px] sm:justify-end lg:opacity-0 lg:group-hover:opacity-100 lg:group-focus-within:opacity-100 lg:focus-within:opacity-100">
          <button
            type="button"
            onClick={() => onEdit(node)}
            className="p-2 rounded-lg hover:bg-surface-elevated focus-visible:opacity-100"
            aria-label={t('aria.editCategory')}
          >
            <Edit2 size={16} className="text-text-secondary" />
          </button>
          {!node.isSystem && (
            <button
              type="button"
              onClick={() => onDelete(node)}
              className="p-2 rounded-lg hover:bg-surface-elevated"
              aria-label={t('aria.deleteCategory')}
            >
              <Trash2 size={16} className="text-red-500" />
            </button>
          )}
        </div>
      </div>

      {/* Children */}
      {hasChildren && isExpanded && (
        <div className="border-l border-border ml-[22px] pl-1">
          {node.subcategories.map((child, i) => (
            <TreeNode
              key={child.id}
              node={child}
              depth={depth + 1}
              index={i}
              onEdit={onEdit}
              onDelete={onDelete}
            />
          ))}
        </div>
      )}
    </div>
  );
}

/**
 * CategoryFormDialog component - Add/Edit category dialog
 */
interface CategoryFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  category?: CategoryTreeNode | null;
  onSubmit: (data: CategoryFormData) => void;
  isLoading?: boolean;
  error?: string | null;
}

interface CategoryFormData {
  name: string;
  parentId?: number;
  icon?: string;
  color?: string;
}

const ICONS = [
  '🍔',
  '🚗',
  '🏠',
  '💊',
  '🛒',
  '📺',
  '✈️',
  '💰',
  '🎁',
  '💳',
  '🏥',
  '📱',
  '🎮',
  '👕',
  '🏋️',
  '📚',
  '🎵',
  '☕',
  '🚿',
  '💼',
];

export function CategoryFormDialog({
  open,
  onOpenChange,
  category,
  onSubmit,
  isLoading,
  error,
}: CategoryFormDialogProps) {
  const { t } = useTranslation('categories');
  const [formData, setFormData] = useState<CategoryFormData>({
    name: '',
    parentId: undefined,
    icon: '📁',
    color: CATEGORY_COLOR_FALLBACK,
  });

  // Bug #4 fix: reset form whenever the dialog opens (watch both `open` and `category`)
  useEffect(() => {
    if (!open) return;
    if (category) {
      setFormData({
        name: category.name,
        parentId: category.parentId || undefined,
        icon: category.icon || '📁',
        color: category.color || CATEGORY_COLOR_FALLBACK,
      });
    } else {
      setFormData({
        name: '',
        parentId: undefined,
        icon: '📁',
        color: CATEGORY_COLOR_FALLBACK,
      });
    }
  }, [category, open]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSubmit(formData);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[500px]">
        <DialogHeader>
          <DialogTitle>{category ? t('form.editTitle') : t('form.addTitle')}</DialogTitle>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Bug #1 fix: display API error */}
          {error && (
            <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-800 dark:bg-red-950 dark:text-red-400">
              {error}
            </div>
          )}

          {/* Name */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <label htmlFor="category-name" className="text-sm font-medium text-text-primary">
                {t('form.name')}
              </label>
              {/* Bug #6 fix: character counter */}
              <span
                className={`text-xs ${formData.name.length > 90 ? 'text-red-500' : 'text-text-secondary'}`}
              >
                {formData.name.length}/100
              </span>
            </div>
            <Input
              id="category-name"
              value={formData.name}
              onChange={e => setFormData(previous => ({ ...previous, name: e.target.value }))}
              placeholder={t('form.name')}
              maxLength={100}
              required
            />
          </div>

          {/* Parent Category */}
          <div className="space-y-2">
            <label className="text-sm font-medium text-text-primary">
              {t('form.parentCategory')}
            </label>
            <CategorySelect
              value={formData.parentId}
              onValueChange={value => setFormData(previous => ({ ...previous, parentId: value }))}
              placeholder={t('form.selectParentCategory')}
              allowNone={true}
            />
          </div>

          {/* Color */}
          <div className="space-y-2">
            <label className="text-sm font-medium text-text-primary">{t('form.color')}</label>
            <div className="flex flex-wrap gap-2">
              {CATEGORY_COLOR_SWATCHES.map(color => (
                <button
                  key={color}
                  type="button"
                  onClick={() => setFormData(previous => ({ ...previous, color }))}
                  className={`
                    w-8 h-8 rounded-lg transition-transform
                    ${formData.color === color ? 'ring-2 ring-offset-2 ring-primary scale-110' : ''}
                  `}
                  style={{ backgroundColor: color }}
                />
              ))}
            </div>
          </div>

          {/* Icon */}
          <div className="space-y-2">
            <label className="text-sm font-medium text-text-primary">{t('form.icon')}</label>
            <div className="flex flex-wrap gap-2">
              {ICONS.map(icon => (
                <button
                  key={icon}
                  type="button"
                  aria-label={t('form.chooseIcon', { icon })}
                  aria-pressed={formData.icon === icon}
                  onClick={() => setFormData(previous => ({ ...previous, icon }))}
                  className={`
                    w-10 h-10 rounded-lg border flex items-center justify-center text-lg
                    transition-colors
                    ${
                      formData.icon === icon
                        ? 'border-primary bg-primary/10'
                        : 'border-border hover:bg-surface'
                    }
                  `}
                >
                  {icon}
                </button>
              ))}
            </div>
          </div>

          {/* Actions */}
          <div className="flex justify-end gap-3 pt-4">
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              {t('form.cancel')}
            </Button>
            <Button type="submit" disabled={isLoading}>
              {isLoading ? t('form.saving') : category ? t('form.update') : t('form.create')}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

/**
 * RailButton — one drawer front on the root rail.
 */
interface RailButtonProps {
  active: boolean;
  name: string;
  title?: string;
  medallion: React.ReactNode;
  meta?: string;
  onClick: () => void;
}

function RailButton({ active, name, title, medallion, meta, onClick }: RailButtonProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-current={active ? 'true' : undefined}
      title={title ?? name}
      className={`
        flex items-center gap-2.5 rounded-xl border px-2.5 py-2 text-left transition-colors
        shrink-0 snap-start lg:shrink lg:snap-none w-auto lg:w-full
        ${
          active
            ? 'border-border-strong bg-surface-elevated shadow-slot'
            : 'border-transparent hover:border-border hover:bg-surface'
        }
      `}
    >
      <span className="shrink-0">{medallion}</span>
      <span className="min-w-0 flex-1">
        <span
          className={`block truncate text-sm ${active ? 'font-semibold text-text-primary' : 'font-medium text-text-secondary'}`}
        >
          {name}
        </span>
      </span>
      {meta && (
        <span className="shrink-0 rounded-md border border-border bg-background px-1.5 py-0.5 font-mono text-[11px] tabular-nums text-text-secondary">
          {meta}
        </span>
      )}
      {active && <span className="h-5 w-1 shrink-0 rounded-full bg-primary" aria-hidden="true" />}
    </button>
  );
}

/**
 * Main CategoriesPage component — the filing wall.
 */
type SortOption = 'name' | 'transactions' | 'amount';

export default function CategoriesPage() {
  const { t } = useTranslation('categories');
  useDocumentTitle(t('title'));

  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingCategory, setEditingCategory] = useState<CategoryTreeNode | null>(null);
  const [deletingCategory, setDeletingCategory] = useState<CategoryTreeNode | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchRegex, setSearchRegex] = useState(false);
  const [sortBy, setSortBy] = useState<SortOption>('name');
  const [formError, setFormError] = useState<string | null>(null);
  const [selectedRoot, setSelectedRoot] = useState<number | 'all'>('all');

  const { data: categories = [], isLoading, error } = useCategoryTree();
  const createCategory = useCreateCategory();
  const updateCategory = useUpdateCategory();
  const deleteCategory = useDeleteCategory();
  const { baseCurrency } = useAuthContext();

  const compareNodes = (a: CategoryTreeNode, b: CategoryTreeNode): number => {
    switch (sortBy) {
      case 'transactions':
        return (b.transactionCount || 0) - (a.transactionCount || 0);
      case 'amount':
        return (b.totalAmount || 0) - (a.totalAmount || 0);
      case 'name':
      default:
        return (a.name || '').localeCompare(b.name || '');
    }
  };

  // Flatten categories, attaching each node's parent name for search breadcrumbs
  const flattenWithParents = (
    cats: CategoryTreeNode[],
    parent?: CategoryTreeNode
  ): FlatCategoryNode[] => {
    const result: FlatCategoryNode[] = [];
    const traverse = (nodes: CategoryTreeNode[], parentNode?: CategoryTreeNode) => {
      for (const node of nodes) {
        // Clone node without subcategories to prevent duplicate rendering when flattened
        const flatNode: FlatCategoryNode = {
          ...node,
          subcategories: [],
          parentName: parentNode?.name,
        };
        result.push(flatNode);
        if (node.subcategories && node.subcategories.length > 0) {
          traverse(node.subcategories, node);
        }
      }
    };
    traverse(cats, parent);
    return result;
  };

  // Fully sorted hierarchy — the single source the rail and the board read from
  const sortedTree = useMemo(() => {
    const sortTree = (nodes: CategoryTreeNode[]): CategoryTreeNode[] => {
      const sorted = [...nodes].sort(compareNodes);
      return sorted.map(node => ({
        ...node,
        subcategories: node.subcategories ? sortTree(node.subcategories) : [],
      }));
    };
    return sortTree(categories);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [categories, sortBy]);

  const searchTrimmed = searchQuery.trim();
  const searchResults = useMemo(() => {
    if (!searchTrimmed) return [];
    return flattenWithParents(categories)
      .filter(c => matchesQuery(c.name, searchTrimmed, searchRegex))
      .sort(compareNodes);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [categories, searchTrimmed, searchRegex, sortBy]);

  const selectedNode =
    selectedRoot === 'all' ? null : (sortedTree.find(c => c.id === selectedRoot) ?? null);
  const drawerCurrency = selectedNode?.currency || baseCurrency;
  const drawerConversion = useSecondaryConversion(drawerCurrency);

  // The open drawer falls back to the full wall when its root disappears
  useEffect(() => {
    if (selectedRoot !== 'all' && !sortedTree.some(c => c.id === selectedRoot)) {
      setSelectedRoot('all');
    }
  }, [sortedTree, selectedRoot]);

  const handleCreate = () => {
    setFormError(null);
    setEditingCategory(null);
    setIsFormOpen(true);
  };

  const handleEdit = (category: CategoryTreeNode) => {
    setFormError(null);
    setEditingCategory(category);
    setIsFormOpen(true);
  };

  const handleDelete = (category: CategoryTreeNode) => {
    setDeletingCategory(category);
  };

  const handleFormSubmit = async (data: CategoryFormData) => {
    try {
      if (editingCategory) {
        await updateCategory.mutateAsync({
          id: editingCategory.id,
          data: {
            name: data.name,
            parentId: data.parentId,
            icon: data.icon,
            color: data.color,
          },
        });
      } else {
        await createCategory.mutateAsync({
          name: data.name,
          parentId: data.parentId,
          icon: data.icon,
          color: data.color,
        });
      }
      setFormError(null);
      setIsFormOpen(false);
      setEditingCategory(null);
    } catch (err: unknown) {
      console.error('Failed to save category:', err);
      // Bug #1 fix: extract and display the API error message
      const axiosErr = err as { response?: { data?: { message?: string } } };
      const message = axiosErr?.response?.data?.message ?? t('form.saveError');
      setFormError(message);
    }
  };

  const handleDeleteConfirm = async () => {
    if (!deletingCategory) return;

    try {
      await deleteCategory.mutateAsync(deletingCategory.id);
      setDeletingCategory(null);
    } catch (error) {
      console.error('Failed to delete category:', error);
    }
  };

  // Ledger figures across the whole wall
  const flatAllCategories = useMemo(() => flattenWithParents(categories), [categories]);
  const totalCategories = flatAllCategories.length;
  const rootCategories = categories.length;
  const subcategories = totalCategories - rootCategories;
  // Wall totals read from the roots only — roots already aggregate their
  // subtrees, so summing the flat list would double-count every slot.
  const totalTransactions = useMemo(
    () => categories.reduce((sum, c) => sum + (c.transactionCount || 0), 0),
    [categories]
  );

  const sortOptions: { value: SortOption; label: string; short: string; Icon: typeof Hash }[] = [
    { value: 'name', label: t('sort.byName'), short: t('table.name'), Icon: ArrowDownAZ },
    {
      value: 'transactions',
      label: t('sort.byTransactions'),
      short: t('table.transactionCount'),
      Icon: Hash,
    },
    { value: 'amount', label: t('sort.byAmount'), short: t('table.totalAmount'), Icon: Coins },
  ];

  const isSearching = searchTrimmed.length > 0;
  const boardListKey = `${selectedRoot}-${isSearching ? 'search' : 'tree'}`;

  return (
    <div className="space-y-5">
      <PageHeader
        title={t('title')}
        description={t('description')}
        actions={
          <Button onClick={handleCreate}>
            <Plus size={20} className="mr-2" />
            {t('addCategory')}
          </Button>
        }
      />

      {/* Ledger strip — one engraved plate, not four cards.
          Hairlines come from the 1px gap on a border ground, so the cells
          reflow cleanly from two columns up to four. */}
      <section
        aria-label={`${t('summary.totalCategories')} · ${t('summary.rootCategories')} · ${t('summary.subcategories')} · ${t('summary.totalTransactions')}`}
        className="grid grid-cols-2 gap-px overflow-hidden rounded-xl border border-border bg-border shadow-plate sm:grid-cols-4"
      >
        {[
          { value: totalCategories.toLocaleString(), label: t('summary.totalCategories') },
          { value: rootCategories.toLocaleString(), label: t('summary.rootCategories') },
          { value: subcategories.toLocaleString(), label: t('summary.subcategories') },
          { value: totalTransactions.toLocaleString(), label: t('summary.totalTransactions') },
        ].map(cell => (
          <div key={cell.label} className="min-w-0 bg-surface px-3 py-3 sm:px-5 sm:py-3.5">
            <div className="number-display truncate text-lg font-semibold tabular-nums text-text-primary sm:text-2xl">
              {cell.value}
            </div>
            <div className="plate-label mt-1 leading-snug">{cell.label}</div>
          </div>
        ))}
      </section>

      {/* Filing wall: root rail + slot board */}
      <div className="grid items-start gap-4 lg:grid-cols-[272px_minmax(0,1fr)]">
        {/* Root rail */}
        <nav
          aria-label={t('rail.label')}
          className="min-w-0 rounded-xl border border-border bg-surface p-2 shadow-plate lg:sticky lg:top-4"
        >
          <div className="plate-label mb-2 hidden px-2 pt-1 lg:block">
            {t('rail.label')} · {rootCategories}
          </div>
          <div className="scrollbar-hide flex gap-1.5 overflow-x-auto pb-1 lg:flex-col lg:overflow-visible lg:pb-0">
            {isLoading ? (
              <div className="flex w-full gap-1.5 max-lg:flex-row lg:flex-col" aria-hidden="true">
                {[...Array(4)].map((_, i) => (
                  <LoadingSkeleton key={i} className="h-[52px] w-40 shrink-0 lg:w-full" />
                ))}
              </div>
            ) : (
              <>
                <RailButton
                  active={selectedRoot === 'all'}
                  name={t('rail.all')}
                  meta={String(totalCategories)}
                  onClick={() => setSelectedRoot('all')}
                  medallion={
                    <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary/15 text-primary">
                      <Layers size={16} />
                    </span>
                  }
                />
                {sortedTree.map(root => (
                  <RailButton
                    key={root.id}
                    active={selectedRoot === root.id}
                    name={root.name || t('unknownCategory')}
                    title={`${root.name} — ${t('transactionCount', { count: root.transactionCount || 0 })}`}
                    onClick={() => setSelectedRoot(root.id)}
                    meta={
                      root.subcategories && root.subcategories.length > 0
                        ? String(root.subcategories.length)
                        : undefined
                    }
                    medallion={
                      <span
                        className="flex h-8 w-8 items-center justify-center rounded-lg text-xs font-medium text-white ring-1 ring-inset ring-black/25"
                        style={{ backgroundColor: root.color || CATEGORY_COLOR_FALLBACK }}
                        aria-hidden="true"
                      >
                        {root.icon ? (
                          <span className="text-xs leading-none">{root.icon}</span>
                        ) : (
                          <FolderOpen size={15} />
                        )}
                      </span>
                    }
                  />
                ))}
              </>
            )}
          </div>
        </nav>

        {/* Slot board */}
        <section className="min-w-0 overflow-hidden rounded-xl border border-border bg-surface shadow-plate">
          {/* Toolbar */}
          <div className="flex flex-col gap-2 border-b border-border p-3 sm:flex-row sm:items-center">
            <div className="relative flex-1">
              <Input
                type="text"
                placeholder={t('search.placeholder')}
                value={searchQuery}
                onChange={e => setSearchQuery(e.target.value)}
                className="pl-10 pr-10"
              />
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-text-muted" />
              <RegexToggle
                enabled={searchRegex}
                onChange={setSearchRegex}
                className="absolute right-2 top-1/2 -translate-y-1/2"
              />
            </div>

            <div
              role="group"
              aria-label={t('sort.label')}
              className="flex shrink-0 items-center gap-0.5 self-start rounded-lg border border-border bg-background p-1 sm:self-auto"
            >
              {sortOptions.map(({ value, label, short, Icon }) => (
                <button
                  key={value}
                  type="button"
                  onClick={() => setSortBy(value)}
                  aria-pressed={sortBy === value}
                  title={label}
                  className={`
                    flex items-center gap-1.5 rounded-md px-2.5 py-1.5 text-xs font-medium transition-colors
                    ${
                      sortBy === value
                        ? 'bg-surface-elevated text-text-primary shadow-slot'
                        : 'text-text-secondary hover:text-text-primary'
                    }
                  `}
                >
                  <Icon size={14} aria-hidden="true" />
                  <span className="hidden xl:inline">{short}</span>
                </button>
              ))}
            </div>
          </div>

          {/* Open drawer header */}
          {!isSearching && selectedNode && (
            <div>
              <div className="flex items-center gap-3 px-4 py-3.5">
                <div
                  className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl text-xl text-white ring-1 ring-inset ring-black/25"
                  style={{ backgroundColor: selectedNode.color || CATEGORY_COLOR_FALLBACK }}
                  aria-hidden="true"
                >
                  {selectedNode.icon ? (
                    <span className="leading-none">{selectedNode.icon}</span>
                  ) : (
                    <FolderOpen size={20} />
                  )}
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex min-w-0 flex-wrap items-center gap-2">
                    <h2 className="truncate text-base font-semibold text-text-primary">
                      {selectedNode.name}
                    </h2>
                    {selectedNode.isSystem && (
                      <Badge variant="outline" className="text-xs">
                        {t('badges.system')}
                      </Badge>
                    )}
                  </div>
                  <p className="mt-0.5 flex flex-wrap items-center gap-x-1.5 text-xs text-text-secondary">
                    <span>
                      {t('transactionCount', { count: selectedNode.transactionCount || 0 })}
                    </span>
                    {selectedNode.mccCode && <span>· MCC: {selectedNode.mccCode}</span>}
                    <span aria-hidden="true">·</span>
                    <span
                      className={`number-display font-medium tabular-nums ${(selectedNode.totalAmount ?? 0) > 0 ? 'text-green-600' : (selectedNode.totalAmount ?? 0) < 0 ? 'text-red-600' : ''}`}
                    >
                      <ConvertedAmount
                        amount={selectedNode.totalAmount ?? 0}
                        currency={drawerCurrency}
                        isConverted={false}
                        secondaryAmount={drawerConversion.convert(selectedNode.totalAmount ?? 0)}
                        secondaryCurrency={drawerConversion.secondaryCurrency}
                        secondaryExchangeRate={drawerConversion.secondaryExchangeRate}
                        inline
                      />
                    </span>
                  </p>
                </div>
                <div className="flex shrink-0 items-center gap-0.5">
                  <button
                    type="button"
                    onClick={() => handleEdit(selectedNode)}
                    className="rounded-lg p-2 hover:bg-surface-elevated"
                    aria-label={t('aria.editCategory')}
                  >
                    <Edit2 size={16} className="text-text-secondary" />
                  </button>
                  {!selectedNode.isSystem && (
                    <button
                      type="button"
                      onClick={() => handleDelete(selectedNode)}
                      className="rounded-lg p-2 hover:bg-surface-elevated"
                      aria-label={t('aria.deleteCategory')}
                    >
                      <Trash2 size={16} className="text-red-500" />
                    </button>
                  )}
                </div>
              </div>
              <div className="hairline-brass mx-4" aria-hidden="true" />
            </div>
          )}

          {/* Column header — the board's fixed columns (rows are self-evident below sm) */}
          <div className="plate-label hidden items-center gap-2 px-3 py-2 sm:flex sm:gap-3">
            <div className="w-6 shrink-0" aria-hidden="true" />
            <div className="w-9 shrink-0" aria-hidden="true" />
            <div className="min-w-0 flex-1">{t('table.name')}</div>
            <div className="w-[92px] shrink-0 text-right sm:w-[120px]">{t('table.txns')}</div>
            <div className="w-[104px] shrink-0 text-right sm:w-[120px]">{t('table.amount')}</div>
            <div className="w-[76px] shrink-0" aria-hidden="true" />
          </div>

          {/* Board body */}
          <div className="px-1 pb-2">
            {isLoading ? (
              <div className="space-y-2 p-3" aria-hidden="true">
                {[...Array(6)].map((_, i) => (
                  <LoadingSkeleton key={i} className="h-16" />
                ))}
              </div>
            ) : error ? (
              <EmptyState
                icon={Tag}
                title={t('loadError.title')}
                description={t('loadError.description')}
              />
            ) : isSearching && searchResults.length === 0 ? (
              <EmptyState
                icon={FolderOpen}
                title={t('empty.noMatch')}
                description={t('empty.adjustSearch')}
              />
            ) : !isSearching && sortedTree.length === 0 ? (
              <EmptyState
                icon={FolderOpen}
                title={t('empty.noCategories')}
                description={t('empty.addFirst')}
                action={{
                  label: t('addCategory'),
                  onClick: handleCreate,
                }}
              />
            ) : (
              <div key={boardListKey} className="divide-y divide-border">
                {isSearching ? (
                  // Flat results across drawers, each carrying its parent chip
                  searchResults.map((category, i) => (
                    <TreeNode
                      key={category.id}
                      node={category}
                      index={i}
                      parentName={category.parentName}
                      onEdit={handleEdit}
                      onDelete={handleDelete}
                    />
                  ))
                ) : selectedNode ? (
                  // Open drawer: its slots read top to bottom
                  <CategoryTree
                    categories={selectedNode.subcategories ?? []}
                    onEdit={handleEdit}
                    onDelete={handleDelete}
                  />
                ) : (
                  // Full wall: every drawer in place
                  <CategoryTree
                    categories={sortedTree}
                    onEdit={handleEdit}
                    onDelete={handleDelete}
                  />
                )}
              </div>
            )}
          </div>
        </section>
      </div>

      {/* Add/Edit Form Dialog */}
      <CategoryFormDialog
        open={isFormOpen}
        onOpenChange={open => {
          setIsFormOpen(open);
          if (!open) setFormError(null);
        }}
        category={editingCategory}
        onSubmit={handleFormSubmit}
        isLoading={createCategory.isPending || updateCategory.isPending}
        error={formError}
      />

      {/* Delete Confirmation */}
      <ConfirmationDialog
        open={!!deletingCategory}
        onOpenChange={open => !open && setDeletingCategory(null)}
        title={t('dialogs.delete.title')}
        description={t('dialogs.delete.description', { name: deletingCategory?.name })}
        confirmText={t('dialogs.delete.confirmText')}
        onConfirm={handleDeleteConfirm}
        variant="danger"
      />
    </div>
  );
}
