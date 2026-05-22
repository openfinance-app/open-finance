/**
 * LiabilitiesPage Component
 * Task 6.2.1: Create LiabilitiesPage component
 *
 * Main page for managing user liabilities (loans, mortgages, credit cards, etc.)
 * Requirement 2.1: Opens a unified LiabilityDetailDialog (Overview / Amortization / Linked Payments)
 * when the user clicks "View Details" on any liability card.
 */
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router';
import { Plus, Filter } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/Dialog';
import { Pagination } from '@/components/ui/Pagination';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/layout/EmptyState';
import { LoadingSkeleton } from '@/components/LoadingComponents';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import {
  useLiabilitiesPaged,
  useCreateLiability,
  useUpdateLiability,
  useDeleteLiability,
} from '@/hooks/useLiabilities';
import type { Liability, LiabilityRequest, LiabilityFilters as Filters } from '@/types/liability';

// Components
import { LiabilityForm } from '@/components/liabilities/LiabilityForm';
import { LiabilityList } from '@/components/liabilities/LiabilityList';
import { LiabilityFilters } from '@/components/liabilities/LiabilityFilters';
import { LiabilitySummaryCards } from '@/components/liabilities/LiabilitySummaryCards';
import { LiabilityDetailDialog } from '@/components/liabilities/LiabilityDetailDialog';

