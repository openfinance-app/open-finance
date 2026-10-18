import { Link } from 'react-router';
import { useTranslation } from 'react-i18next';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useAuthContext } from '@/context/AuthContext';
import { buildTransactionsLink } from '@/utils/navigation';
import type { CategoryTreeNode } from '@/types/transaction';

interface CategoryActivityProps {
  node: CategoryTreeNode;
  scope?: 'direct' | 'total';
}

export function CategoryTransactionCount({ node, scope = 'total' }: CategoryActivityProps) {
  const { t } = useTranslation('categories');
  const hasChildren = node.subcategories?.length > 0 || node.hasSubcategories;
  const includeSubcategories = scope === 'total' && !!hasChildren;
  const count =
    scope === 'direct'
      ? (node.directTransactionCount ?? node.transactionCount ?? 0)
      : (node.transactionCount ?? 0);
  return (
    <Link
      to={buildTransactionsLink({
        categoryId: node.id,
        includeSubcategories,
        excludeTransfers: true,
      })}
      className="inline-flex min-h-11 items-center rounded-sm underline decoration-border-strong underline-offset-4 hover:text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary lg:min-h-8"
      aria-label={t(
        includeSubcategories ? 'aria.viewSubtreeTransactions' : 'aria.viewDirectTransactions',
        { name: node.name }
      )}
    >
      {t('transactionCount', { count })}
    </Link>
  );
}

export function CategoryAmount({ node, scope = 'total' }: CategoryActivityProps) {
  const { t } = useTranslation('categories');
  const { baseCurrency } = useAuthContext();
  const currency = node.currency || baseCurrency;
  const hasChildren = node.subcategories?.length > 0 || node.hasSubcategories;
  const amount =
    scope === 'direct' ? (node.directAmount ?? node.totalAmount ?? 0) : (node.totalAmount ?? 0);
  return (
    <span
      className={`number-display tabular-nums ${amount > 0 ? 'text-success' : amount < 0 ? 'text-error' : 'text-text-secondary'}`}
      title={t(scope === 'total' && hasChildren ? 'includingSubcategories' : 'directAmount')}
    >
      <ConvertedAmount amount={amount} currency={currency} inline />
    </span>
  );
}
