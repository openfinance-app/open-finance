/**
 * SharedParametersPanel Component
 * 
 * Displays data inherited from Buy/Rent comparator
 * Requirements: REQ-4.2.1
 */

import React from 'react';
import { Link2, Home, Wallet, FileText } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { useAuthContext } from '@/context/AuthContext';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useTranslation } from 'react-i18next';
import type { SharedPropertyData } from '@/types/realEstateTools';

export interface SharedParametersPanelProps {
  sharedData: SharedPropertyData;
}

export const SharedParametersPanel: React.FC<SharedParametersPanelProps> = ({
  sharedData,
}) => {
  const { baseCurrency } = useAuthContext();
  const { t } = useTranslation('realEstate');
  if (!sharedData) {
    return null;
  }

  return (
    <Card className="mb-6 border-primary/20 bg-primary/5">
      <CardHeader className="pb-3">
        <div className="flex items-center justify-between">
          <CardTitle className="text-base flex items-center gap-2">
            <Link2 className="h-4 w-4 text-primary" />
            {t('sharedParameters.importedDataTitle')}
          </CardTitle>
          <Badge variant="outline" className="text-xs">
            {t('sharedParameters.readOnly')}
          </Badge>
        </div>
      </CardHeader>
      <CardContent className="pt-0">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          {/* Property Price */}
          <div className="flex items-center gap-3 bg-background/50 p-3 rounded">
            <Home className="h-5 w-5 text-muted-foreground" />
            <div>
              <p className="text-xs text-muted-foreground">{t('sharedParameters.propertyPrice')}</p>
              <p className="font-medium">
                <ConvertedAmount amount={sharedData.totalPrice} currency={baseCurrency} inline />
              </p>
            </div>
          </div>

          {/* Credit Payment */}
          <div className="flex items-center gap-3 bg-background/50 p-3 rounded">
            <Wallet className="h-5 w-5 text-muted-foreground" />
            <div>
              <p className="text-xs text-muted-foreground">{t('sharedParameters.creditPayment')}</p>
              <p className="font-medium">
                <ConvertedAmount
                  amount={sharedData.credit.monthlyPayment}
                  currency={baseCurrency}
                  inline
                />
                {t('sharedParameters.creditPaymentPerMonth')}
              </p>
            </div>
          </div>

          {/* Property Tax */}
          <div className="flex items-center gap-3 bg-background/50 p-3 rounded">
            <FileText className="h-5 w-5 text-muted-foreground" />
            <div>
              <p className="text-xs text-muted-foreground">{t('sharedParameters.propertyTax')}</p>
              <p className="font-medium">
                <ConvertedAmount amount={sharedData.propertyTax} currency={baseCurrency} inline />
                {t('sharedParameters.propertyTaxPerYear')}
              </p>
            </div>
          </div>

          {/* Co-ownership Charges */}
          <div className="flex items-center gap-3 bg-background/50 p-3 rounded">
            <FileText className="h-5 w-5 text-muted-foreground" />
            <div>
              <p className="text-xs text-muted-foreground">{t('sharedParameters.coOwnershipCharges')}</p>
              <p className="font-medium">
                <ConvertedAmount
                  amount={sharedData.coOwnershipCharges}
                  currency={baseCurrency}
                  inline
                />
                {t('sharedParameters.coOwnershipChargesPerYear')}
              </p>
            </div>
          </div>
        </div>

        {/* Note */}
        <div className="mt-4 text-sm text-muted-foreground">
          <p>
            {t('sharedParameters.note')}
          </p>
        </div>
      </CardContent>
    </Card>
  );
};

export default SharedParametersPanel;
