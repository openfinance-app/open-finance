import { useState, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from '@/components/ui/Select';
import { useLiabilities, getLiabilityTypeName } from '@/hooks/useLiabilities';
import { Loader2, Search, CreditCard } from 'lucide-react';
import type { Liability } from '@/types/liability';

interface LiabilitySelectorProps {
    value?: number;
    onValueChange: (value: number | undefined) => void;
    placeholder?: string;
    disabled?: boolean;
    className?: string;
    /** Filter liabilities by type, e.g. for mortgage only */
    liabilityFilter?: (liability: Liability) => boolean;
    /** Show "None" option at the top */
    allowNone?: boolean;
}

export function LiabilitySelector({
    value,
    onValueChange,
    placeholder = 'Select liability',
    disabled = false,
    className,
    liabilityFilter,
    allowNone = true,
}: LiabilitySelectorProps) {
    const { t } = useTranslation(['liabilities', 'common']);
    const { data: liabilities, isLoading, isError } = useLiabilities();
    const [searchQuery, setSearchQuery] = useState('');
    const [isOpen, setIsOpen] = useState(false);

    // Filter liabilities by search query and optional filter
    const filteredLiabilities = useMemo(() => {
        if (!liabilities) return [];

        let filtered = liabilities;
        if (liabilityFilter) {
            filtered = filtered.filter(liabilityFilter);
        }

        const normalizedQuery = searchQuery.trim().toLowerCase();
        if (!normalizedQuery) return filtered;

        return filtered.filter(
            (liability) =>
                liability.name.toLowerCase().includes(normalizedQuery) ||
                liability.type.toLowerCase().includes(normalizedQuery)
        );
    }, [liabilities, searchQuery, liabilityFilter]);

    // Group liabilities by type
    const groupedLiabilities = useMemo(() => {
        const groups: Record<string, Liability[]> = {};

        filteredLiabilities.forEach((liability) => {
            const type = liability.type || 'OTHER';
            if (!groups[type]) {
                groups[type] = [];
            }
            groups[type].push(liability);
        });

        // Sort each group by name
        Object.keys(groups).forEach((type) => {
            groups[type].sort((a, b) => a.name.localeCompare(b.name));
        });

        return groups;
    }, [filteredLiabilities]);

    // Get sorted categories
    const sortedTypes = useMemo(() => {
        return Object.keys(groupedLiabilities).sort((a, b) => {
            const labelA = getLiabilityTypeName(a) || a;
            const labelB = getLiabilityTypeName(b) || b;
            return labelA.localeCompare(labelB);
        });
    }, [groupedLiabilities]);

    // Find selected liability
    const selectedLiability = useMemo(() => {
        if (!value || !liabilities) return null;
        return liabilities.find((l) => l.id === value) || null;
    }, [value, liabilities]);

    const handleValueChange = (val: string) => {
        if (val === '__none__') {
            onValueChange(undefined);
        } else {
            onValueChange(val ? Number(val) : undefined);
        }
    };

    if (isLoading) {
        return (
            <div className="flex h-10 w-full items-center justify-center rounded-lg border border-border bg-surface px-3 py-2">
                <Loader2 className="h-4 w-4 animate-spin text-text-muted" />
                <span className="ml-2 text-sm text-text-muted">Loading...</span>
            </div>
        );
    }

    if (isError || !liabilities) {
        return (
            <div className="flex h-10 w-full items-center justify-center rounded-lg border border-error bg-surface px-3 py-2">
                <span className="text-sm text-error">Failed to load liabilities</span>
            </div>
        );
    }

    return (
        <Select
            value={value?.toString() || (allowNone ? '__none__' : '')}
            onValueChange={handleValueChange}
            disabled={disabled}
            open={isOpen}
            onOpenChange={(open) => {
                setIsOpen(open);
                if (!open) {
                    setSearchQuery('');
                }
            }}
        >
            <SelectTrigger className={className}>
                <SelectValue placeholder={placeholder}>
                    {selectedLiability ? (
                        <span className="flex items-center gap-2">
                            <CreditCard className="h-4 w-4 text-text-muted" />
                            <span>{selectedLiability.name}</span>
                            <span className="text-text-muted text-xs">
                                ({selectedLiability.currency})
                            </span>
                        </span>
                    ) : (
                        <span className="text-text-muted">{t('common:none')}</span>
                    )}
                </SelectValue>
            </SelectTrigger>
            <SelectContent 
                className="p-0 flex flex-col" 
                viewportClassName="p-1"
                headerSlot={
                    <div className="shrink-0 border-b border-border bg-surface p-2">
                        <div className="relative">
                            <Search className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted" />
                            <input
                                type="text"
                                value={searchQuery}
                                onChange={(event) => setSearchQuery(event.target.value)}
                                onKeyDown={(event) => event.stopPropagation()}
                                placeholder={t('liabilities:form.searchLiability')}
                                className="h-9 w-full rounded-md border border-border bg-background pl-8 pr-2 text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:ring-2 focus:ring-primary"
                                autoFocus={isOpen}
                            />
                        </div>
                    </div>
                }
            >
                {/* "None" option */}
                {allowNone && (
                    <SelectItem value="__none__" className="gap-2">
                        <span className="flex items-center gap-2">
                            <CreditCard className="h-4 w-4 text-text-muted" />
                            <span className="text-text-muted">{t('common:none')}</span>
                        </span>
                    </SelectItem>
                )}

                {/* Grouped liabilities */}
                {sortedTypes.map((type) => (
                    <div key={type} className="mt-2">
                        {/* Type header */}
                        <div className="px-2 py-1 text-xs font-semibold text-text-muted">
                            {getLiabilityTypeName(type)}
                        </div>

                        {/* Liabilities in this group */}
                        {groupedLiabilities[type].map((liability) => (
                            <SelectItem
                                key={liability.id}
                                value={liability.id.toString()}
                                className="gap-2"
                            >
                                <div className="flex items-center justify-between w-full gap-2">
                                    <span className="flex items-center gap-2 min-w-0">
                                        <CreditCard className="h-4 w-4 text-text-muted shrink-0" />
                                        <span className="text-sm truncate">{liability.name}</span>
                                    </span>
                                    <span className="text-xs text-text-muted shrink-0 whitespace-nowrap pt-0.5">
                                        {liability.currency} {liability.currentBalance.toLocaleString()}
                                    </span>
                                </div>
                            </SelectItem>
                        ))}
                    </div>
                ))}

                {/* Empty state */}
                {sortedTypes.length === 0 && (
                    <div className="p-3 text-center text-sm text-text-muted space-y-1">
                        <p>No liabilities found.</p>
                        <p className="text-xs">Create a liability first, then link it here.</p>
                    </div>
                )}
            </SelectContent>
        </Select>
    );
}
