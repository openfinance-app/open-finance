/**
 * CategoriesPage Component
 * Task CAT-2.2: Create CategoryManagementPage
 * 
 * Main page for viewing and managing transaction categories in hierarchical tree view
 */
import { useState, useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Plus, ChevronRight, ChevronDown, FolderOpen, Edit2, Trash2, Tag, Search, ArrowUpDown, ChevronDown as ChevronDownIcon, Check } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Badge } from '@/components/ui/Badge';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/Dialog';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/layout/EmptyState';
import { LoadingSkeleton } from '@/components/LoadingComponents';
import { ConfirmationDialog } from '@/components/ConfirmationDialog';
import { CategorySelect } from '@/components/ui/CategorySelect';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/DropdownMenu';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { useCategoryTree, useCreateCategory, useUpdateCategory, useDeleteCategory } from '@/hooks/useTransactions';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useSecondaryConversion } from '@/hooks/useSecondaryConversion';
import { useAuthContext } from '@/context/AuthContext';
import type { CategoryTreeNode, TransactionType } from '@/types/transaction';

/**
 * CategoryTree component - displays hierarchical category tree with expand/collapse
 */
interface CategoryTreeProps {
  categories: CategoryTreeNode[];
  onEdit: (category: CategoryTreeNode) => void;
  onDelete: (category: CategoryTreeNode) => void;
}

function CategoryTree({ categories, onEdit, onDelete }: CategoryTreeProps) {
  return (
    <div className="space-y-2">
      {categories.map((category) => (
        <TreeNode
          key={category.id}
          node={category}
          onEdit={onEdit}
          onDelete={onDelete}
        />
      ))}
    </div>
  );
}

/**
 * TreeNode component - individual category node with expand/collapse
 */
interface TreeNodeProps {
  node: CategoryTreeNode;
  depth?: number;
  onEdit: (category: CategoryTreeNode) => void;
  onDelete: (category: CategoryTreeNode) => void;
}

