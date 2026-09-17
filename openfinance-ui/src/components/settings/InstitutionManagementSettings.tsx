/**
 * InstitutionManagementSettings component
 *
 * Settings section for managing financial institutions.
 * Users can view, add, edit, and delete custom institutions.
 */

import { useState, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { Building2, Plus, Pencil, Trash2, Upload, X } from 'lucide-react';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Card } from '@/components/ui/Card';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/Dialog';
import { LoadingSkeleton } from '@/components/LoadingComponents';
import {
  useInstitutions,
  useCreateInstitution,
  useUpdateInstitution,
  useDeleteInstitution,
} from '@/hooks/useInstitutions';
import type { Institution, InstitutionRequest } from '@/types/institution';
import { cn } from '@/lib/utils';
import { CountrySelector, ALL_COUNTRIES } from '@/components/common/CountrySelector';

const MAX_LOGO_SIZE = 500 * 1024; // 500KB
const ALLOWED_TYPES = ['image/png', 'image/jpeg', 'image/webp', 'image/svg+xml'];

export function InstitutionManagementSettings() {
  const { data: institutions, isLoading, error } = useInstitutions();
  const createInstitution = useCreateInstitution();
  const updateInstitution = useUpdateInstitution();
  const deleteInstitution = useDeleteInstitution();
  const { t } = useTranslation('institutions');
  useDocumentTitle(t('title'));

  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingInstitution, setEditingInstitution] = useState<Institution | null>(null);
  const [deletingInstitution, setDeletingInstitution] = useState<Institution | null>(null);
  const [formData, setFormData] = useState<InstitutionRequest>({
    name: '',
    bic: '',
    country: 'FR',
    logo: '',
  });
  const [searchQuery, setSearchQuery] = useState('');
  const [logoPreview, setLogoPreview] = useState<string | null>(null);
  const [logoError, setLogoError] = useState<string | null>(null);
  const [bicError, setBicError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [showAllSystem, setShowAllSystem] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Filter institutions by search query
  const filteredInstitutions = institutions?.filter(
    inst =>
      inst.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      inst.bic?.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const handleLogoUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    setLogoError(null);
    setFormError(null);

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

    // Read file as base64 — use functional update to avoid stale closure
    const reader = new FileReader();
    reader.onload = () => {
      const base64 = reader.result as string;
      setLogoPreview(base64);
      setFormData(prev => ({ ...prev, logo: base64 }));
    };
    reader.onerror = () => {
      setLogoError(t('form.logoReadError'));
    };
    reader.readAsDataURL(file);
  };

  const handleRemoveLogo = () => {
    setLogoPreview(null);
    setLogoError(null);
    setFormData(prev => ({ ...prev, logo: '' }));
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  const validateBic = (value: string) => {
    if (!value) {
      setBicError(null);
      return;
    }
    if (value.length !== 8 && value.length !== 11) {
      setBicError(t('form.bicLengthError'));
    } else {
      setBicError(null);
    }
  };

  const openCreateForm = () => {
    setEditingInstitution(null);
    setFormData({ name: '', bic: '', country: 'FR', logo: '' });
    setLogoPreview(null);
    setLogoError(null);
    setBicError(null);
    setFormError(null);
    setIsFormOpen(true);
  };

  const openEditForm = (inst: Institution) => {
    setEditingInstitution(inst);
    setFormData({
      name: inst.name,
      bic: inst.bic || '',
      country: inst.country || 'FR',
      logo: inst.logo || '',
    });
    // Show existing logo or local preview
    setLogoPreview(inst.logo || null);
    setLogoError(null);
    setBicError(null);
    setFormError(null);
    setIsFormOpen(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);

    // Sanitize: send undefined for empty optional fields to satisfy backend @Size constraints
    const trimmedName = formData.name.trim();
    const trimmedBic = formData.bic?.trim() || undefined;
    const sanitizedCountry = formData.country || undefined;

    // Client-side guard: prevent whitespace-only name from reaching the backend
    if (!trimmedName) {
      setFormError(t('form.nameRequired'));
      return;
    }

    // Client-side BIC format validation before submit
    if (trimmedBic && trimmedBic.length !== 8 && trimmedBic.length !== 11) {
      setBicError(t('form.bicLengthError'));
      return;
    }

    const requestData: InstitutionRequest = {
      name: trimmedName,
      bic: trimmedBic,
      country: sanitizedCountry,
      logo: formData.logo || undefined,
    };

    try {
      if (editingInstitution) {
        await updateInstitution.mutateAsync({
          id: editingInstitution.id,
          data: requestData,
        });
      } else {
        await createInstitution.mutateAsync(requestData);
      }
      setIsFormOpen(false);
      setEditingInstitution(null);
    } catch (error: any) {
      console.error('Failed to save institution:', error);
      const responseData = error?.response?.data;
      // Show field-level validation errors when available
      if (responseData?.validationErrors) {
        const fieldErrors = Object.entries(responseData.validationErrors as Record<string, string>)
          .map(([field, msg]) => `${field}: ${msg}`)
          .join('; ');
        setFormError(fieldErrors);
      } else {
        setFormError(responseData?.message || t('loadError'));
      }
    }
  };

  const handleDelete = async () => {
    if (!deletingInstitution) return;

    try {
      await deleteInstitution.mutateAsync(deletingInstitution.id);
      setDeletingInstitution(null);
    } catch (error: any) {
      console.error('Failed to delete institution:', error);
      alert(error?.response?.data?.message || t('deleteDialog.error'));
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

  const allCustomInstitutions = institutions?.filter(inst => !inst.isSystem) || [];
  const systemInstitutions = filteredInstitutions?.filter(inst => inst.isSystem) || [];
  const customInstitutions = filteredInstitutions?.filter(inst => !inst.isSystem) || [];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-semibold text-text-primary">{t('title')}</h2>
          <p className="text-sm text-text-secondary mt-1">{t('description')}</p>
        </div>
        <Button variant="primary" onClick={openCreateForm}>
          <Plus className="h-4 w-4 mr-2" />
          {t('addInstitution')}
        </Button>
      </div>

      {/* Search */}
      <div className="relative">
        <Input
          type="text"
          placeholder={t('searchPlaceholder')}
          value={searchQuery}
          onChange={e => setSearchQuery(e.target.value)}
          className="pl-10"
        />
        <Building2 className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-text-muted" />
      </div>

      {/* Custom Institutions Section */}
      <div className="space-y-4">
        <h3 className="text-lg font-medium text-text-primary">{t('custom.title')}</h3>
        {customInstitutions.length === 0 ? (
          allCustomInstitutions.length === 0 ? (
            <Card className="p-6 text-center">
              <Building2 className="h-12 w-12 mx-auto text-text-muted mb-3" />
              <p className="text-text-secondary">{t('custom.empty')}</p>
              <Button variant="primary" className="mt-4" onClick={openCreateForm}>
                <Plus className="h-4 w-4 mr-2" />
                {t('custom.addFirst')}
              </Button>
            </Card>
          ) : (
            <Card className="p-6 text-center">
              <Building2 className="h-12 w-12 mx-auto text-text-muted mb-3" />
              <p className="text-text-secondary">{t('custom.noResults')}</p>
            </Card>
          )
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {customInstitutions.map(inst => (
              <Card key={inst.id} className="p-4">
                <div className="flex items-start justify-between">
                  <div className="flex items-start gap-3">
                    <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10">
                      {inst.logo ? (
                        <img
                          src={inst.logo}
                          alt=""
                          className="h-8 w-8 rounded object-contain bg-white"
                        />
                      ) : (
                        <Building2 className="h-5 w-5 text-primary" />
                      )}
                    </div>
                    <div>
                      <h4 className="font-medium text-text-primary">{inst.name}</h4>
                      {inst.bic && <p className="text-xs text-text-muted">{inst.bic}</p>}
                      {inst.country && (
                        <p className="text-xs text-text-muted">
                          {ALL_COUNTRIES.find(c => c.code === inst.country)?.name || inst.country}
                        </p>
                      )}
                    </div>
                  </div>
                  <div className="flex gap-1">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => openEditForm(inst)}
                      className="h-8 w-8 p-0"
                    >
                      <Pencil className="h-4 w-4" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => setDeletingInstitution(inst)}
                      className="h-8 w-8 p-0 text-error hover:text-error hover:bg-error/10"
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              </Card>
            ))}
          </div>
        )}
      </div>

      {/* System Institutions Section */}
      <div className="space-y-4">
        <h3 className="text-lg font-medium text-text-primary">
          {t('system.title', { count: systemInstitutions.length })}
        </h3>
        <p className="text-sm text-text-secondary">{t('system.description')}</p>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {systemInstitutions.slice(0, showAllSystem ? undefined : 12).map(inst => (
            <Card key={inst.id} className="p-4 opacity-75">
              <div className="flex items-center gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10">
                  {inst.logo ? (
                    <img
                      src={inst.logo}
                      alt=""
                      className="h-8 w-8 rounded object-contain bg-white"
                    />
                  ) : (
                    <Building2 className="h-5 w-5 text-primary" />
                  )}
                </div>
                <div>
                  <h4 className="font-medium text-text-primary">{inst.name}</h4>
                  {inst.bic && <p className="text-xs text-text-muted">{inst.bic}</p>}
                </div>
              </div>
            </Card>
          ))}
        </div>
        {systemInstitutions.length > 12 && (
          <button
            type="button"
            onClick={() => setShowAllSystem(prev => !prev)}
            className="w-full text-sm text-primary hover:text-primary/80 text-center py-2 transition-colors"
          >
            {showAllSystem
              ? t('system.showFewer')
              : t('system.andMore', { count: systemInstitutions.length - 12 })}
          </button>
        )}
      </div>

      {/* Create/Edit Form Dialog */}
      <Dialog open={isFormOpen} onOpenChange={setIsFormOpen}>
        <DialogContent className="sm:max-w-[450px]">
          <DialogHeader>
            <DialogTitle>
              {editingInstitution ? t('form.editTitle') : t('form.addTitle')}
            </DialogTitle>
          </DialogHeader>
          <form onSubmit={handleSubmit} className="space-y-4">
            {formError && (
              <div className="p-3 bg-error/10 border border-error/20 rounded-lg text-sm text-error">
                {formError}
              </div>
            )}
            <div>
              <label className="block text-sm font-medium text-text-primary mb-1.5">
                {t('form.name')}
              </label>
              <Input
                value={formData.name}
                onChange={e => {
                  setFormData({ ...formData, name: e.target.value });
                  setFormError(null);
                }}
                placeholder={t('form.namePlaceholder')}
                required
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-text-primary mb-1.5">
                {t('form.bic')}
              </label>
              <Input
                value={formData.bic}
                onChange={e => {
                  const value = e.target.value.toUpperCase();
                  setFormData({ ...formData, bic: value });
                  setBicError(null);
                  setFormError(null);
                }}
                onBlur={e => validateBic(e.target.value)}
                placeholder={t('form.bicPlaceholder')}
                maxLength={11}
              />
              {bicError ? (
                <p className="text-xs text-error mt-1">{bicError}</p>
              ) : (
                <p className="text-xs text-text-muted mt-1">{t('form.bicHelp')}</p>
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-text-primary mb-1.5">
                {t('form.country')}
              </label>
              <CountrySelector
                value={formData.country ?? ''}
                onValueChange={value => {
                  setFormData({ ...formData, country: value });
                  setFormError(null);
                }}
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
                    id="logo-upload"
                  />
                  <label
                    htmlFor="logo-upload"
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
                {logoError && <p className="text-xs text-error">{logoError}</p>}

                {/* Help Text */}
                {!logoPreview && <p className="text-xs text-text-muted">{t('form.logoHelp')}</p>}
              </div>
            </div>

            <div className="flex justify-end gap-3 pt-4">
              <Button type="button" variant="ghost" onClick={() => setIsFormOpen(false)}>
                {t('form.cancel')}
              </Button>
              <Button
                type="submit"
                variant="primary"
                isLoading={createInstitution.isPending || updateInstitution.isPending}
                disabled={!formData.name.trim() || !!logoError || !!bicError}
              >
                {editingInstitution ? t('form.update') : t('form.create')}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog
        open={!!deletingInstitution}
        onOpenChange={open => !open && setDeletingInstitution(null)}
      >
        <DialogContent className="sm:max-w-[400px]">
          <DialogHeader>
            <DialogTitle>{t('deleteDialog.title')}</DialogTitle>
          </DialogHeader>
          <p className="text-text-secondary">
            {t('deleteDialog.description', { name: deletingInstitution?.name })}
          </p>
          <div className="flex justify-end gap-3 pt-4">
            <Button variant="ghost" onClick={() => setDeletingInstitution(null)}>
              {t('deleteDialog.cancel')}
            </Button>
            <Button
              variant="destructive"
              onClick={handleDelete}
              isLoading={deleteInstitution.isPending}
            >
              {t('deleteDialog.confirm')}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
