/**
 * PayeeManagementSettings component
 * 
 * Settings section for managing payees.
 * Users can view, add, edit, delete, and toggle visibility of payees.
 */

import { useState, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { User, Plus, Pencil, Trash2, Upload, X, Eye, EyeOff, FolderOpen, ArrowUpDown, ChevronDown, Check, Search } from 'lucide-react';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Card } from '@/components/ui/Card';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/Dialog';
import { LoadingSkeleton } from '@/components/LoadingComponents';
import { CategorySelect } from '@/components/ui/CategorySelect';
import { HelpTooltip } from '@/components/ui/HelpTooltip';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/DropdownMenu';
import {
  usePayees,
  useCreatePayee,
  useUpdatePayee,
  useDeletePayee,
  useTogglePayeeActive,
} from '@/hooks/usePayees';
import type { Payee, PayeeRequest } from '@/types/payee';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useAuthContext } from '@/context/AuthContext';
import { useSecondaryConversion } from '@/hooks/useSecondaryConversion';
import { cn } from '@/lib/utils';

const MAX_LOGO_SIZE = 500 * 1024; // 500KB
const ALLOWED_TYPES = ['image/png', 'image/jpeg', 'image/webp', 'image/svg+xml'];

type SortOption = 'name' | 'transactions' | 'amount';