export default function LiabilitiesPage() {
  const { t } = useTranslation('liabilities');
  useDocumentTitle(t('title'));
  const [searchParams] = useSearchParams();
  const highlightId = searchParams.get('highlight') ? parseInt(searchParams.get('highlight')!) : null;

  const [showFilters, setShowFilters] = useState(false);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingLiability, setEditingLiability] = useState<Liability | null>(null);
  /** Requirement 2.1: Single state for unified details dialog */
  const [viewingDetailLiability, setViewingDetailLiability] = useState<Liability | null>(null);
  const [filters, setFilters] = useState<Filters>({ page: 0, size: 20, sort: 'createdAt,desc' });

  const { data: liabilitiesPage, isLoading, error } = useLiabilitiesPaged(filters);
  const createLiability = useCreateLiability();
  const updateLiability = useUpdateLiability();
  const deleteLiability = useDeleteLiability();

  // Fetch all liabilities for summary cards (unfiltered)
  const { data: allLiabilities } = useLiabilitiesPaged({ page: 0, size: 1000 });

  const liabilities = liabilitiesPage?.content || [];
  const hasLiabilities = (allLiabilities?.content?.length ?? 0) > 0;

  const handleCreate = () => {
    setEditingLiability(null);
    setIsFormOpen(true);
  };

  const handleEdit = (liability: Liability) => {
    setEditingLiability(liability);
    setIsFormOpen(true);
  };

  const handleDelete = async (liabilityId: number) => {
    try {
      await deleteLiability.mutateAsync(liabilityId);
    } catch (err) {
      console.error('Failed to delete liability:', err);
    }
  };

  /** Requirement 2.1: Open the unified details dialog for the selected liability */
  const handleViewDetails = (liability: Liability) => {
    setViewingDetailLiability(liability);
  };

  const handleFormSubmit = async (data: LiabilityRequest) => {
    try {
      if (editingLiability) {
        await updateLiability.mutateAsync({ id: editingLiability.id, data });
      } else {
        await createLiability.mutateAsync(data);
      }
      setIsFormOpen(false);
      setEditingLiability(null);
    } catch (err) {
      console.error('Failed to save liability:', err);
    }
  };

  const handleFormCancel = () => {
    setIsFormOpen(false);
    setEditingLiability(null);
  };

  const handleFiltersChange = (newFilters: Filters) => {
    setFilters({ ...newFilters, page: 0, size: filters.size || 20 });
  };

  const handlePageChange = (page: number) => {
    setFilters({ ...filters, page });
  };

  const handlePageSizeChange = (size: number) => {
    setFilters({ ...filters, page: 0, size });
  };

  if (error) {
    return (
      <div className="p-8">
        <PageHeader title={t('title')} />
        <div className="mt-6 p-4 bg-error/10 border border-error/20 rounded-lg text-error">
          {t('loadError')}
        </div>
      </div>
    );
  }

  return (
    <div className="p-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <PageHeader
          title={t('title')}
          description={t('description')}
        />
        <div className="flex gap-2 shrink-0">
          <Button variant="ghost" onClick={() => setShowFilters(!showFilters)}>
            <Filter className="h-4 w-4 mr-2" />
            {t('filters')}
          </Button>
          <Button variant="primary" onClick={handleCreate}>
            <Plus className="h-4 w-4 mr-2" />
            {t('addLiability')}
          </Button>
        </div>
      </div>

      {/* Filters */}
      {showFilters && (
        <div className="mb-6">
          <LiabilityFilters
            filters={filters}
            onFiltersChange={handleFiltersChange}
          />
        </div>
      )}

      {/* Summary Cards - Show when there are liabilities */}
      {!isLoading && hasLiabilities && (
        <div className="mb-8">
          <LiabilitySummaryCards
            liabilities={allLiabilities?.content || []}
            filteredLiabilities={liabilities}
            isActiveFilter={!!(filters.type || filters.search)}
          />
        </div>
      )}

      {/* Loading State */}
      {isLoading && (
        <div className="space-y-4">
          {[...Array(5)].map((_, i) => (
            <LoadingSkeleton key={i} className="h-24" />
          ))}
        </div>
      )}

      {/* Empty State */}
      {!isLoading && liabilities.length === 0 && !filters.type && !filters.search && (
        <EmptyState
          title={t('empty.noLiabilities')}
          description={t('empty.addFirst')}
          action={{
            label: t('addLiability'),
            onClick: handleCreate,
          }}
        />
      )}

      {/* No Results for Filter */}
      {!isLoading && liabilities.length === 0 && (filters.type || filters.search) && (
        <EmptyState
          title={t('empty.noResults')}
          description={t('empty.noMatch')}
          action={{
            label: t('empty.clearFilters'),
            onClick: () => setFilters({ page: 0, size: filters.size || 20, sort: filters.sort || 'createdAt,desc' }),
          }}
        />
      )}

      {/* Liabilities List */}
      {!isLoading && liabilities.length > 0 && (
        <>
          <LiabilityList
            liabilities={liabilities}
            onEdit={handleEdit}
            onDelete={handleDelete}
            onViewDetails={handleViewDetails}
            highlightedId={highlightId}
          />

          {/* Pagination Controls */}
          {liabilitiesPage && liabilitiesPage.totalPages > 1 && (
            <Pagination
              currentPage={liabilitiesPage.number}
              totalPages={liabilitiesPage.totalPages}
              pageSize={liabilitiesPage.size}
              totalElements={liabilitiesPage.totalElements}
              onPageChange={handlePageChange}
              onPageSizeChange={handlePageSizeChange}
            />
          )}
        </>
      )}

      {/* Create/Edit Dialog */}
      <Dialog open={isFormOpen} onOpenChange={setIsFormOpen}>
        <DialogContent className="sm:max-w-[700px] max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>
              {editingLiability ? t('dialogs.editTitle') : t('dialogs.createTitle')}
            </DialogTitle>
          </DialogHeader>
          <LiabilityForm
            liability={editingLiability || undefined}
            onSubmit={handleFormSubmit}
            onCancel={handleFormCancel}
            isLoading={createLiability.isPending || updateLiability.isPending}
          />
        </DialogContent>
      </Dialog>

      {/* Requirement 2.1: Unified Liability Details Dialog (Overview / Amortization / Linked Payments) */}
      <LiabilityDetailDialog
        liability={viewingDetailLiability}
        onClose={() => setViewingDetailLiability(null)}
      />
    </div>
  );
}
