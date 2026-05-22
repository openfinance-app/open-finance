/**
 * BackupPage Component
 * Task 12.5.6: Create BackupPage component
 * 
 * Main page for managing database backups and restore operations
 * Requirements: REQ-2.14.2 (Data Backup & Restore)
 */
import { useState } from 'react';
import { Download, RefreshCw, Trash2, Upload, Database, Clock, AlertCircle } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useLocale } from '@/context/LocaleContext';
import { Button } from '@/components/ui/Button';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/components/ui/Dialog';
import { Input } from '@/components/ui/Input';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/layout/EmptyState';
import { LoadingSkeleton } from '@/components/LoadingComponents';
import { ConfirmationDialog } from '@/components/ConfirmationDialog';
import { Badge } from '@/components/ui/Badge';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import {
  useListBackups,
  useCreateBackup,
  useRestoreBackup,
  useUploadAndRestoreBackup,
  useDeleteBackup,
  useDownloadBackup,
} from '@/hooks/useBackup';
import type { BackupResponse, BackupStatus, BackupType } from '@/types/backup';

export default function BackupPage() {
  const { t } = useTranslation('backup');
  const { locale } = useLocale();
  useDocumentTitle(t('title'));

  // State management
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);
  const [isUploadDialogOpen, setIsUploadDialogOpen] = useState(false);
  const [backupDescription, setBackupDescription] = useState('');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [deletingBackup, setDeletingBackup] = useState<BackupResponse | null>(null);
  const [restoringBackup, setRestoringBackup] = useState<BackupResponse | null>(null);

  // API hooks
  const { data: backups, isLoading, error } = useListBackups();
  const createBackup = useCreateBackup();
  const restoreBackup = useRestoreBackup();
  const uploadAndRestore = useUploadAndRestoreBackup();
  const deleteBackup = useDeleteBackup();
  const downloadBackup = useDownloadBackup();

  // Handlers
  const handleCreateBackup = async () => {
    try {
      await createBackup.mutateAsync(backupDescription || undefined);
      setIsCreateDialogOpen(false);
      setBackupDescription('');
    } catch (error) {
      console.error('Failed to create backup:', error);
    }
  };

  const handleRestoreBackup = async () => {
    if (!restoringBackup) return;

    try {
      const message = await restoreBackup.mutateAsync(restoringBackup.id);
      alert(t('alerts.restoreSuccess', { message }));
      setRestoringBackup(null);
    } catch (error) {
      console.error('Failed to restore backup:', error);
      alert(t('alerts.restoreFailed'));
    }
  };

  const handleUploadAndRestore = async () => {
    if (!selectedFile) return;

    try {
      const message = await uploadAndRestore.mutateAsync(selectedFile);
      alert(t('alerts.restoreSuccess', { message }));
      setIsUploadDialogOpen(false);
      setSelectedFile(null);
    } catch (error) {
      console.error('Failed to upload and restore backup:', error);
      alert(t('alerts.restoreFailed'));
    }
  };

  const handleDeleteBackup = async () => {
    if (!deletingBackup) return;

    try {
      await deleteBackup.mutateAsync(deletingBackup.id);
      setDeletingBackup(null);
    } catch (error) {
      console.error('Failed to delete backup:', error);
    }
  };

  const handleDownloadBackup = async (backup: BackupResponse) => {
    try {
      await downloadBackup.mutateAsync({ backupId: backup.id, filename: backup.filename });
    } catch (error) {
      console.error('Failed to download backup:', error);
    }
  };

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      // Validate file extension
      if (!file.name.endsWith('.ofbak')) {
        alert(t('alerts.invalidFileType'));
        return;
      }
      // Validate file size (max 100 MB)
      const maxSize = 100 * 1024 * 1024; // 100 MB
      if (file.size > maxSize) {
        alert(t('alerts.fileTooLarge'));
        return;
      }
      setSelectedFile(file);
    }
  };

  // Helper functions
  const getStatusBadge = (status: BackupStatus) => {
    switch (status) {
      case 'COMPLETED':
        return <Badge variant="success">{t('status.completed')}</Badge>;
      case 'FAILED':
        return <Badge variant="error">{t('status.failed')}</Badge>;
      case 'IN_PROGRESS':
        return <Badge variant="warning">{t('status.inProgress')}</Badge>;
      case 'PENDING':
        return <Badge variant="default">{t('status.pending')}</Badge>;
      default:
        return <Badge variant="default">{status}</Badge>;
    }
  };

  const getTypeBadge = (type: BackupType) => {
    switch (type) {
      case 'MANUAL':
        return <Badge variant="info">{t('type.manual')}</Badge>;
      case 'AUTOMATIC':
        return <Badge variant="success">{t('type.automatic')}</Badge>;
      default:
        return <Badge variant="default">{type}</Badge>;
    }
  };

  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    return new Intl.DateTimeFormat(locale, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(date);
  };

  if (error) {
    return (
      <div className="p-8">
        <PageHeader title={t('title')} />
        <div className="mt-6 p-4 bg-error/10 border border-error/20 rounded-lg text-error">
          {t('loadError')}
        </div>
      </div>
    );
  }

  return (
    <div className="p-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <PageHeader
          title={t('title')}
          description={t('description')}
        />
        <div className="flex gap-3 shrink-0">
          <Button variant="outline" onClick={() => setIsUploadDialogOpen(true)}>
            <Upload className="h-4 w-4 mr-2" />
            {t('uploadBackup')}
          </Button>
          <Button variant="primary" onClick={() => setIsCreateDialogOpen(true)}>
            <Database className="h-4 w-4 mr-2" />
            {t('createBackup')}
          </Button>
        </div>
      </div>

      {/* Loading State */}
      {isLoading && (
        <div className="space-y-4">
          {[...Array(5)].map((_, i) => (
            <LoadingSkeleton key={i} className="h-20" />
          ))}
        </div>
      )}

      {/* Empty State */}
      {!isLoading && backups && backups.length === 0 && (
        <EmptyState
          title={t('empty.noBackups')}
          description={t('empty.description')}
          action={{
            label: t('empty.createCta'),
            onClick: () => setIsCreateDialogOpen(true),
          }}
        />
      )}

      {/* Backups Table */}
      {!isLoading && backups && backups.length > 0 && (
        <div className="bg-card rounded-lg border border-border overflow-x-auto">
          <table className="w-full min-w-[640px]">
            <thead className="bg-muted/50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-muted-foreground uppercase tracking-wider">
                  {t('table.filename')}
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-muted-foreground uppercase tracking-wider">
                  {t('table.size')}
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-muted-foreground uppercase tracking-wider">
                  {t('table.date')}
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-muted-foreground uppercase tracking-wider">
                  {t('table.type')}
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-muted-foreground uppercase tracking-wider">
                  {t('table.status')}
                </th>
                <th className="px-6 py-3 text-right text-xs font-medium text-muted-foreground uppercase tracking-wider">
                  {t('table.actions')}
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {backups.map((backup) => (
                <tr key={backup.id} className="hover:bg-muted/30 transition-colors">
                  <td className="px-6 py-4">
                    <div className="flex flex-col">
                      <span className="text-sm font-medium text-foreground">
                        {backup.filename}
                      </span>
                      {backup.description && (
                        <span className="text-xs text-muted-foreground mt-1">
                          {backup.description}
                        </span>
                      )}
                      {backup.errorMessage && (
                        <div className="flex items-center gap-1 mt-1 text-xs text-error">
                          <AlertCircle className="h-3 w-3" />
                          {backup.errorMessage}
                        </div>
                      )}
                    </div>
                  </td>
                  <td className="px-6 py-4 text-sm text-muted-foreground">
                    {backup.formattedFileSize}
                  </td>
                  <td className="px-6 py-4">
                    <div className="flex items-center gap-2 text-sm text-muted-foreground">
                      <Clock className="h-4 w-4" />
                      {formatDate(backup.createdAt)}
                    </div>
                  </td>
                  <td className="px-6 py-4">
                    {getTypeBadge(backup.backupType)}
                  </td>
                  <td className="px-6 py-4">
                    {getStatusBadge(backup.status)}
                  </td>
                  <td className="px-6 py-4">
                    <div className="flex items-center justify-end gap-2">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => handleDownloadBackup(backup)}
                        disabled={backup.status !== 'COMPLETED'}
                        title={t('actions.download')}
                      >
                        <Download className="h-4 w-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => setRestoringBackup(backup)}
                        disabled={backup.status !== 'COMPLETED'}
                        title={t('actions.restore')}
                      >
                        <RefreshCw className="h-4 w-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => setDeletingBackup(backup)}
                        title={t('actions.delete')}
                      >
                        <Trash2 className="h-4 w-4 text-error" />
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Create Backup Dialog */}
      <Dialog open={isCreateDialogOpen} onOpenChange={setIsCreateDialogOpen}>
        <DialogContent className="sm:max-w-[425px]">
          <DialogHeader>
            <DialogTitle>{t('createDialog.title')}</DialogTitle>
            <DialogDescription>
              {t('createDialog.description')}
            </DialogDescription>
          </DialogHeader>
          <div className="py-4">
            <Input
              label={t('createDialog.descriptionLabel')}
              value={backupDescription}
              onChange={(e) => setBackupDescription(e.target.value)}
              placeholder={t('createDialog.descriptionPlaceholder')}
              maxLength={200}
            />
          </div>
          <DialogFooter>
            <Button
              variant="ghost"
              onClick={() => {
                setIsCreateDialogOpen(false);
                setBackupDescription('');
              }}
              disabled={createBackup.isPending}
            >
              {t('createDialog.cancel')}
            </Button>
            <Button
              variant="primary"
              onClick={handleCreateBackup}
              disabled={createBackup.isPending}
            >
              {createBackup.isPending ? t('createDialog.creating') : t('createDialog.create')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Upload Backup Dialog */}
      <Dialog open={isUploadDialogOpen} onOpenChange={setIsUploadDialogOpen}>
        <DialogContent className="sm:max-w-[425px]">
          <DialogHeader>
            <DialogTitle>{t('uploadDialog.title')}</DialogTitle>
            <DialogDescription>
              {t('uploadDialog.description')}
            </DialogDescription>
          </DialogHeader>
          <div className="py-4">
            <div className="border-2 border-dashed border-border rounded-lg p-6 text-center">
              <input
                type="file"
                accept=".ofbak"
                onChange={handleFileSelect}
                className="hidden"
                id="backup-file-input"
              />
              <label
                htmlFor="backup-file-input"
                className="cursor-pointer flex flex-col items-center gap-2"
              >
                <Upload className="h-8 w-8 text-muted-foreground" />
                <span className="text-sm text-foreground">
                  {selectedFile ? selectedFile.name : t('uploadDialog.selectFile')}
                </span>
                <span className="text-xs text-muted-foreground">
                  {t('uploadDialog.fileRestriction')}
                </span>
              </label>
            </div>
            <div className="mt-4 p-3 bg-warning/10 border border-warning/20 rounded-lg">
              <div className="flex items-start gap-2">
                <AlertCircle className="h-4 w-4 text-warning mt-0.5 flex-shrink-0" />
                <p className="text-xs text-warning">
                  {t('uploadDialog.warning')}
                </p>
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button
              variant="ghost"
              onClick={() => {
                setIsUploadDialogOpen(false);
                setSelectedFile(null);
              }}
              disabled={uploadAndRestore.isPending}
            >
              {t('uploadDialog.cancel')}
            </Button>
            <Button
              variant="primary"
              onClick={handleUploadAndRestore}
              disabled={!selectedFile || uploadAndRestore.isPending}
            >
              {uploadAndRestore.isPending ? t('uploadDialog.restoring') : t('uploadDialog.upload')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Restore Confirmation Dialog */}
      <ConfirmationDialog
        open={!!restoringBackup}
        onOpenChange={(open) => !open && setRestoringBackup(null)}
        onConfirm={handleRestoreBackup}
        title={t('restoreDialog.title')}
        description={t('restoreDialog.description', { date: restoringBackup ? formatDate(restoringBackup.createdAt) : '' })}
        confirmText={t('restoreDialog.confirmText')}
        variant="warning"
        loading={restoreBackup.isPending}
      />

      {/* Delete Confirmation Dialog */}
      <ConfirmationDialog
        open={!!deletingBackup}
        onOpenChange={(open) => !open && setDeletingBackup(null)}
        onConfirm={handleDeleteBackup}
        title={t('deleteDialog.title')}
        description={t('deleteDialog.description', { filename: deletingBackup?.filename })}
        confirmText={t('deleteDialog.confirmText')}
        variant="danger"
        loading={deleteBackup.isPending}
      />
    </div>
  );
}
