/**
 * Pagination Component
 * Task 3.3.6: Add pagination controls
 * 
 * Reusable pagination component with page navigation and size selector
 */
import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from './Button';
import { PAGE_SIZE_OPTIONS } from '@/constants/pagination';

interface PaginationProps {
  currentPage: number; // 0-indexed
  totalPages: number;
  pageSize: number;
  totalElements: number;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
  pageSizeOptions?: number[];
}


export function Pagination({
  currentPage,
  totalPages,
  pageSize,
  totalElements,
  onPageChange,
  onPageSizeChange,
  pageSizeOptions = PAGE_SIZE_OPTIONS,
}: PaginationProps) {
  const { t } = useTranslation('common');
  // Calculate showing range
  const startIndex = currentPage * pageSize + 1;
  const endIndex = Math.min((currentPage + 1) * pageSize, totalElements);

  // Generate page numbers to display
  const getPageNumbers = (): (number | 'ellipsis')[] => {
    const pages: (number | 'ellipsis')[] = [];
    const maxPagesToShow = 7;

    if (totalPages <= maxPagesToShow) {
      // Show all pages
      for (let i = 0; i < totalPages; i++) {
        pages.push(i);
      }
    } else {
      // Show first page, last page, current page and neighbors
      const leftSiblingIndex = Math.max(currentPage - 1, 1);
      const rightSiblingIndex = Math.min(currentPage + 1, totalPages - 2);

      const shouldShowLeftEllipsis = leftSiblingIndex > 1;
      const shouldShowRightEllipsis = rightSiblingIndex < totalPages - 2;

      // Always show first page
      pages.push(0);

      if (shouldShowLeftEllipsis) {
        pages.push('ellipsis');
      }

      // Show current page and neighbors
      for (let i = leftSiblingIndex; i <= rightSiblingIndex; i++) {
        pages.push(i);
      }

      if (shouldShowRightEllipsis) {
        pages.push('ellipsis');
      }

      // Always show last page
      pages.push(totalPages - 1);
    }

    return pages;
  };

  const pageNumbers = getPageNumbers();

  if (totalElements === 0) {
    return null;
  }

  return (
    <div className="flex flex-col sm:flex-row items-center justify-between gap-4 pt-4 border-t border-border">
      {/* Results info */}
      <div className="text-sm text-text-secondary">
        {t('pagination.showing')}{' '}
        <span className="font-medium text-text-primary">{startIndex}</span> {t('pagination.to')}{' '}
        <span className="font-medium text-text-primary">{endIndex}</span> {t('pagination.of')}{' '}
        <span className="font-medium text-text-primary">{totalElements}</span> {t('pagination.results')}
      </div>

      {/* Pagination controls */}
      <div className="flex items-center gap-2">
        {/* Page size selector */}
        <div className="flex items-center gap-2 mr-4">
          <span className="text-sm text-text-secondary">{t('pagination.show')}</span>
          <select
            value={pageSize}
            onChange={(e) => onPageSizeChange(Number(e.target.value))}
            className="h-9 px-2 rounded-lg bg-surface border border-border text-text-primary text-sm focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
            aria-label={t('pagination.rowsPerPage')}
          >
            {pageSizeOptions.map((size) => (
              <option key={size} value={size}>
                {size}
              </option>
            ))}
          </select>
        </div>

        {/* First page button */}
        <Button
          variant="ghost"
          size="sm"
          onClick={() => onPageChange(0)}
          disabled={currentPage === 0}
          className="hidden sm:flex"
          aria-label="First page"
        >
          <ChevronsLeft className="h-4 w-4" />
        </Button>

        {/* Previous page button */}
        <Button
          variant="ghost"
          size="sm"
          onClick={() => onPageChange(currentPage - 1)}
          disabled={currentPage === 0}
          aria-label="Previous page"
        >
          <ChevronLeft className="h-4 w-4" />
        </Button>

        {/* Page numbers */}
        <div className="flex items-center gap-1">
          {pageNumbers.map((page, index) => {
            if (page === 'ellipsis') {
              return (
                <span key={`ellipsis-${index}`} className="px-2 text-text-tertiary">
                  ...
                </span>
              );
            }

            return (
              <Button
                key={page}
                variant={currentPage === page ? 'primary' : 'ghost'}
                size="sm"
                onClick={() => onPageChange(page)}
                className="min-w-[36px]"
                aria-label={t('pagination.page', { current: page + 1, total: totalPages })}
                aria-current={currentPage === page ? 'page' : undefined}
              >
                {page + 1}
              </Button>
            );
          })}
        </div>

        {/* Next page button */}
        <Button
          variant="ghost"
          size="sm"
          onClick={() => onPageChange(currentPage + 1)}
          disabled={currentPage >= totalPages - 1}
          aria-label="Next page"
        >
          <ChevronRight className="h-4 w-4" />
        </Button>

        {/* Last page button */}
        <Button
          variant="ghost"
          size="sm"
          onClick={() => onPageChange(totalPages - 1)}
          disabled={currentPage >= totalPages - 1}
          className="hidden sm:flex"
          aria-label="Last page"
        >
          <ChevronsRight className="h-4 w-4" />
        </Button>
      </div>
    </div>
  );
}
