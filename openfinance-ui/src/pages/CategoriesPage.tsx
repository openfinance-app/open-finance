/**
 * Categories use a single ledger: subtree totals lead, with direct parent
 * activity on a labeled secondary line. Numeric columns stay aligned at
 * every depth; narrow screens stack identity above activity.
 */
import { useState, useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Plus,
  ChevronRight,
  ChevronDown,
  ChevronsDownUp,
  ChevronsUpDown,
  FolderOpen,
  Edit2,
  Trash2,
  Tag,
  Search,
  MoreHorizontal,
} from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Badge } from '@/components/ui/Badge';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/DropdownMenu';
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
import {
  CategoryAmount,
  CategoryTransactionCount,
} from '@/components/transactions/CategoryActivity';
import { RegexToggle } from '@/components/ui/RegexToggle';
import { matchesQuery } from '@/utils/searchMatch';
import type { CategoryTreeNode } from '@/types/transaction';

/** A matching branch with its parent's name attached for search-result breadcrumbs. */
interface CategorySearchNode extends CategoryTreeNode {
  parentName?: string;
}

interface CategoryTreeProps {
  categories: CategorySearchNode[];
  expandAllByDefault?: boolean;
  onEdit: (category: CategoryTreeNode) => void;
  onDelete: (category: CategoryTreeNode) => void;
}

function CategoryTree({
  categories,
  expandAllByDefault = false,
  onEdit,
  onDelete,
}: CategoryTreeProps) {
  const { t } = useTranslation('categories');
  const [expandedNodes, setExpandedNodes] = useState<Map<number, boolean>>(() => new Map());
  const expandableNodes = useMemo(() => {
    const result: { id: number; depth: number }[] = [];
    const visit = (nodes: CategoryTreeNode[], depth: number): void => {
      for (const node of nodes) {
        if (node.subcategories?.length) {
          result.push({ id: node.id, depth });
          visit(node.subcategories, depth + 1);
        }
      }
    };
    visit(categories, 0);
    return result;
  }, [categories]);
  const allExpanded = expandableNodes.every(
    node => expandedNodes.get(node.id) ?? expandAllByDefault
  );
  const toggleNode = (id: number, expanded: boolean): void => {
    setExpandedNodes(current => new Map(current).set(id, expanded));
  };

  return (
    <div className="pb-2">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 px-3 py-2 lg:px-5">
        <p className="min-w-0 flex-1 basis-64 text-xs leading-relaxed text-text-secondary">
          {t('list.amountHint')}
        </p>
        {expandableNodes.length > 0 && (
          <Button
            variant="ghost"
            className="ml-auto h-11 shrink-0 gap-2 text-text-secondary lg:h-8"
            aria-expanded={allExpanded}
            aria-controls="category-tree"
            onClick={() =>
              setExpandedNodes(new Map(expandableNodes.map(node => [node.id, !allExpanded])))
            }
          >
            {allExpanded ? (
              <ChevronsDownUp size={16} aria-hidden="true" />
            ) : (
              <ChevronsUpDown size={16} aria-hidden="true" />
            )}
            {t(allExpanded ? 'list.collapseAll' : 'list.expandAll')}
          </Button>
        )}
      </div>
      <div
        aria-hidden="true"
        className="hidden grid-cols-[minmax(0,1fr)_8rem_11rem_2.75rem] gap-x-5 border-b border-border px-5 pb-2 text-xs font-medium text-text-secondary lg:grid"
      >
        <span className="pl-19">{t('table.name')}</span>
        <span className="text-right">{t('summary.totalTransactions')}</span>
        <span className="text-right">{t('table.amount')}</span>
        <span />
      </div>
      <ul id="category-tree" className="divide-y divide-border">
        {categories.map(category => (
          <TreeNode
            key={category.id}
            node={category}
            parentName={category.parentName}
            expandedNodes={expandedNodes}
            expandAllByDefault={expandAllByDefault}
            onToggle={toggleNode}
            onEdit={onEdit}
            onDelete={onDelete}
          />
        ))}
      </ul>
    </div>
  );
}

interface TreeNodeProps {
  node: CategoryTreeNode;
  depth?: number;
  parentName?: string;
  expandedNodes: ReadonlyMap<number, boolean>;
  expandAllByDefault: boolean;
  onToggle: (id: number, expanded: boolean) => void;
  onEdit: (category: CategoryTreeNode) => void;
  onDelete: (category: CategoryTreeNode) => void;
}

