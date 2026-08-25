/**
 * SaveSearchDialog Component
 * Task 12.4.8: Add saved searches UI
 * 
 * Dialog for saving search filter combinations with a custom name
 */
import { useState } from 'react';
import { Save } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/Dialog';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { HelpTooltip } from '@/components/ui/HelpTooltip';

interface SaveSearchDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onSave: (name: string) => void;
  isLoading?: boolean;
}

export function SaveSearchDialog({
  isOpen,
  onClose,
  onSave,
  isLoading = false,
}: SaveSearchDialogProps) {
  const { t } = useTranslation(['common']);
  const [name, setName] = useState('');
  const [error, setError] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    
    // Validation
    if (!name.trim()) {
      setError(t('common:saveSearchDialog.errorEmpty'));
      return;
    }
    
    if (name.trim().length < 3) {
      setError(t('common:saveSearchDialog.errorTooShort'));
      return;
    }
    
    if (name.trim().length > 50) {
      setError(t('common:saveSearchDialog.errorTooLong'));
      return;
    }
    
    // Save and close
    onSave(name.trim());
    setName('');
    setError('');
  };

  const handleClose = () => {
    setName('');
    setError('');
    onClose();
  };

  return (
    <Dialog open={isOpen} onOpenChange={handleClose}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t('common:saveSearchDialog.title')}</DialogTitle>
        </DialogHeader>
        
        <form onSubmit={handleSubmit} className="space-y-4 mt-4">
          <div>
            <div className="flex items-center gap-1 mb-2">
              <label htmlFor="search-name" className="block text-sm font-medium text-text-primary">
                {t('common:saveSearchDialog.label')}
              </label>
              <HelpTooltip text={t('common:saveSearchDialog.hint')} side="right" />
            </div>
            <Input
              id="search-name"
              type="text"
              placeholder={t('common:saveSearchDialog.placeholder')}
              value={name}
              onChange={(e) => {
                setName(e.target.value);
                setError('');
              }}
              disabled={isLoading}
              autoFocus
              maxLength={50}
            />
            {error && (
              <p className="text-sm text-error mt-1">{error}</p>
            )}
          </div>

          <div className="flex items-center justify-end gap-3 pt-2">
            <Button
              type="button"
              variant="ghost"
              onClick={handleClose}
              disabled={isLoading}
            >
              {t('common:cancel')}
            </Button>
            <Button
              type="submit"
              variant="primary"
              disabled={isLoading || !name.trim()}
            >
              <Save className="h-4 w-4 mr-2" />
              {isLoading ? t('common:saveSearchDialog.saving') : t('common:saveSearchDialog.save')}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
