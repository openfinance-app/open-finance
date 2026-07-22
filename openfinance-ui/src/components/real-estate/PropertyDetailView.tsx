/**
 * PropertyDetailView Component
 * Task 9.1.11: Create PropertyDetailView component
 * 
 * Detailed view of a property with tabs for Overview, Equity, and ROI analysis
 */
import { useState } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/Dialog';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { LoadingSkeleton } from '@/components/LoadingComponents';
import { Home, Building2, Mountain, Building, Factory, MapPin, TrendingUp, DollarSign, Calendar, MapPinIcon } from 'lucide-react';
import { useProperty, usePropertyEquity, usePropertyROI } from '@/hooks/useRealEstate';
import { PrivateAmount } from '@/components/ui/PrivateAmount';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { PropertyType, getPropertyTypeName, getPropertyTypeBadgeColor, formatAppreciation, calculatePropertyAge } from '@/types/realEstate';
import { AttachmentList, AttachmentUpload } from '@/components/attachments';
import { AttachmentEntityType } from '@/types/attachment';
import { multiply } from '@/utils/money';
import { cn } from '@/lib/utils';

interface PropertyDetailViewProps {
  propertyId: number;
  onClose: () => void;
}

// Icon mapping for property types
function getPropertyTypeIcon(type: string): React.ReactNode {
  const icons: Record<string, React.ReactNode> = {
    [PropertyType.RESIDENTIAL]: <Home className="h-6 w-6" />,
    [PropertyType.COMMERCIAL]: <Building2 className="h-6 w-6" />,
    [PropertyType.LAND]: <Mountain className="h-6 w-6" />,
    [PropertyType.MIXED_USE]: <Building className="h-6 w-6" />,
    [PropertyType.INDUSTRIAL]: <Factory className="h-6 w-6" />,
    [PropertyType.OTHER]: <MapPin className="h-6 w-6" />,
  };
  return icons[type] || <MapPin className="h-6 w-6" />;
}

type TabType = 'overview' | 'equity' | 'roi' | 'attachments';

