import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Banknote } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { DisburseForm } from '@/components/liabilities/DisburseForm';
import { useTranches } from '@/hooks/useTranches';
import { decimalField, ExactDecimal } from '@/utils/exact-decimal';
import type { Liability } from '@/types/liability';

/** The same drawdown action is available wherever the user manages loan movements. */
export function LoanDisbursementAction({ liability }: { liability: Liability }) {
  const { t } = useTranslation('liabilities');
  const { data: tranches = [], isLoading, error } = useTranches(liability.id);
  const [open, setOpen] = useState(false);
  const available = new ExactDecimal(
    decimalField(liability, liability.approvedAmount == null ? 'principal' : 'approvedAmount')
  ).minus(
    decimalField(liability, liability.fundedAmount == null ? 'currentBalance' : 'fundedAmount')
  );
  if (
    available.lte(0) ||
    liability.representedByAccountId != null ||
    liability.type === 'CREDIT_CARD'
  ) {
    return null;
  }
  return open ? (
    <div className="w-full basis-full">
      <DisburseForm
        liability={liability}
        tranche={tranches.find(tranche => tranche.status === 'PLANNED')}
        onDone={() => setOpen(false)}
      />
    </div>
  ) : (
    <Button
      variant="outline"
      size="sm"
      type="button"
      disabled={isLoading || !!error}
      onClick={() => setOpen(true)}
    >
      <Banknote className="h-4 w-4 mr-1.5" />
      {t('drawdowns.disburse')}
    </Button>
  );
}
