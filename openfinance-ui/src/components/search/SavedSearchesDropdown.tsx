/**
 * SavedSearchesDropdown Component
 * Task 12.4.8: Add saved searches UI
 *
 * Dropdown menu for loading, editing, and deleting saved searches
 */
import { useState } from 'react';
import { Bookmark, Trash2, Clock, ChevronDown } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { ConfirmationDialog } from '@/components/ConfirmationDialog';
import { Card } from '@/components/ui/Card';
import type { SavedSearch } from '@/types/search';
import { formatDistanceToNow } from 'date-fns';

interface SavedSearchesDropdownProps {
  savedSearches: SavedSearch[];
  onLoad: (search: SavedSearch) => void;
  onDelete: (id: string) => void;
}

export function SavedSearchesDropdown({
  savedSearches,
  onLoad,
  onDelete,
}: SavedSearchesDropdownProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [deletingSearch, setDeletingSearch] = useState<SavedSearch | null>(null);

  const handleLoad = (search: SavedSearch) => {
    onLoad(search);
    setIsOpen(false);
  };

  const handleDeleteClick = (e: React.MouseEvent, search: SavedSearch) => {
    e.stopPropagation();
    setDeletingSearch(search);
  };

  const handleConfirmDelete = () => {
    if (deletingSearch) {
      onDelete(deletingSearch.id);
      setDeletingSearch(null);
    }
  };

  // Sort by last used (most recent first), then by created date
  const sortedSearches = [...savedSearches].sort((a, b) => {
    const aDate = a.lastUsed || a.createdAt;
    const bDate = b.lastUsed || b.createdAt;
    return new Date(bDate).getTime() - new Date(aDate).getTime();
  });

  if (savedSearches.length === 0) {
    return null;
  }

  return (
    <>
      <div className="relative">
        <Button variant="ghost" onClick={() => setIsOpen(!isOpen)} className="gap-2">
          <Bookmark className="h-4 w-4" />
          Saved Searches
          <Badge variant="info" className="ml-1">
            {savedSearches.length}
          </Badge>
          <ChevronDown className={`h-4 w-4 transition-transform ${isOpen ? 'rotate-180' : ''}`} />
        </Button>

        {isOpen && (
          <>
            {/* Backdrop */}
            <div className="fixed inset-0 z-40" onClick={() => setIsOpen(false)} />

            {/* Dropdown */}
            <Card className="absolute top-full mt-2 right-0 z-50 w-80 max-h-96 overflow-y-auto shadow-lg">
              <div className="p-2">
                <div className="px-3 py-2 mb-2 border-b border-border">
                  <h3 className="font-semibold text-text-primary text-sm">Saved Searches</h3>
                  <p className="text-xs text-text-tertiary mt-0.5">Click to load a saved search</p>
                </div>

                <div className="space-y-1">
                  {sortedSearches.map(search => (
                    <button
                      key={search.id}
                      onClick={() => handleLoad(search)}
                      className="w-full px-3 py-2.5 rounded-lg hover:bg-surface-elevated transition-colors text-left group"
                    >
                      <div className="flex items-start justify-between gap-2">
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2 mb-1">
                            <Bookmark className="h-3.5 w-3.5 text-primary flex-shrink-0" />
                            <span className="font-medium text-text-primary text-sm truncate">
                              {search.name}
                            </span>
                          </div>

                          {/* Search details */}
                          <div className="text-xs text-text-tertiary space-y-0.5 ml-5">
                            {search.filters.query && (
                              <div className="truncate">Query: "{search.filters.query}"</div>
                            )}
                            {search.lastUsed && (
                              <div className="flex items-center gap-1">
                                <Clock className="h-3 w-3" />
                                Used{' '}
                                {formatDistanceToNow(new Date(search.lastUsed), {
                                  addSuffix: true,
                                })}
                              </div>
                            )}
                            {!search.lastUsed && (
                              <div className="flex items-center gap-1">
                                <Clock className="h-3 w-3" />
                                Created{' '}
                                {formatDistanceToNow(new Date(search.createdAt), {
                                  addSuffix: true,
                                })}
                              </div>
                            )}
                          </div>
                        </div>

                        {/* Delete button */}
                        <button
                          onClick={e => handleDeleteClick(e, search)}
                          className="opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0 p-1.5 rounded hover:bg-error/10 text-error"
                          title="Delete saved search"
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                        </button>
                      </div>
                    </button>
                  ))}
                </div>
              </div>
            </Card>
          </>
        )}
      </div>

      {/* Delete confirmation dialog */}
      <ConfirmationDialog
        open={!!deletingSearch}
        onOpenChange={open => !open && setDeletingSearch(null)}
        onConfirm={handleConfirmDelete}
        title="Delete Saved Search"
        description={`Are you sure you want to delete "${deletingSearch?.name}"? This action cannot be undone.`}
        confirmText="Delete"
        cancelText="Cancel"
      />
    </>
  );
}