export function PropertyDetailView({ propertyId, onClose }: PropertyDetailViewProps) {
  const [activeTab, setActiveTab] = useState<TabType>('overview');

  const { data: property, isLoading: loadingProperty, error: propertyError } = useProperty(propertyId);
  const { data: equity, isLoading: loadingEquity } = usePropertyEquity(propertyId);
  const { data: roi, isLoading: loadingROI } = usePropertyROI(propertyId);

  if (propertyError) {
    return (
      <Dialog open={true} onOpenChange={onClose}>
        <DialogContent className="sm:max-w-4xl">
          <DialogHeader>
            <DialogTitle>Property Details</DialogTitle>
          </DialogHeader>
          <div className="p-4 bg-error/10 border border-error/20 rounded-lg text-error">
            Failed to load property details. Please try again.
          </div>
          <Button variant="ghost" onClick={onClose}>Close</Button>
        </DialogContent>
      </Dialog>
    );
  }

  return (
    <>
      <Dialog open={true} onOpenChange={onClose}>
        <DialogContent className="sm:max-w-4xl max-h-[90vh] overflow-y-auto">
          {loadingProperty ? (
            <div className="space-y-4">
              <DialogHeader>
                <DialogTitle className="sr-only">Loading property details</DialogTitle>
              </DialogHeader>
              <LoadingSkeleton className="h-8 w-3/4" />
              <LoadingSkeleton className="h-64" />
            </div>
          ) : property ? (
            <>
              {/* Header */}
              <DialogHeader>
                <div className="flex items-start justify-between">
                  <div className="flex items-start gap-4 flex-1">
                    <div className="flex h-14 w-14 items-center justify-center rounded-xl bg-primary/10 text-primary shrink-0">
                      {getPropertyTypeIcon(property.propertyType)}
                    </div>
                    <div className="flex-1 min-w-0">
                      <DialogTitle className="text-2xl mb-2">{property.name}</DialogTitle>
                      <div className="flex items-center gap-2 flex-wrap">
                        <div className={cn(
                          'inline-flex items-center px-2 py-1 rounded-md text-xs font-medium border',
                          getPropertyTypeBadgeColor(property.propertyType)
                        )}>
                          {getPropertyTypeName(property.propertyType)}
                        </div>
                        {!property.isActive && (
                          <div className="inline-flex items-center px-2 py-1 rounded-md bg-surface-elevated text-text-muted text-xs font-medium border border-border">
                            Inactive
                          </div>
                        )}
                      </div>
                    </div>
                  </div>

                </div>
              </DialogHeader>

              {/* Tabs */}
              <div className="border-b border-border">
                <div className="flex gap-4">
                  <button
                    onClick={() => setActiveTab('overview')}
                    className={cn(
                      'px-4 py-2 text-sm font-medium border-b-2 transition-colors',
                      activeTab === 'overview'
                        ? 'border-primary text-primary'
                        : 'border-transparent text-text-secondary hover:text-text-primary'
                    )}
                  >
                    Overview
                  </button>
                  <button
                    onClick={() => setActiveTab('equity')}
                    className={cn(
                      'px-4 py-2 text-sm font-medium border-b-2 transition-colors',
                      activeTab === 'equity'
                        ? 'border-primary text-primary'
                        : 'border-transparent text-text-secondary hover:text-text-primary'
                    )}
                  >
                    Equity
                  </button>
                  <button
                    onClick={() => setActiveTab('roi')}
                    className={cn(
                      'px-4 py-2 text-sm font-medium border-b-2 transition-colors',
                      activeTab === 'roi'
                        ? 'border-primary text-primary'
                        : 'border-transparent text-text-secondary hover:text-text-primary'
                    )}
                  >
                    ROI Analysis
                  </button>
                  <button
                    onClick={() => setActiveTab('attachments')}
                    className={cn(
                      'px-4 py-2 text-sm font-medium border-b-2 transition-colors',
                      activeTab === 'attachments'
                        ? 'border-primary text-primary'
                        : 'border-transparent text-text-secondary hover:text-text-primary'
                    )}
                  >
                    Attachments
                  </button>
                </div>
              </div>

              {/* Tab Content */}
              <div className="py-4">
                {/* Overview Tab */}
                {activeTab === 'overview' && (
                  <div className="space-y-6">
                    {/* Key Metrics Cards */}
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                      <Card className="p-4">
                        <div className="flex items-center gap-2 mb-1">
                          <DollarSign className="h-4 w-4 text-text-secondary" />
                          <p className="text-sm text-text-secondary">Current Value</p>
                        </div>
                        <p className="text-2xl font-bold text-text-primary">
                           {/* REQ-2.4: Show converted base-currency value when available */}
                           <ConvertedAmount
                             amount={property.currentValue}
                             currency={property.currency}
                             convertedAmount={property.valueInBaseCurrency}
                             baseCurrency={property.baseCurrency}
                             exchangeRate={property.exchangeRate}
                             isConverted={property.isConverted}
                             secondaryAmount={property.valueInSecondaryCurrency}
                             secondaryCurrency={property.secondaryCurrency}
                           />
                         </p>
                      </Card>

                      <Card className="p-4">
                        <div className="flex items-center gap-2 mb-1">
                          <TrendingUp className="h-4 w-4 text-text-secondary" />
                          <p className="text-sm text-text-secondary">Appreciation</p>
                        </div>
                        {property.appreciation !== undefined && property.appreciationPercentage !== undefined ? (
                          <p className={cn('text-2xl font-bold', formatAppreciation(property.appreciation, property.appreciationPercentage, property.currency).color)}>
                            {property.appreciation >= 0 ? '+' : '-'}
                            <ConvertedAmount
                              amount={Math.abs(property.appreciation)}
                              currency={property.currency}
                              convertedAmount={property.exchangeRate != null ? multiply(Math.abs(property.appreciation), property.exchangeRate) : undefined}
                              baseCurrency={property.baseCurrency}
                              exchangeRate={property.exchangeRate}
                              isConverted={property.isConverted}
                              inline
                            />
                            {' '}({property.appreciation >= 0 ? '+' : ''}{property.appreciationPercentage.toFixed(2)}%)
                          </p>
                        ) : (
                          <p className="text-2xl font-bold text-text-muted">N/A</p>
                        )}
                      </Card>

                      <Card className="p-4">
                        <div className="flex items-center gap-2 mb-1">
                          <Calendar className="h-4 w-4 text-text-secondary" />
                          <p className="text-sm text-text-secondary">Property Age</p>
                        </div>
                        <p className="text-2xl font-bold text-text-primary">
                          {calculatePropertyAge(property.purchaseDate)} years
                        </p>
                      </Card>
                    </div>

                    {/* Property Details */}
                    <Card className="p-6">
                      <h3 className="text-lg font-semibold text-text-primary mb-4">Property Details</h3>
                      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div>
                          <p className="text-sm text-text-secondary mb-1">Address</p>
                          <p className="text-text-primary">{property.address}</p>
                        </div>

                        <div>
                          <p className="text-sm text-text-secondary mb-1">Purchase Date</p>
                          <p className="text-text-primary">
                            {new Date(property.purchaseDate).toLocaleDateString('en-US', {
                              year: 'numeric',
                              month: 'long',
                              day: 'numeric'
                            })}
                          </p>
                        </div>

                         <div>
                           <p className="text-sm text-text-secondary mb-1">Purchase Price</p>
                           <p className="text-text-primary">
                             <ConvertedAmount
                               amount={property.purchasePrice}
                               currency={property.currency}
                               convertedAmount={property.exchangeRate != null ? multiply(property.purchasePrice, property.exchangeRate) : undefined}
                               baseCurrency={property.baseCurrency}
                               exchangeRate={property.exchangeRate}
                               isConverted={property.isConverted}
                             />
                           </p>
                         </div>

                        <div>
                          <p className="text-sm text-text-secondary mb-1">Currency</p>
                          <p className="text-text-primary">{property.currency}</p>
                        </div>

                        {property.mortgageName && (
                          <div>
                            <p className="text-sm text-text-secondary mb-1">Linked Mortgage</p>
                            <p className="text-text-primary">{property.mortgageName}</p>
                          </div>
                        )}

                         {property.rentalIncome && property.rentalIncome > 0 && (
                           <div>
                             <p className="text-sm text-text-secondary mb-1">Monthly Rental Income</p>
                             <p className="text-primary font-medium">
                               <ConvertedAmount
                                 amount={property.rentalIncome}
                                 currency={property.currency}
                                 convertedAmount={property.exchangeRate != null ? multiply(property.rentalIncome, property.exchangeRate) : undefined}
                                 baseCurrency={property.baseCurrency}
                                 exchangeRate={property.exchangeRate}
                                 isConverted={property.isConverted}
                                 inline
                               />/mo
                             </p>
                           </div>
                         )}
                      </div>
                    </Card>

                    {/* Location */}
                    {(property.latitude || property.longitude) && (
                      <Card className="p-6">
                        <h3 className="text-lg font-semibold text-text-primary mb-4 flex items-center gap-2">
                          <MapPinIcon className="h-5 w-5" />
                          Location
                        </h3>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                          {property.latitude && (
                            <div>
                              <p className="text-sm text-text-secondary mb-1">Latitude</p>
                              <p className="text-text-primary font-mono">{property.latitude.toFixed(6)}</p>
                            </div>
                          )}
                          {property.longitude && (
                            <div>
                              <p className="text-sm text-text-secondary mb-1">Longitude</p>
                              <p className="text-text-primary font-mono">{property.longitude.toFixed(6)}</p>
                            </div>
                          )}
                        </div>
                        <p className="mt-3 text-xs text-text-tertiary">
                          Future feature: Interactive map view
                        </p>
                      </Card>
                    )}

                    {/* Notes */}
                    {property.notes && (
                      <Card className="p-6">
                        <h3 className="text-lg font-semibold text-text-primary mb-3">Notes</h3>
                        <p className="text-text-secondary whitespace-pre-wrap">{property.notes}</p>
                      </Card>
                    )}
                  </div>
                )}

                {/* Equity Tab */}
                {activeTab === 'equity' && (
                  <div className="space-y-6">
                    {loadingEquity ? (
                      <LoadingSkeleton className="h-64" />
                    ) : equity ? (
                      <>
                         {/* Equity Summary Card */}
                         <Card className="p-6 bg-gradient-to-br from-green-500/10 to-green-500/5 border-green-500/20">
                           <h3 className="text-xl font-semibold text-text-primary mb-4">Property Equity</h3>
                           <p className="text-4xl font-bold text-green-400 mb-2">
                             <ConvertedAmount
                               amount={equity.equity}
                               currency={equity.currency}
                               convertedAmount={property.exchangeRate != null ? multiply(equity.equity, property.exchangeRate) : undefined}
                               baseCurrency={property.baseCurrency}
                               exchangeRate={property.exchangeRate}
                               isConverted={property.isConverted}
                             />
                           </p>
                           <p className="text-text-secondary">
                             {equity.equityPercentage.toFixed(2)}% of property value
                           </p>
                         </Card>

                        {/* Equity Calculation */}
                        <Card className="p-6">
                          <h3 className="text-lg font-semibold text-text-primary mb-4">Equity Calculation</h3>

                          <div className="space-y-4">
                             {/* Current Value */}
                             <div className="flex justify-between items-center text-lg">
                               <span className="text-text-secondary">Current Value</span>
                               <span className="text-text-primary font-semibold">
                                 <ConvertedAmount
                                   amount={equity.currentValue}
                                   currency={equity.currency}
                                   convertedAmount={property.exchangeRate != null ? multiply(equity.currentValue, property.exchangeRate) : undefined}
                                   baseCurrency={property.baseCurrency}
                                   exchangeRate={property.exchangeRate}
                                   isConverted={property.isConverted}
                                 />
                               </span>
                             </div>

                             {/* Mortgage Balance */}
                             {equity.hasMortgage && (
                               <>
                                 <div className="flex justify-between items-center text-lg">
                                   <span className="text-text-secondary">Mortgage Balance</span>
                                   <span className="text-error font-semibold">
                                     - <ConvertedAmount
                                       amount={equity.mortgageBalance}
                                       currency={equity.currency}
                                       convertedAmount={property.exchangeRate != null ? multiply(equity.mortgageBalance, property.exchangeRate) : undefined}
                                       baseCurrency={property.baseCurrency}
                                       exchangeRate={property.exchangeRate}
                                       isConverted={property.isConverted}
                                       inline
                                     />
                                   </span>
                                 </div>

                                 <div className="border-t border-border pt-4">
                                   <div className="flex justify-between items-center text-xl">
                                     <span className="text-text-primary font-semibold">Total Equity</span>
                                     <span className="text-green-400 font-bold">
                                       <ConvertedAmount
                                         amount={equity.equity}
                                         currency={equity.currency}
                                         convertedAmount={property.exchangeRate != null ? multiply(equity.equity, property.exchangeRate) : undefined}
                                         baseCurrency={property.baseCurrency}
                                         exchangeRate={property.exchangeRate}
                                         isConverted={property.isConverted}
                                       />
                                     </span>
                                   </div>
                                 </div>
                               </>
                             )}

                            {/* Visual Bar */}
                            <div className="mt-6">
                              <div className="flex justify-between text-sm text-text-secondary mb-2">
                                <span>Equity vs Debt</span>
                                <span>{equity.equityPercentage.toFixed(1)}% equity</span>
                              </div>
                              <div className="h-4 bg-surface rounded-full overflow-hidden">
                                <div
                                  className="h-full bg-green-500 transition-all duration-300"
                                  style={{ width: `${equity.equityPercentage}%` }}
                                />
                              </div>
                              <div className="flex justify-between text-xs text-text-tertiary mt-1">
                                <span>0%</span>
                                <span>100%</span>
                              </div>
                            </div>
                          </div>

                          {!equity.hasMortgage && (
                            <p className="mt-4 text-sm text-text-tertiary italic">
                              This property has no linked mortgage. Full equity belongs to you.
                            </p>
                          )}

                          {equity.mortgageId && (
                            <p className="mt-4 text-sm text-text-secondary">
                              Mortgage ID: {equity.mortgageId}
                            </p>
                          )}
                        </Card>


                      </>
                    ) : (
                      <div className="p-4 bg-error/10 border border-error/20 rounded-lg text-error">
                        Failed to load equity data.
                      </div>
                    )}
                  </div>
                )}

                {/* ROI Analysis Tab */}
                {activeTab === 'roi' && (
                  <div className="space-y-6">
                    {loadingROI ? (
                      <LoadingSkeleton className="h-64" />
                    ) : roi ? (
                      <>
                         {/* Overall ROI Summary */}
                         <Card className="p-6 bg-gradient-to-br from-primary/10 to-primary/5 border-primary/20">
                           <h3 className="text-xl font-semibold text-text-primary mb-4">Total Return on Investment</h3>
                           <p className="text-4xl font-bold text-primary mb-2">
                             <PrivateAmount>{roi.totalROI != null ? `${roi.totalROI.toFixed(2)}%` : 'N/A'}</PrivateAmount>
                           </p>
                           <p className="text-text-secondary">
                             Annualized return: {roi.annualizedReturn != null ? roi.annualizedReturn.toFixed(2) : 'N/A'}% per year
                           </p>
                           {roi.yearsOwned != null && (
                             <p className="text-sm text-text-tertiary mt-2">
                               Holding period: {roi.yearsOwned} year{roi.yearsOwned !== 1 ? 's' : ''}
                             </p>
                           )}
                         </Card>

                         {/* Appreciation Section */}
                         <Card className="p-6">
                           <h3 className="text-lg font-semibold text-text-primary mb-4">Appreciation</h3>
                           <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                             <div>
                               <p className="text-sm text-text-secondary mb-1">Purchase Price</p>
                               <p className="text-text-primary font-medium">
                                 <ConvertedAmount
                                   amount={roi.purchasePrice}
                                   currency={roi.currency}
                                   convertedAmount={property.exchangeRate != null ? multiply(roi.purchasePrice, property.exchangeRate) : undefined}
                                   baseCurrency={property.baseCurrency}
                                   exchangeRate={property.exchangeRate}
                                   isConverted={property.isConverted}
                                 />
                               </p>
                             </div>

                             <div>
                               <p className="text-sm text-text-secondary mb-1">Current Value</p>
                               <p className="text-text-primary font-medium">
                                 <ConvertedAmount
                                   amount={roi.currentValue}
                                   currency={roi.currency}
                                   convertedAmount={property.exchangeRate != null ? multiply(roi.currentValue, property.exchangeRate) : undefined}
                                   baseCurrency={property.baseCurrency}
                                   exchangeRate={property.exchangeRate}
                                   isConverted={property.isConverted}
                                 />
                               </p>
                             </div>

                             <div>
                               <p className="text-sm text-text-secondary mb-1">Total Appreciation</p>
                               <p className={cn(
                                 'font-bold text-lg',
                                 roi.appreciation >= 0 ? 'text-green-400' : 'text-red-400'
                               )}>
                                 {roi.appreciation >= 0 ? '+' : '-'}
                                 <ConvertedAmount
                                   amount={Math.abs(roi.appreciation)}
                                   currency={roi.currency}
                                   convertedAmount={property.exchangeRate != null ? multiply(Math.abs(roi.appreciation), property.exchangeRate) : undefined}
                                   baseCurrency={property.baseCurrency}
                                   exchangeRate={property.exchangeRate}
                                   isConverted={property.isConverted}
                                   inline
                                 /> ({roi.appreciationPercentage != null ? roi.appreciationPercentage.toFixed(2) : 'N/A'}%)
                               </p>
                             </div>

                             <div>
                               <p className="text-sm text-text-secondary mb-1">Annualized Return</p>
                               <p className="text-primary font-bold text-lg">
                                 {roi.annualizedReturn != null ? roi.annualizedReturn.toFixed(2) : 'N/A'}%
                               </p>
                             </div>
                           </div>
                         </Card>

                         {/* Rental Income Section */}
                         {roi.isRentalProperty && roi.monthlyRentalIncome != null && (
                           <Card className="p-6">
                             <h3 className="text-lg font-semibold text-text-primary mb-4">Rental Income</h3>
                             <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                               <div>
                                 <p className="text-sm text-text-secondary mb-1">Monthly Rental Income</p>
                                 <p className="text-primary font-bold text-lg">
                                   <ConvertedAmount
                                     amount={roi.monthlyRentalIncome}
                                     currency={roi.currency}
                                     convertedAmount={property.exchangeRate != null ? multiply(roi.monthlyRentalIncome, property.exchangeRate) : undefined}
                                     baseCurrency={property.baseCurrency}
                                     exchangeRate={property.exchangeRate}
                                     isConverted={property.isConverted}
                                     inline
                                   />/mo
                                 </p>
                               </div>

                               {roi.totalRentalIncome != null && (
                                 <div>
                                   <p className="text-sm text-text-secondary mb-1">Total Rental Income</p>
                                   <p className="text-text-primary font-medium">
                                     <ConvertedAmount
                                       amount={roi.totalRentalIncome}
                                       currency={roi.currency}
                                       convertedAmount={property.exchangeRate != null ? multiply(roi.totalRentalIncome, property.exchangeRate) : undefined}
                                       baseCurrency={property.baseCurrency}
                                       exchangeRate={property.exchangeRate}
                                       isConverted={property.isConverted}
                                     />
                                   </p>
                                 </div>
                               )}

                               {roi.rentalYield != null && (
                                 <div className="md:col-span-2">
                                   <p className="text-sm text-text-secondary mb-1">Rental Yield</p>
                                   <p className="text-green-400 font-bold text-lg">
                                     {roi.rentalYield.toFixed(2)}% annually
                                   </p>
                                   <p className="text-xs text-text-tertiary mt-1">
                                     Based on current property value
                                   </p>
                                 </div>
                               )}
                             </div>
                           </Card>
                         )}


                      </>
                    ) : (
                      <div className="p-4 bg-error/10 border border-error/20 rounded-lg text-error">
                        Failed to load ROI data.
                      </div>
                    )}
                  </div>
                )}
                {/* Attachments Tab */}
                {activeTab === 'attachments' && (
                  <div className="space-y-4 py-4">
                    <AttachmentList
                      entityType={AttachmentEntityType.REAL_ESTATE}
                      entityId={propertyId}
                    />
                    <AttachmentUpload
                      entityType={AttachmentEntityType.REAL_ESTATE}
                      entityId={propertyId}
                    />
                  </div>
                )}
              </div>
            </>
          ) : null}
        </DialogContent>
      </Dialog>
    </>
  );
}
