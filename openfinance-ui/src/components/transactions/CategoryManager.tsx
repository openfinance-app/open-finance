/**
 * CategoryManager Component
 * Task 3.2.17: Create CategoryManager component
 *
 * Modal for managing income and expense categories
 */
import { useState } from 'react';
import { Plus, Trash2, FolderOpen } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Badge } from '@/components/ui/Badge';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/Dialog';
import { ConfirmationDialog } from '@/components/ConfirmationDialog';
import { useCategories, useCreateCategory, useDeleteCategory } from '@/hooks/useTransactions';
import type { Category } from '@/types/transaction';

interface CategoryManagerProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function CategoryManager({ open, onOpenChange }: CategoryManagerProps) {
  const [activeTab, setActiveTab] = useState<'INCOME' | 'EXPENSE'>('EXPENSE');
  const [newCategoryName, setNewCategoryName] = useState('');
  const [deletingCategory, setDeletingCategory] = useState<Category | null>(null);

  const { data: allCategories = [] } = useCategories();
  const createCategory = useCreateCategory();
  const deleteCategory = useDeleteCategory();

  const filteredCategories = allCategories.filter(cat => cat.type === activeTab);

  const handleCreateCategory = async () => {
    if (!newCategoryName.trim()) return;

    try {
      await createCategory.mutateAsync({
        name: newCategoryName.trim(),
        type: activeTab,
      });
      setNewCategoryName('');
    } catch (error) {
      console.error('Failed to create category:', error);
    }
  };

  const handleDeleteCategory = async () => {
    if (!deletingCategory) return;

    try {
      await deleteCategory.mutateAsync(deletingCategory.id);
      setDeletingCategory(null);
    } catch (error) {
      console.error('Failed to delete category:', error);
    }
  };

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="sm:max-w-[600px]">
          <DialogHeader>
            <DialogTitle>Manage Categories</DialogTitle>
          </DialogHeader>

          <div className="space-y-4">
            {/* Tabs */}
            <div className="flex gap-2 border-b border-border">
              <button
                onClick={() => setActiveTab('EXPENSE')}
                className={`px-4 py-2 text-sm font-medium transition-colors ${
                  activeTab === 'EXPENSE'
                    ? 'text-primary border-b-2 border-primary'
                    : 'text-text-secondary hover:text-text-primary'
                }`}
              >
                Expenses
              </button>
              <button
                onClick={() => setActiveTab('INCOME')}
                className={`px-4 py-2 text-sm font-medium transition-colors ${
                  activeTab === 'INCOME'
                    ? 'text-primary border-b-2 border-primary'
                    : 'text-text-secondary hover:text-text-primary'
                }`}
              >
                Income
              </button>
            </div>

            {/* Create new category */}
            <div className="flex gap-2">
              <Input
                placeholder={`New ${activeTab.toLowerCase()} category...`}
                value={newCategoryName}
                onChange={e => setNewCategoryName(e.target.value)}
                onKeyPress={e => {
                  if (e.key === 'Enter') {
                    handleCreateCategory();
                  }
                }}
              />
              <Button
                variant="primary"
                onClick={handleCreateCategory}
                disabled={!newCategoryName.trim() || createCategory.isPending}
              >
                <Plus className="h-4 w-4" />
              </Button>
            </div>

            {/* Category list */}
            <div className="space-y-2 max-h-[400px] overflow-y-auto">
              {filteredCategories.length === 0 && (
                <div className="text-center py-8 text-text-secondary">
                  <FolderOpen className="h-12 w-12 mx-auto mb-2 opacity-50" />
                  <p>No categories yet</p>
                  <p className="text-sm">Create your first category above</p>
                </div>
              )}

              {filteredCategories.map(category => (
                <div
                  key={category.id}
                  className="flex items-center justify-between p-3 bg-surface rounded-lg hover:bg-surface-elevated transition-colors"
                >
                  <div className="flex items-center gap-3 flex-1">
                    <span className="text-text-primary">{category.name}</span>
                    {category.parentId && (
                      <Badge variant="default" size="sm">
                        Subcategory
                      </Badge>
                    )}
                  </div>

                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => setDeletingCategory(category)}
                    className="h-8 w-8 p-0 text-error hover:text-error hover:bg-error/10"
                    aria-label="Delete category"
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>
              ))}
            </div>
          </div>

          {/* Close button */}
          <div className="flex justify-end pt-4">
            <Button variant="ghost" onClick={() => onOpenChange(false)}>
              Close
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation */}
      <ConfirmationDialog
        open={!!deletingCategory}
        onOpenChange={open => !open && setDeletingCategory(null)}
        onConfirm={handleDeleteCategory}
        title="Delete Category"
        description={`Are you sure you want to delete "${deletingCategory?.name}"? This may affect existing transactions.`}
        confirmText="Delete"
        variant="danger"
        loading={deleteCategory.isPending}
      />
    </>
  );
}