function TreeNode({ node, depth = 0, onEdit, onDelete }: TreeNodeProps) {
  const { t } = useTranslation('categories');
  const [isExpanded, setIsExpanded] = useState(depth < 1); // Expand first level by default
  const hasChildren = Array.isArray(node?.subcategories) && node.subcategories.length > 0;
  const { baseCurrency } = useAuthContext();
  const { convert, secondaryCurrency: secCurrency, secondaryExchangeRate } = useSecondaryConversion(baseCurrency);

  if (!node) return null;

  const typeColor = node.type === 'INCOME' ? 'bg-green-500/10 text-green-500' : 'bg-red-500/10 text-red-500';

  return (
    <div className="select-none">
      <div
        className="flex items-center gap-2 px-3 py-2 rounded-lg hover:bg-surface transition-colors group"
      >
        {/* Depth Spacer */}
        {depth > 0 && <div style={{ width: depth * 24 }} className="shrink-0" />}

        {/* Expand/Collapse Button */}
        <div className="w-6 shrink-0 flex justify-center">
          <button
            type="button"
            onClick={() => setIsExpanded(!isExpanded)}
            className={`
              p-0.5 rounded hover:bg-surface-elevated
              ${!hasChildren && 'invisible'}
            `}
            aria-label={isExpanded ? t('badges.collapse') : t('badges.expand')}          >
            {isExpanded ? (
              <ChevronDown size={16} className="text-text-secondary" />
            ) : (
              <ChevronRight size={16} className="text-text-secondary" />
            )}
          </button>
        </div>

        {/* Icon */}
        <div
          className="w-8 h-8 shrink-0 rounded-lg flex items-center justify-center text-white text-sm font-medium"
          style={{ backgroundColor: node.color || '#6B7280' }}
        >
          {node.icon ? (
            <span className="text-xs">{node.icon}</span>
          ) : (
            <FolderOpen size={16} />
          )}
        </div>

        {/* Name */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <span className="font-medium text-text-primary truncate">{node.name || 'Unknown Category'}</span>
            {node.isSystem && (
              <Badge variant="outline" className="text-xs">{t('badges.system')}</Badge>
            )}
          </div>
          {node.mccCode && (
            <span className="text-xs text-text-secondary">MCC: {node.mccCode}</span>
          )}
        </div>

        {/* Type Badge */}
        <div className="w-[80px] shrink-0">
          <Badge className={typeColor}>
            {node.type === 'INCOME' ? t('badges.income') : t('badges.expense')}
          </Badge>
        </div>

        {/* Transaction Count */}
        <div className="w-[70px] shrink-0 text-sm text-text-secondary text-right">
          {node.transactionCount || 0} {t('table.txns')}
        </div>

        {/* Total Amount */}
        <div className={`w-[110px] shrink-0 text-sm text-right font-medium ${node.type === 'INCOME' ? 'text-green-600' : 'text-red-600'}`}>
          <ConvertedAmount
            amount={Math.abs(node.totalAmount ?? 0)}
            currency={baseCurrency}
            isConverted={false}
            secondaryAmount={convert(Math.abs(node.totalAmount ?? 0))}
            secondaryCurrency={secCurrency}
            secondaryExchangeRate={secondaryExchangeRate}
            inline
          />
        </div>

        {/* Actions */}
        <div className="w-[80px] shrink-0 flex items-center justify-end gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
          <button
            type="button"
            onClick={() => onEdit(node)}
            className="p-2 rounded-lg hover:bg-surface-elevated"
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
        <div className="border-l border-border ml-4">
          {node.subcategories.map((child) => (
            <TreeNode
              key={child.id}
              node={child}
              depth={depth + 1}
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
  type: TransactionType;
  parentId?: number;
  icon?: string;
  color?: string;
}

const COLORS = [
  '#EF4444', '#F97316', '#F59E0B', '#84CC16', '#22C55E',
  '#14B8A6', '#06B6D4', '#3B82F6', '#6366F1', '#8B5CF6',
  '#A855F7', '#EC4899', '#F43F5E', '#6B7280', '#10B981'
];

const ICONS = [
  '🍔', '🚗', '🏠', '💊', '🛒', '📺', '✈️', '💰', '🎁', '💳',
  '🏥', '📱', '🎮', '👕', '🏋️', '📚', '🎵', '☕', '🚿', '💼'
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
    type: 'EXPENSE',
    parentId: undefined,
    icon: '📁',
    color: '#6B7280',
  });

  // Bug #4 fix: reset form whenever the dialog opens (watch both `open` and `category`)
  useEffect(() => {
    if (!open) return;
    if (category) {
      setFormData({
        name: category.name,
        type: category.type,
        parentId: category.parentId || undefined,
        icon: category.icon || '📁',
        color: category.color || '#6B7280',
      });
    } else {
      setFormData({
        name: '',
        type: 'EXPENSE',
        parentId: undefined,
        icon: '📁',
        color: '#6B7280',
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
              <label className="text-sm font-medium text-text-primary">{t('form.name')}</label>
              {/* Bug #6 fix: character counter */}
              <span className={`text-xs ${formData.name.length > 90 ? 'text-red-500' : 'text-text-secondary'}`}>
                {formData.name.length}/100
              </span>
            </div>
            <Input
              value={formData.name}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              placeholder={t('form.name')}
              maxLength={100}
              required
            />
          </div>

          {/* Type */}
          <div className="space-y-2">
            <label className="text-sm font-medium text-text-primary">{t('form.type')}</label>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => setFormData({ ...formData, type: 'EXPENSE', parentId: undefined })}
                className={`
                  flex-1 py-2 px-4 rounded-lg border transition-colors
                  ${formData.type === 'EXPENSE'
                    ? 'bg-red-500/10 border-red-500 text-red-600'
                    : 'border-border text-text-secondary hover:bg-surface'}
                `}
              >
                {t('form.expense')}
              </button>
              <button
                type="button"
                onClick={() => setFormData({ ...formData, type: 'INCOME', parentId: undefined })}
                className={`
                  flex-1 py-2 px-4 rounded-lg border transition-colors
                  ${formData.type === 'INCOME'
                    ? 'bg-green-500/10 border-green-500 text-green-600'
                    : 'border-border text-text-secondary hover:bg-surface'}
                `}
              >
                {t('form.income')}
              </button>
            </div>
          </div>

          {/* Parent Category */}
          <div className="space-y-2">
            <label className="text-sm font-medium text-text-primary">{t('form.parentCategory')}</label>
            <CategorySelect
              value={formData.parentId}
              onValueChange={(value) => setFormData({ ...formData, parentId: value })}
              placeholder={t('form.selectParentCategory')}
              type={formData.type}
              allowNone={true}
            />
          </div>

          {/* Color */}
          <div className="space-y-2">
            <label className="text-sm font-medium text-text-primary">{t('form.color')}</label>
            <div className="flex flex-wrap gap-2">
              {COLORS.map((color) => (
                <button
                  key={color}
                  type="button"
                  onClick={() => setFormData({ ...formData, color })}
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
              {ICONS.map((icon) => (
                <button
                  key={icon}
                  type="button"
                  onClick={() => setFormData({ ...formData, icon })}
                  className={`
                    w-10 h-10 rounded-lg border flex items-center justify-center text-lg
                    transition-colors
                    ${formData.icon === icon
                      ? 'border-primary bg-primary/10'
                      : 'border-border hover:bg-surface'}
                  `}
                >
                  {icon}
                </button>
              ))}
            </div>
          </div>

          {/* Actions */}
          <div className="flex justify-end gap-3 pt-4">
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
            >
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
 * Main CategoriesPage component
 */
type SortOption = 'name' | 'transactions' | 'amount';

export default function CategoriesPage() {
  const { t } = useTranslation('categories');
  useDocumentTitle(t('title'));

  const [activeTab, setActiveTab] = useState<'ALL' | 'EXPENSE' | 'INCOME'>('ALL');
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingCategory, setEditingCategory] = useState<CategoryTreeNode | null>(null);
  const [deletingCategory, setDeletingCategory] = useState<CategoryTreeNode | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [sortBy, setSortBy] = useState<SortOption>('name');
  const [formError, setFormError] = useState<string | null>(null);

  const { data: categories = [], isLoading, error } = useCategoryTree();
  const createCategory = useCreateCategory();
  const updateCategory = useUpdateCategory();
  const deleteCategory = useDeleteCategory();

  // Flatten categories for searching and sorting
  const flattenCategories = (cats: CategoryTreeNode[]): CategoryTreeNode[] => {
    const result: CategoryTreeNode[] = [];
    const traverse = (nodes: CategoryTreeNode[]) => {
      for (const node of nodes) {
        // Clone node without subcategories to prevent duplicate rendering when flattened
        const flatNode = { ...node, subcategories: [] };
        result.push(flatNode);
        if (node.subcategories && node.subcategories.length > 0) {
          traverse(node.subcategories);
        }
      }
    };
    traverse(cats);
    return result;
  };

  const filteredCategories = useMemo(() => {
    // 1. Filter by active tab (keep tree structure)
    const filterTree = (nodes: CategoryTreeNode[]): CategoryTreeNode[] => {
      if (activeTab === 'ALL') return nodes;
      return nodes.filter(c => c.type === activeTab).map(c => ({
        ...c,
        subcategories: c.subcategories ? filterTree(c.subcategories) : []
      }));
    };

    const result = filterTree(categories);

    // 2. Search and Sort
    const searchLower = searchQuery.toLowerCase().trim();
    if (searchLower) {
      // If searching, flatten the tree, filter by name, then sort the flat list
      const flat = flattenCategories(result);
      const filtered = flat.filter(c => (c.name || '').toLowerCase().includes(searchLower));

      return filtered.sort((a, b) => {
        switch (sortBy) {
          case 'transactions': return (b.transactionCount || 0) - (a.transactionCount || 0);
          case 'amount': return (b.totalAmount || 0) - (a.totalAmount || 0);
          case 'name': default: return (a.name || '').localeCompare(b.name || '');
        }
      });
    } else {
      // If not searching, sort the tree recursively
      const sortTree = (nodes: CategoryTreeNode[]): CategoryTreeNode[] => {
        const sorted = [...nodes].sort((a, b) => {
          switch (sortBy) {
            case 'transactions': return (b.transactionCount || 0) - (a.transactionCount || 0);
            case 'amount': return (b.totalAmount || 0) - (a.totalAmount || 0);
            case 'name': default: return (a.name || '').localeCompare(b.name || '');
          }
        });
        return sorted.map(node => ({
          ...node,
          subcategories: node.subcategories ? sortTree(node.subcategories) : []
        }));
      };
      return sortTree(result);
    }
  }, [categories, activeTab, searchQuery, sortBy]);

  const displayCategories = filteredCategories;

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
            type: data.type,
            parentId: data.parentId,
            icon: data.icon,
            color: data.color,
          },
        });
      } else {
        await createCategory.mutateAsync({
          name: data.name,
          type: data.type,
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

  // Calculate totals including subcategories
  const flatAllCategories = useMemo(() => flattenCategories(categories), [categories]);
  const totalCategories = flatAllCategories.length;

  const totalIncome = flatAllCategories
    .filter(c => c.type === 'INCOME')
    .reduce((sum, c) => sum + (c.transactionCount || 0), 0);
  const totalExpense = flatAllCategories
    .filter(c => c.type === 'EXPENSE')
    .reduce((sum, c) => sum + (c.transactionCount || 0), 0);

  return (
    <div className="space-y-6">
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

      {/* Stats */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="bg-surface rounded-xl p-4 border border-border">
          <div className="text-2xl font-bold text-text-primary">{totalCategories}</div>
          <div className="text-sm text-text-secondary">{t('summary.totalCategories')}</div>
        </div>
        <div className="bg-surface rounded-xl p-4 border border-border">
          <div className="text-2xl font-bold text-green-600">{totalIncome}</div>
          <div className="text-sm text-text-secondary">{t('summary.incomeTransactions')}</div>
        </div>
        <div className="bg-surface rounded-xl p-4 border border-border">
          <div className="text-2xl font-bold text-red-600">{totalExpense}</div>
          <div className="text-sm text-text-secondary">{t('summary.expenseTransactions')}</div>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-2 border-b border-border">
        <button
          onClick={() => setActiveTab('ALL')}
          className={`px-4 py-2 text-sm font-medium transition-colors ${activeTab === 'ALL'
            ? 'text-primary border-b-2 border-primary'
            : 'text-text-secondary hover:text-text-primary'
            }`}
        >
          {t('tabs.all')}
        </button>
        <button
          onClick={() => setActiveTab('EXPENSE')}
          className={`px-4 py-2 text-sm font-medium transition-colors ${activeTab === 'EXPENSE'
            ? 'text-red-600 border-b-2 border-red-500'
            : 'text-text-secondary hover:text-text-primary'
            }`}
        >
          {t('tabs.expenses')}
        </button>
        <button
          onClick={() => setActiveTab('INCOME')}
          className={`px-4 py-2 text-sm font-medium transition-colors ${activeTab === 'INCOME'
            ? 'text-green-600 border-b-2 border-green-500'
            : 'text-text-secondary hover:text-text-primary'
            }`}
        >
          {t('tabs.income')}
        </button>
      </div>

      {/* Search and Sort Bar */}
      <div className="flex gap-4">
        <div className="relative flex-1">
          <Input
            type="text"
            placeholder={t('search.placeholder')}
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="pl-10"
          />
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-text-muted" />
        </div>

        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" className="gap-2">
              <ArrowUpDown className="h-4 w-4" />
              <span>{sortBy === 'name' ? t('sort.byName') : sortBy === 'transactions' ? t('sort.byTransactions') : t('sort.byAmount')}</span>
              <ChevronDownIcon className="h-4 w-4 opacity-50" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-[180px]">
            <DropdownMenuItem onClick={() => setSortBy('name')} className="flex items-center justify-between">
              {t('table.name')}
              {sortBy === 'name' && <Check className="h-4 w-4" />}
            </DropdownMenuItem>
            <DropdownMenuItem onClick={() => setSortBy('transactions')} className="flex items-center justify-between">
              {t('table.transactionCount')}
              {sortBy === 'transactions' && <Check className="h-4 w-4" />}
            </DropdownMenuItem>
            <DropdownMenuItem onClick={() => setSortBy('amount')} className="flex items-center justify-between">
              {t('table.totalAmount')}
              {sortBy === 'amount' && <Check className="h-4 w-4" />}
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      {/* Category Tree Header */}
      <div className="bg-surface rounded-xl border border-border">
        {/* Header Row */}
        <div className="flex items-center gap-2 px-3 py-2 border-b border-border text-sm font-medium text-text-secondary">
          <div className="w-6 shrink-0"></div>
          <div className="w-8 shrink-0"></div>
          <div className="flex-1">{t('table.name')}</div>
          <div className="w-[80px] shrink-0">{t('table.type')}</div>
          <div className="w-[70px] shrink-0 text-right">{t('table.txns')}</div>
          <div className="w-[110px] shrink-0 text-right">{t('table.amount')}</div>
          <div className="w-[80px] shrink-0"></div>
        </div>

        {/* Category Tree */}
        {isLoading ? (
          <LoadingSkeleton className="h-64" />
        ) : error ? (
          <EmptyState
            icon={Tag}
            title={t('loadError.title')}
            description={t('loadError.description')}
          />
        ) : displayCategories.length === 0 ? (
          <EmptyState
            icon={FolderOpen}
            title={searchQuery ? t('empty.noMatch') : t('empty.noCategories')}
            description={searchQuery ? t('empty.adjustSearch') : t('empty.addFirst')}
            action={!searchQuery ? {
              label: t('addCategory'),
              onClick: handleCreate,
            } : undefined}
          />
        ) : (
          <div className="divide-y divide-border">
            {searchQuery ? (
              // Flat list when searching
              displayCategories.map((category) => (
                <TreeNode
                  key={category.id}
                  node={category}
                  onEdit={handleEdit}
                  onDelete={handleDelete}
                />
              ))
            ) : (
              // Tree structure when not searching
              <CategoryTree
                categories={filteredCategories}
                onEdit={handleEdit}
                onDelete={handleDelete}
              />
            )}
          </div>
        )}
      </div>

      {/* Add/Edit Form Dialog */}
      <CategoryFormDialog
        open={isFormOpen}
        onOpenChange={(open) => {
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
        onOpenChange={(open) => !open && setDeletingCategory(null)}
        title={t('dialogs.delete.title')}
        description={t('dialogs.delete.description', { name: deletingCategory?.name })}
        confirmText={t('dialogs.delete.confirmText')}
        onConfirm={handleDeleteConfirm}
        variant="danger"
      />
    </div>
  );
}