function TreeNode({
  node,
  depth = 0,
  parentName,
  expandedNodes,
  expandAllByDefault,
  onToggle,
  onEdit,
  onDelete,
}: TreeNodeProps) {
  const { t } = useTranslation('categories');
  const isExpanded = expandedNodes.get(node.id) ?? expandAllByDefault;
  const canExpand = node.subcategories?.length > 0;
  const hasChildren = canExpand || node.hasSubcategories;
  // A zero net amount can still represent real transactions, so use the count.
  const hasDirectActivity =
    hasChildren && (node.directTransactionCount ?? node.transactionCount ?? 0) > 0;

  return (
    <li>
      <div className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-3 px-3 py-2 hover:bg-surface-elevated/60 lg:grid-cols-[minmax(0,1fr)_8rem_11rem_2.75rem] lg:gap-x-5 lg:px-5">
        <div
          className="col-span-2 col-start-1 row-start-1 flex min-w-0 items-center gap-2 pr-11 lg:col-span-1 lg:pr-0"
          style={{ paddingInlineStart: Math.min(depth, 4) * 12 }}
        >
          {canExpand ? (
            <button
              type="button"
              onClick={() => onToggle(node.id, !isExpanded)}
              className="flex h-11 w-11 shrink-0 items-center justify-center rounded-md text-text-secondary hover:bg-surface-elevated focus-visible:outline-2 focus-visible:outline-primary lg:h-8 lg:w-7"
              aria-label={`${t(isExpanded ? 'badges.collapse' : 'badges.expand')} ${node.name}`}
              aria-expanded={isExpanded}
            >
              {isExpanded ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
            </button>
          ) : (
            <span className="w-11 shrink-0 lg:w-7" aria-hidden="true" />
          )}
          <span
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-sm text-white"
            style={{ backgroundColor: node.color || CATEGORY_COLOR_FALLBACK }}
            aria-hidden="true"
          >
            {node.icon || <FolderOpen size={16} />}
          </span>
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
              <span
                className={`break-words text-sm text-text-primary ${hasChildren ? 'font-semibold' : 'font-medium'}`}
              >
                {node.name || t('unknownCategory')}
              </span>
              {node.isSystem && (
                <Badge variant="outline" className="text-xs">
                  {t('badges.system')}
                </Badge>
              )}
            </div>
            {(parentName || node.mccCode) && (
              <p className="mt-0.5 break-words text-xs text-text-secondary">
                {parentName}
                {parentName && node.mccCode && ' · '}
                {node.mccCode && `MCC: ${node.mccCode}`}
              </p>
            )}
          </div>
        </div>

        <div className="col-start-1 row-start-2 text-xs text-text-secondary tabular-nums lg:col-start-2 lg:row-start-1 lg:text-right lg:text-sm">
          <CategoryTransactionCount node={node} />
        </div>
        <div className="col-start-2 row-start-2 text-right text-sm font-semibold lg:col-start-3 lg:row-start-1">
          <CategoryAmount node={node} />
        </div>
        <div className="col-start-2 row-start-1 justify-self-end lg:col-start-4">
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button
                variant="ghost"
                className="h-11 w-11 p-0 text-text-secondary lg:h-8 lg:w-8"
                aria-label={t('aria.categoryActions', { name: node.name })}
              >
                <MoreHorizontal size={18} />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="shadow-plate-lift">
              <DropdownMenuItem onSelect={() => onEdit(node)} className="min-h-11 gap-2 lg:min-h-8">
                <Edit2 size={15} />
                {t('aria.editCategory')}
              </DropdownMenuItem>
              {!node.isSystem && (
                <DropdownMenuItem
                  onSelect={() => onDelete(node)}
                  className="min-h-11 gap-2 text-error focus:text-error lg:min-h-8"
                >
                  <Trash2 size={15} />
                  {t('aria.deleteCategory')}
                </DropdownMenuItem>
              )}
            </DropdownMenuContent>
          </DropdownMenu>
        </div>

        {hasDirectActivity && (
          <div className="col-span-full grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-3 text-xs text-text-secondary lg:grid-cols-subgrid lg:gap-x-5">
            <div className="flex flex-wrap items-center gap-x-2 lg:contents">
              <span className="lg:pl-19">{t('list.directActivity')}</span>
              <span className="tabular-nums lg:text-right">
                <CategoryTransactionCount node={node} scope="direct" />
              </span>
            </div>
            <span className="text-right">
              <CategoryAmount node={node} scope="direct" />
            </span>
          </div>
        )}
      </div>
      {canExpand && isExpanded && (
        <ul>
          {node.subcategories.map(child => (
            <TreeNode
              key={child.id}
              node={child}
              depth={depth + 1}
              expandedNodes={expandedNodes}
              expandAllByDefault={expandAllByDefault}
              onToggle={onToggle}
              onEdit={onEdit}
              onDelete={onDelete}
            />
          ))}
        </ul>
      )}
    </li>
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

  // Sort each level without losing the parent-child hierarchy.
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
    const roots =
      selectedRoot === 'all' ? sortedTree : sortedTree.filter(c => c.id === selectedRoot);
    const results: CategorySearchNode[] = [];
    const findMatches = (nodes: CategoryTreeNode[], parentName?: string): void => {
      for (const node of nodes) {
        if (matchesQuery(node.name, searchTrimmed, searchRegex)) {
          // Keep the full matching branch; its descendants must not appear again as results.
          results.push({ ...node, parentName });
        } else {
          findMatches(node.subcategories || [], node.name);
        }
      }
    };
    findMatches(roots);
    return results.sort(compareNodes);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sortedTree, searchTrimmed, searchRegex, sortBy, selectedRoot]);

  const selectedNode =
    selectedRoot === 'all' ? null : (sortedTree.find(c => c.id === selectedRoot) ?? null);

  // Clear a group filter when its category is deleted.
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

  // Summary counts cover the complete category tree.
  const totalCategories = useMemo(() => {
    const count = (nodes: CategoryTreeNode[]): number =>
      nodes.reduce((total, node) => total + 1 + count(node.subcategories || []), 0);
    return count(categories);
  }, [categories]);
  const rootCategories = categories.length;
  const subcategories = totalCategories - rootCategories;
  // Root counts already include descendants; summing every level would double-count.
  const totalTransactions = useMemo(
    () => categories.reduce((sum, c) => sum + (c.transactionCount || 0), 0),
    [categories]
  );

  const sortOptions: { value: SortOption; label: string }[] = [
    { value: 'name', label: t('table.name') },
    { value: 'transactions', label: t('summary.totalTransactions') },
    { value: 'amount', label: t('table.amount') },
  ];

  const isSearching = searchTrimmed.length > 0;
  // A new search reveals all matching descendants, even after manually folding a prior result.
  const boardListKey = `${selectedRoot}-${searchRegex}-${searchTrimmed}`;

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

      {!isLoading && !error && (
        <dl
          aria-label={t('summary.label')}
          className="flex flex-wrap gap-x-6 gap-y-2 text-xs text-text-secondary"
        >
          {[
            { value: totalCategories, label: t('summary.totalCategories') },
            { value: rootCategories, label: t('summary.rootCategories') },
            { value: subcategories, label: t('summary.subcategories') },
            { value: totalTransactions, label: t('summary.totalTransactions') },
          ].map(cell => (
            <div key={cell.label} className="flex items-baseline gap-1.5">
              <dt>{cell.label}</dt>
              <dd className="order-first font-semibold tabular-nums text-text-primary">
                {cell.value.toLocaleString()}
              </dd>
            </div>
          ))}
        </dl>
      )}

      <section
        aria-label={t('list.label')}
        className="min-w-0 rounded-xl border border-border bg-surface shadow-plate"
      >
        <div className="grid gap-3 border-b border-border p-3 lg:grid-cols-[minmax(0,1fr)_minmax(10rem,14rem)_11rem] lg:items-end lg:p-4">
          <div className="relative">
            <label htmlFor="category-search" className="sr-only">
              {t('search.placeholder')}
            </label>
            <Input
              id="category-search"
              type="text"
              placeholder={t('search.placeholder')}
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              className="h-11 pl-10 pr-10"
            />
            <Search
              className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted"
              aria-hidden="true"
            />
            <RegexToggle
              enabled={searchRegex}
              onChange={setSearchRegex}
              className="absolute right-2 top-1/2 -translate-y-1/2"
            />
          </div>
          <div className="grid grid-cols-[minmax(0,1.8fr)_minmax(0,1fr)] gap-3 lg:contents">
            <label className="min-w-0 text-xs text-text-secondary">
              <span className="mb-1.5 block">{t('list.group')}</span>
              <select
                value={selectedRoot}
                onChange={e =>
                  setSelectedRoot(e.target.value === 'all' ? 'all' : Number(e.target.value))
                }
                className="h-11 w-full min-w-0 rounded-lg border border-border bg-background px-2 text-sm text-text-primary focus-visible:outline-2 focus-visible:outline-primary lg:px-3"
              >
                <option value="all">{t('list.allGroups')}</option>
                {sortedTree.map(root => (
                  <option key={root.id} value={root.id}>
                    {root.name || t('unknownCategory')}
                  </option>
                ))}
              </select>
            </label>
            <label className="min-w-0 text-xs text-text-secondary">
              <span className="mb-1.5 block">{t('sort.label')}</span>
              <select
                value={sortBy}
                onChange={e => setSortBy(e.target.value as SortOption)}
                className="h-11 w-full rounded-lg border border-border bg-background px-2 text-sm text-text-primary focus-visible:outline-2 focus-visible:outline-primary lg:px-3"
              >
                {sortOptions.map(option => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
          </div>
        </div>

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
        ) : sortedTree.length === 0 ? (
          <EmptyState
            icon={FolderOpen}
            title={t('empty.noCategories')}
            description={t('empty.addFirst')}
            action={{ label: t('addCategory'), onClick: handleCreate }}
          />
        ) : (
          <CategoryTree
            key={boardListKey}
            categories={isSearching ? searchResults : selectedNode ? [selectedNode] : sortedTree}
            expandAllByDefault={isSearching}
            onEdit={handleEdit}
            onDelete={handleDelete}
          />
        )}
      </section>

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