export function PayeeManagementSettings() {
  const { t } = useTranslation('payees');
  useDocumentTitle(t('title'));
  const { baseCurrency } = useAuthContext();
  const { convert, secondaryCurrency: secCurrency, secondaryExchangeRate } = useSecondaryConversion(baseCurrency);
  const { data: payees, isLoading, error } = usePayees();
  const createPayee = useCreatePayee();
  const updatePayee = useUpdatePayee();
  const deletePayee = useDeletePayee();
  const togglePayeeActive = useTogglePayeeActive();

  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingPayee, setEditingPayee] = useState<Payee | null>(null);
  const [deletingPayee, setDeletingPayee] = useState<Payee | null>(null);
  const [formData, setFormData] = useState<PayeeRequest & { categoryId?: number }>({
    name: '',
    logo: '',
    categoryId: undefined,
  });
  const [searchQuery, setSearchQuery] = useState('');
  const [logoPreview, setLogoPreview] = useState<string | null>(null);
  const [logoError, setLogoError] = useState<string | null>(null);
  const [showInactive, setShowInactive] = useState(false);
  const [sortBy, setSortBy] = useState<SortOption>('name');
  const [formError, setFormError] = useState<string | null>(null);
  const [showAllSystem, setShowAllSystem] = useState(false);
  const [showAllCustom, setShowAllCustom] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Filter payees by search query
  const filteredPayees = payees?.filter((p) => {
    const matchesSearch =
      p.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      p.category?.toLowerCase().includes(searchQuery.toLowerCase()) ||
      p.categoryName?.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesActive = showInactive || p.isActive;
    return matchesSearch && matchesActive;
  })?.sort((a, b) => {
    switch (sortBy) {
      case 'transactions':
        return (b.transactionCount || 0) - (a.transactionCount || 0);
      case 'amount':
        return (b.totalAmount || 0) - (a.totalAmount || 0);
      case 'name':
      default:
        return a.name.localeCompare(b.name, undefined, { numeric: true, sensitivity: 'base' });
    }
  });

  const handleLogoUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    setLogoError(null);

    if (!file) return;

    // Validate file type
    if (!ALLOWED_TYPES.includes(file.type)) {
      setLogoError(t('form.logoInvalidType'));
      return;
    }

    // Validate file size
    if (file.size > MAX_LOGO_SIZE) {
      setLogoError(t('form.logoTooLarge'));
      return;
    }

    // Read file as base64
    const reader = new FileReader();
    reader.onload = () => {
      const base64 = reader.result as string;
      setLogoPreview(base64);
      setFormData({ ...formData, logo: base64 });
    };
    reader.onerror = () => {
      setLogoError(t('form.logoReadError'));
    };
    reader.readAsDataURL(file);
  };

  const handleRemoveLogo = () => {
    setLogoPreview(null);
    setFormData({ ...formData, logo: '' });
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  const openCreateForm = () => {
    setEditingPayee(null);
    setFormData({ name: '', logo: '', categoryId: undefined });
    setLogoPreview(null);
    setLogoError(null);
    setFormError(null);
    setIsFormOpen(true);
  };

  const openEditForm = (payee: Payee) => {
    setEditingPayee(payee);
    setFormData({
      name: payee.name,
      logo: payee.logo || '',
      categoryId: payee.categoryId,
    });
    setLogoPreview(payee.logo || null);
    setLogoError(null);
    setFormError(null);
    setIsFormOpen(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);

    try {
      if (editingPayee) {
        await updatePayee.mutateAsync({
          id: editingPayee.id,
          data: formData,
        });
      } else {
        await createPayee.mutateAsync(formData);
      }
      setIsFormOpen(false);
      setEditingPayee(null);
    } catch (error: unknown) {
      console.error('Failed to save payee:', error);
      const axiosError = error as { response?: { status?: number; data?: { message?: string } } };
      const status = axiosError?.response?.status;
      if (status === 409) {
        setFormError(t('form.duplicateName'));
      } else {
        const serverMsg = axiosError?.response?.data?.message;
        setFormError(serverMsg || t('form.saveFailed'));
      }
    }
  };

  const handleDelete = async () => {
    if (!deletingPayee) return;

    try {
      await deletePayee.mutateAsync(deletingPayee.id);
      setDeletingPayee(null);
    } catch (error: unknown) {
      console.error('Failed to delete payee:', error);
      const axiosError = error as { response?: { data?: { message?: string } } };
      alert(axiosError?.response?.data?.message || t('deleteDialog.error'));
    }
  };

  const handleToggleActive = async (payee: Payee) => {
    try {
      await togglePayeeActive.mutateAsync(payee.id);
    } catch (error) {
      console.error('Failed to toggle payee visibility:', error);
    }
  };

  if (isLoading) {
    return (
      <div className="space-y-4">
        <LoadingSkeleton className="h-12" />
        <LoadingSkeleton className="h-24" />
        <LoadingSkeleton className="h-24" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-4 bg-error/10 border border-error/20 rounded-lg text-error">
        {t('loadError')}
      </div>
    );
  }

  const systemPayees = filteredPayees?.filter((p) => p.isSystem) || [];
  const customPayees = filteredPayees?.filter((p) => !p.isSystem) || [];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-semibold text-text-primary">{t('title')}</h2>
          <p className="text-sm text-text-secondary mt-1">
            {t('description')}
          </p>
        </div>
        <Button variant="primary" onClick={openCreateForm}>
          <Plus className="h-4 w-4 mr-2" />
          {t('addPayee')}
        </Button>
      </div>

      {/* Search and Filter */}
      <div className="flex gap-4">
        <div className="relative flex-1">
          <Input
            type="text"
            placeholder={t('searchPlaceholder')}
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="pl-10"
          />
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-text-muted" />
        </div>
        <Button
          variant={showInactive ? 'secondary' : 'ghost'}
          onClick={() => setShowInactive(!showInactive)}
          className="gap-2"
        >
          <EyeOff className="h-4 w-4" />
          {showInactive ? t('hideHidden') : t('showHidden')}
        </Button>

        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" className="gap-2">
              <ArrowUpDown className="h-4 w-4" />
              <span>{t('sortBy', { field: t(sortBy === 'name' ? 'sortName' : sortBy === 'transactions' ? 'sortTransactions' : 'sortAmount') })}</span>
              <ChevronDown className="h-4 w-4 opacity-50" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-[180px]">
            <DropdownMenuItem onClick={() => setSortBy('name')} className="flex items-center justify-between">
              {t('sortName')}
              {sortBy === 'name' && <Check className="h-4 w-4" />}
            </DropdownMenuItem>
            <DropdownMenuItem onClick={() => setSortBy('transactions')} className="flex items-center justify-between">
              {t('transactionCount')}
              {sortBy === 'transactions' && <Check className="h-4 w-4" />}
            </DropdownMenuItem>
            <DropdownMenuItem onClick={() => setSortBy('amount')} className="flex items-center justify-between">
              {t('totalAmount')}
              {sortBy === 'amount' && <Check className="h-4 w-4" />}
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      {/* Custom Payees Section */}
      <div className="space-y-4">
        <h3 className="text-lg font-medium text-text-primary">{t('custom.title')}</h3>
        {customPayees.length === 0 ? (
          <Card className="p-6 text-center">
            <User className="h-12 w-12 mx-auto text-text-muted mb-3" />
            {searchQuery ? (
              <p className="text-text-secondary">{t('custom.noSearchResults', { query: searchQuery })}</p>
            ) : (
              <>
                <p className="text-text-secondary">{t('custom.empty')}</p>
                <Button variant="primary" className="mt-4" onClick={openCreateForm}>
                  <Plus className="h-4 w-4 mr-2" />
                  {t('custom.addFirst')}
                </Button>
              </>
            )}
          </Card>
        ) : (
          <>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {customPayees.slice(0, showAllCustom ? undefined : 12).map((payee) => (
              <Card key={payee.id} className={cn('p-4', !payee.isActive && 'opacity-50')}>
                <div className="flex items-start justify-between">
                  <div className="flex items-start gap-3">
                    <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10">
                      {payee.logo ? (
                        <img
                          src={payee.logo}
                          alt=""
                          className="h-8 w-8 rounded object-contain bg-white"
                        />
                      ) : (
                        <User className="h-5 w-5 text-primary" />
                      )}
                    </div>
                    <div>
                      <h4 className="font-medium text-text-primary">{payee.name}</h4>
                      {payee.categoryName ? (
                        <p className="text-xs text-text-muted flex items-center gap-1">
                          <FolderOpen className="h-3 w-3" />
                          {payee.categoryName}
                        </p>
                      ) : null}

                      <div className="mt-2 flex flex-wrap gap-2">
                        <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-surface-elevated text-text-secondary border border-border">
                          {t('transactions', { count: payee.transactionCount || 0 })}
                        </span>
                        <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-primary/5 text-primary border border-primary/10">
                          <ConvertedAmount
                            amount={payee.totalAmount || 0}
                            currency={baseCurrency}
                            isConverted={false}
                            secondaryAmount={convert(payee.totalAmount || 0)}
                            secondaryCurrency={secCurrency}
                            secondaryExchangeRate={secondaryExchangeRate}
                            inline
                          />
                        </span>
                      </div>
                    </div>
                  </div>
                  <div className="flex gap-1">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => openEditForm(payee)}
                      className="h-8 w-8 p-0"
                      aria-label={t('form.editPayee', { name: payee.name })}
                    >
                      <Pencil className="h-4 w-4" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => setDeletingPayee(payee)}
                      className="h-8 w-8 p-0 text-error hover:text-error hover:bg-error/10"
                      aria-label={t('form.deletePayee', { name: payee.name })}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              </Card>
            ))}
          </div>
          {customPayees.length > 12 && (
            <button
              type="button"
              onClick={() => setShowAllCustom((prev) => !prev)}
              className="w-full text-sm text-primary hover:text-primary/80 text-center py-2 transition-colors"
            >
              {showAllCustom
                ? t('custom.showFewer')
                : t('custom.andMore', { count: customPayees.length - 12 })}
            </button>
          )}
          </>
        )}
      </div>

      {/* System Payees Section */}
      <div className="space-y-4">
        <h3 className="text-lg font-medium text-text-primary">
          {t('system.title', { count: systemPayees.length })}
        </h3>
        <p className="text-sm text-text-secondary">
          {t('system.description')}
        </p>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {systemPayees.slice(0, showAllSystem ? undefined : 12).map((payee) => (
            <Card key={payee.id} className={cn('p-4', !payee.isActive && 'opacity-50')}>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10">
                    {payee.logo ? (
                      <img
                        src={payee.logo}
                        alt=""
                        className="h-8 w-8 rounded object-contain bg-white"
                      />
                    ) : (
                      <User className="h-5 w-5 text-primary" />
                    )}
                  </div>
                  <div>
                    <h4 className="font-medium text-text-primary">{payee.name}</h4>
                    {payee.categoryName && (
                      <p className="text-xs text-text-muted">
                        {payee.categoryName}
                      </p>
                    )}
                    <div className="mt-1.5 flex gap-2">
                      <span className="text-[10px] text-text-muted">
                        {t('txn', { count: payee.transactionCount || 0 })}
                      </span>
                      <span className="text-[10px] text-text-muted">
                        <ConvertedAmount
                          amount={payee.totalAmount || 0}
                          currency={baseCurrency}
                          isConverted={false}
                          secondaryAmount={convert(payee.totalAmount || 0)}
                          secondaryCurrency={secCurrency}
                          secondaryExchangeRate={secondaryExchangeRate}
                          inline
                        />
                      </span>
                    </div>
                  </div>
                </div>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => handleToggleActive(payee)}
                  className="h-8 w-8 p-0"
                  title={payee.isActive ? t('system.hidePayee') : t('system.showPayee')}
                >
                  {payee.isActive ? (
                    <Eye className="h-4 w-4 text-text-muted" />
                  ) : (
                    <EyeOff className="h-4 w-4 text-text-muted" />
                  )}
                </Button>
              </div>
            </Card>
          ))}
        </div>
        {systemPayees.length > 12 && (
          <button
            type="button"
            onClick={() => setShowAllSystem((prev) => !prev)}
            className="w-full text-sm text-primary hover:text-primary/80 text-center py-2 transition-colors"
          >
            {showAllSystem
              ? t('system.showFewer')
              : t('system.andMore', { count: systemPayees.length - 12 })}
          </button>
        )}
      </div>

      {/* Create/Edit Form Dialog */}
      <Dialog open={isFormOpen} onOpenChange={(open) => { setIsFormOpen(open); if (!open) setFormError(null); }}>
        <DialogContent className="sm:max-w-[450px]">
          <DialogHeader>
            <DialogTitle>
              {editingPayee ? t('form.editTitle') : t('form.addTitle')}
            </DialogTitle>
          </DialogHeader>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-text-primary mb-1.5">
                {t('form.name')}
              </label>
              <Input
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                placeholder={t('form.namePlaceholder')}
                maxLength={100}
                required
              />
            </div>

            <div>
              <div className="flex items-center gap-1 mb-1.5">
                <label className="block text-sm font-medium text-text-primary">
                  {t('form.category')}
                </label>
                <HelpTooltip text={t('form.categoryHelp')} />
              </div>
              <CategorySelect
                value={formData.categoryId}
                onValueChange={(value) => setFormData({ ...formData, categoryId: value })}
                placeholder={t('form.categoryPlaceholder')}
                allowNone={true}
              />
            </div>

            {/* Logo Upload */}
            <div>
              <label className="block text-sm font-medium text-text-primary mb-1.5">
                {t('form.logo')}
              </label>
              <div className="space-y-3">
                {/* Logo Preview */}
                {logoPreview && (
                  <div className="relative inline-block">
                    <div className="flex h-16 w-16 items-center justify-center rounded-lg border border-border bg-surface overflow-hidden">
                      <img
                        src={logoPreview}
                        alt={t('form.logoPreview')}
                        className="h-full w-full object-contain"
                      />
                    </div>
                    <button
                      type="button"
                      onClick={handleRemoveLogo}
                      className="absolute -top-2 -right-2 h-5 w-5 rounded-full bg-error text-white flex items-center justify-center hover:bg-error/80"
                    >
                      <X className="h-3 w-3" />
                    </button>
                  </div>
                )}

                {/* Upload Button */}
                <div className="flex items-center gap-3">
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept={ALLOWED_TYPES.join(',')}
                    onChange={handleLogoUpload}
                    className="hidden"
                    id="logo-upload-payee"
                  />
                  <label
                    htmlFor="logo-upload-payee"
                    className={cn(
                      'flex items-center gap-2 px-4 py-2 rounded-lg border border-border',
                      'text-sm font-medium text-text-secondary cursor-pointer',
                      'hover:bg-surface-elevated hover:text-text-primary transition-colors'
                    )}
                  >
                    <Upload className="h-4 w-4" />
                    {logoPreview ? t('form.changeLogo') : t('form.uploadLogo')}
                  </label>
                  {logoPreview && (
                    <button
                      type="button"
                      onClick={handleRemoveLogo}
                      className="text-sm text-error hover:text-error/80"
                    >
                      {t('form.removeLogo')}
                    </button>
                  )}
                </div>

                {/* Error Message */}
                {logoError && (
                  <p className="text-xs text-error">{logoError}</p>
                )}

                {/* Help Text */}
                {!logoPreview && (
                  <p className="text-xs text-text-muted">
                    {t('form.logoHelp')}
                  </p>
                )}
              </div>
            </div>

            <div className="flex justify-end gap-3 pt-4">
              {formError && (
                <p className="flex-1 text-sm text-error self-center">{formError}</p>
              )}
              <Button type="button" variant="ghost" onClick={() => setIsFormOpen(false)}>
                {t('form.cancel')}
              </Button>
              <Button
                type="submit"
                variant="primary"
                isLoading={createPayee.isPending || updatePayee.isPending}
                disabled={!formData.name.trim()}
              >
                {editingPayee ? t('form.update') : t('form.create')}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={!!deletingPayee} onOpenChange={(open) => !open && setDeletingPayee(null)}>
        <DialogContent className="sm:max-w-[400px]">
          <DialogHeader>
            <DialogTitle>{t('deleteDialog.title')}</DialogTitle>
          </DialogHeader>
          <p className="text-text-secondary">
            {t('deleteDialog.description', { name: deletingPayee?.name })}
          </p>
          <div className="flex justify-end gap-3 pt-4">
            <Button variant="ghost" onClick={() => setDeletingPayee(null)}>
              {t('deleteDialog.cancel')}
            </Button>
            <Button
              variant="destructive"
              onClick={handleDelete}
              isLoading={deletePayee.isPending}
            >
              {t('deleteDialog.confirm')}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
