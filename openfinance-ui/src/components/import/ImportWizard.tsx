/**
 * ImportWizard Component
 *
 * Multi-step wizard for importing transactions from files.
 *
 * Steps:
 *   1. Upload File     — drag-and-drop / browse
 *   2. Select Account  — auto-detect or manual pick
 *   3. Review          — review transactions, inline edit, bulk edit,
 *                        auto-assign categories & payees, map categories
 *   4. Confirm Import  — summary + skip-duplicates toggle
 *   5. Importing       — live progress feedback
 */
import { useState, useEffect, useRef } from 'react';
import { useNavigate, useBeforeUnload } from 'react-router';
import { useTranslation } from 'react-i18next';
import { FileUpload } from './FileUpload';
import { ImportReview } from './ImportReview';
import { ImportProgress } from './ImportProgress';
import { Button } from '@/components/ui/Button';
import { SimpleSelect } from '@/components/ui/SimpleSelect';
import {
  ChevronLeft,
  ChevronRight,
  X,
  Upload,
  Users,
  FileSearch,
  CheckCircle,
  Loader2,
  AlertTriangle,
} from 'lucide-react';
import { useAccounts } from '@/hooks/useAccounts';
import {
  useStartImport,
  useImportSession,
  useImportTransactions,
  useConfirmImport,
  useCancelImport,
  useUpdateAccount,
  useUpdateTransactions,
} from '@/hooks/useImport';
import { useCreateCategory } from '@/hooks/useTransactions';
import { useCategories } from '@/hooks/useCategories';
import type {
  FileUploadResponse,
  ImportWizardStep,
  ImportTransactionDTO,
} from '@/types/import';

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const STEPS: ImportWizardStep[] = ['upload', 'account', 'review', 'confirm', 'progress'];

const STEP_INFO: Record<
  ImportWizardStep,
  { title: string; icon: React.ElementType; description: string }
> = {
  upload: {
    title: 'wizard.upload.title',
    icon: Upload,
    description: 'wizard.upload.description',
  },
  account: {
    title: 'wizard.account.title',
    icon: Users,
    description: 'wizard.account.description',
  },
  review: {
    title: 'wizard.review.title',
    icon: FileSearch,
    description: 'wizard.review.description',
  },
  confirm: {
    title: 'wizard.confirm.title',
    icon: CheckCircle,
    description: 'wizard.confirm.description',
  },
  progress: {
    title: 'wizard.progress.title',
    icon: Loader2,
    description: 'wizard.progress.description',
  },
};

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function ImportWizard() {
  const navigate = useNavigate();
  const { t } = useTranslation('import');

  // ── Wizard state ─────────────────────────────────────────────────────────
  const [currentStep, setCurrentStep] = useState<ImportWizardStep>('upload');
  const [uploadId, setUploadId] = useState<string | null>(null);
  const [fileName, setFileName] = useState<string>('');
  const [accountId, setAccountId] = useState<number | null>(null);
  const [sessionId, setSessionId] = useState<number | null>(null);

  /** Controls the "leave and cancel?" confirmation dialog */
  const [showCancelConfirm, setShowCancelConfirm] = useState(false);

  /** Source-category → target-categoryId mappings, collected in the review step */
  const [categoryMappings, setCategoryMappings] = useState<Record<string, number>>({});
  /** Category names from the import file that don't exist in DB yet — created on confirm */
  const [newCategoryNames, setNewCategoryNames] = useState<string[]>([]);
  const [skipDuplicates, setSkipDuplicates] = useState(true);

  /** Local (editable) copy of parsed transactions */
  const [localTransactions, setLocalTransactions] = useState<ImportTransactionDTO[]>([]);
  const [hasInitializedTransactions, setHasInitializedTransactions] = useState(false);

  // ── Remote data & mutations ───────────────────────────────────────────────
  const { data: accounts = [] } = useAccounts();
  const startImport = useStartImport();
  const { data: session, isLoading: isLoadingSession } = useImportSession(sessionId, {
    pollInterval: 2000,
  });
  // Pass session status so the hook disables itself once the session reaches a
  // terminal state (COMPLETED / FAILED / CANCELLED) — prevents 400 errors from
  // React Query re-fetching /review on an already-completed session.
  const { data: transactions = [], isLoading: isLoadingTransactions } =
    useImportTransactions(sessionId, session?.status);
  const confirmImport = useConfirmImport();
  const cancelImport = useCancelImport();
  const updateAccount = useUpdateAccount();
  const updateTransactions = useUpdateTransactions();
  const createCategory = useCreateCategory();
  const { data: allCategories = [] } = useCategories();

  // ── Sync remote transactions → local on first load ───────────────────────
  useEffect(() => {
    // Initialization: only once per session, and only if local state is empty
    if (transactions.length > 0 && !hasInitializedTransactions && localTransactions.length === 0) {
      setLocalTransactions([...transactions]);
      setHasInitializedTransactions(true);
    }
  }, [transactions, hasInitializedTransactions, localTransactions.length]);

  // Reset local state when a new session starts
  const lastSessionIdRef = useRef<number | null>(null);
  useEffect(() => {
    if (sessionId && sessionId !== lastSessionIdRef.current) {
      setHasInitializedTransactions(false);
      setLocalTransactions([]);
      setCategoryMappings({});
      setNewCategoryNames([]);
      lastSessionIdRef.current = sessionId;
    }
  }, [sessionId]);

  // ── Auto-populate accountId from session if backend matched one ─────────
  useEffect(() => {
    if (session?.accountId && accountId === null) {
      setAccountId(session.accountId);
    }
  }, [session?.accountId, accountId]);

  // ── Auto-advance: confirm → progress when import kicks off ───────────────
  useEffect(() => {
    if (session && session.status === 'IMPORTING' && currentStep === 'confirm') {
      setCurrentStep('progress');
    }
  }, [session, currentStep]);

  // ── Prevent accidental navigation when midway ───────────────
  const isMidway = !!sessionId && currentStep !== 'progress';

  // Block tab-close / page refresh
  useBeforeUnload(
    (e) => {
      if (isMidway) {
        e.preventDefault();
      }
    },
    { capture: true }
  );

  // ── Derived ──────────────────────────────────────────────────────────────
  const currentStepIndex = STEPS.indexOf(currentStep);

  // ── Handlers ─────────────────────────────────────────────────────────────

  const handleUploadSuccess = async (response: FileUploadResponse) => {
    if (!response.uploadId) return;

    setUploadId(response.uploadId);
    setFileName(response.fileName);
    setCurrentStep('account');

    try {
      const result = await startImport.mutateAsync({
        uploadId: response.uploadId,
        accountId: null,
        originalFileName: response.fileName,
      });
      setSessionId(result.id);
    } catch (error) {
      console.error('Failed to start import:', error);
    }
  };

  const handleApplyAccount = async () => {
    if (!sessionId) return;
    // Register the chosen account with the session then advance to review.
    // If no account was selected we skip the API call — the backend will
    // auto-create one from suggestedAccountName (or filename) at confirm time.
    if (accountId) {
      try {
        await updateAccount.mutateAsync({ sessionId, accountId });
      } catch (err) {
        console.error('Failed to update account:', err);
        return; // Don't advance on error
      }
    }
    setCurrentStep('review');
  };

  const handleConfirmImport = async () => {
    if (!sessionId) return;
    try {
      // Build final mappings: start with what the user already mapped, then create new categories.
      const finalMappings: Record<string, number> = { ...categoryMappings };

      // Determine the type (EXPENSE/INCOME) of transactions using each new category name.
      // Default to EXPENSE; if only income transactions use it, use INCOME.
      const getTypeForCategory = (catName: string): 'EXPENSE' | 'INCOME' => {
        const txns = localTransactions.filter((t) => t.category === catName);
        if (txns.length > 0 && txns.every((t) => t.amount > 0)) return 'INCOME';
        return 'EXPENSE';
      };

      for (const sourceName of newCategoryNames) {
        // Skip if already mapped by the user
        if (finalMappings[sourceName] != null) continue;

        // Check if it was created in a previous iteration (e.g., as a parent)
        const existing = allCategories.find(
          (c) => c.name.toLowerCase() === sourceName.toLowerCase()
        );
        if (existing) {
          finalMappings[sourceName] = existing.id;
          continue;
        }

        const type = getTypeForCategory(sourceName);

        // Detect hierarchical path (e.g. "Divers:Achat Divers" or "Divers/Achat Divers")
        const parts = sourceName.split(/[:/]/).map((p) => p.trim()).filter(Boolean);

        if (parts.length > 1) {
          // Hierarchical: find or create the parent first, then the leaf
          const parentName = parts[0];
          let parentId: number | undefined = undefined;

          const existingParent =
            allCategories.find((c) => c.name.toLowerCase() === parentName.toLowerCase()) ??
            // Also check already-created parents in finalMappings
            null;

          if (existingParent) {
            parentId = existingParent.id;
          } else {
            // Check if we already have a mapping for the parent name
            const parentMapping = finalMappings[parentName];
            if (parentMapping != null) {
              parentId = parentMapping;
            } else {
              // Create the parent
              const createdParent = await createCategory.mutateAsync({
                name: parentName,
                type,
              });
              parentId = createdParent.id;
              finalMappings[parentName] = createdParent.id;
            }
          }

          const leafName = parts[parts.length - 1];
          const createdLeaf = await createCategory.mutateAsync({
            name: leafName,
            type,
            parentId,
          });
          finalMappings[sourceName] = createdLeaf.id;
        } else {
          // Top-level category
          const created = await createCategory.mutateAsync({
            name: sourceName,
            type,
          });
          finalMappings[sourceName] = created.id;
        }
      }

      await confirmImport.mutateAsync({
        sessionId,
        accountId: accountId ?? null,
        categoryMappings: finalMappings,
        skipDuplicates,
      });
      setCurrentStep('progress');
    } catch (error) {
      console.error('Failed to confirm import:', error);
    }
  };

  const handleCancel = async () => {
    // If an import session is active, ask for confirmation first
    if (isMidway) {
      setShowCancelConfirm(true);
      return;
    }
    navigate('/import');
  };

  /** Called when the user confirms they want to leave mid-import */
  const handleCancelConfirmed = async () => {
    setShowCancelConfirm(false);
    if (sessionId && session?.cancellable) {
      try {
        await cancelImport.mutateAsync(sessionId);
      } catch (error) {
        console.error('Failed to cancel import:', error);
      }
    }
    navigate('/import');
  };

  const handleViewTransactions = () => navigate('/transactions');

  // ── Navigation guards ─────────────────────────────────────────────────────

  const canGoNext = (): boolean => {
    switch (currentStep) {
      case 'upload':
        return !!uploadId;
      case 'account':
        // Account selection is optional — only require that parsing has finished.
        // readyForReview is true once the backend status is PARSED or REVIEWING.
        // Also disable if startImport itself errored before creating a session.
        if (startImport.isError && !sessionId) return false;
        return !!session && session.readyForReview;
      case 'review':
        return localTransactions.length > 0;
      case 'confirm':
        return true;
      default:
        return false;
    }
  };

  const canGoPrevious = (): boolean =>
    currentStepIndex > 0 && currentStep !== 'progress' && !startImport.isPending;

  // ── handleNext ─────────────────────────────────────────────────────────────

  const handleNext = () => {
    const nextIndex = currentStepIndex + 1;
    if (nextIndex >= STEPS.length) return;
    const nextStep = STEPS[nextIndex];

    if (currentStep === 'account') {
      // handleApplyAccount registers the account (if one was selected) and
      // then advances to review. Works for all 3 account scenarios.
      handleApplyAccount();
      return;
    }

    if (currentStep === 'review' && sessionId) {
      updateTransactions
        .mutateAsync({ sessionId, transactions: localTransactions })
        .then(() => setCurrentStep(nextStep))
        .catch((e) => console.error('Failed to update transactions', e));
      return;
    }

    if (currentStep === 'confirm') {
      handleConfirmImport();
      return;
    }

    setCurrentStep(nextStep);
  };

  const handlePrevious = () => {
    const prevIndex = currentStepIndex - 1;
    if (prevIndex < 0) return;

    // Save local transactions to backend before leaving the review step
    // so edits are not lost upon returning.
    if (currentStep === 'review' && sessionId && localTransactions.length > 0) {
      updateTransactions
        .mutateAsync({ sessionId, transactions: localTransactions })
        .then(() => setCurrentStep(STEPS[prevIndex]))
        .catch((e) => {
          console.error('Failed to persist transactions before navigating back', e);
          // Navigate anyway — local state still holds the edits
          setCurrentStep(STEPS[prevIndex]);
        });
      return;
    }

    setCurrentStep(STEPS[prevIndex]);
  };

  // ── Render ──────────────────────────────────────────────────────────────

  const StepIcon = STEP_INFO[currentStep].icon;

  return (
    <div className="max-w-5xl mx-auto">

      {/* ── Cancel-confirmation overlay ─────────────────────────────────── */}
      {showCancelConfirm && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="cancel-confirm-title"
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm"
        >
          <div className="bg-surface border border-border rounded-xl shadow-xl p-6 max-w-sm w-full mx-4">
            <div className="flex items-start gap-3 mb-4">
              <AlertTriangle className="h-5 w-5 text-amber-500 mt-0.5 flex-shrink-0" />
              <div>
                <h3 id="cancel-confirm-title" className="text-base font-semibold text-text-primary">
                  {t('wizard.cancelConfirm.title')}
                </h3>
                <p className="text-sm text-text-secondary mt-1">
                  {t('wizard.cancelConfirm.description')}
                </p>
              </div>
            </div>
            <div className="flex justify-end gap-2">
              <Button variant="secondary" size="sm" onClick={() => setShowCancelConfirm(false)}>
                {t('wizard.cancelConfirm.stay')}
              </Button>
              <Button variant="danger" size="sm" onClick={handleCancelConfirmed}>
                {t('wizard.cancelConfirm.leave')}
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* ── Stepper header ─────────────────────────────────────────────── */}
      <div className="mb-8">
        <div className="flex items-center justify-between mb-6">
          <div>
            <h2 className="text-2xl font-bold text-text-primary flex items-center space-x-2">
              <StepIcon className="h-6 w-6 text-primary" />
              <span>{t(STEP_INFO[currentStep].title)}</span>
            </h2>
            <p className="text-sm text-text-secondary mt-1">
              {t(STEP_INFO[currentStep].description)}
            </p>
          </div>
          <Button variant="ghost" size="sm" onClick={handleCancel}>
            <X className="h-4 w-4 mr-2" />
            {t('common:buttons.cancel')}
          </Button>
        </div>

        {/* Progress steps */}
        <div className="flex items-center space-x-2">
          {(() => {
            // The "progress" step is the last one — it can never satisfy
            // `index < currentStepIndex`. We treat it as completed once the
            // session has reached a terminal state so it turns green.
            const isImportTerminal =
              !!session?.status &&
              ['COMPLETED', 'FAILED', 'CANCELLED'].includes(session.status);

            return STEPS.map((step, index) => {
              const isActive = step === currentStep && !isImportTerminal;
              const isCompleted =
                index < currentStepIndex ||
                (step === 'progress' && isImportTerminal);
              const StepIconComp = STEP_INFO[step].icon;

              return (
                <div key={step} className="flex items-center flex-1">
                  <div className="flex flex-col items-center flex-1">
                    <div
                      className={`flex items-center justify-center w-10 h-10 rounded-full border-2 transition-all ${isCompleted
                          ? 'border-green-500 bg-green-500 text-white'
                          : isActive
                            ? 'border-primary bg-primary text-white'
                            : 'border-border bg-surface text-text-tertiary'
                        }`}
                    >
                      <StepIconComp
                        className={`h-5 w-5 ${isActive && step === 'progress' ? 'animate-spin' : ''}`}
                      />
                    </div>
                    <div className="text-xs mt-2 text-center hidden sm:block text-text-secondary">
                      {t(STEP_INFO[step].title)}
                    </div>
                  </div>
                  {index < STEPS.length - 1 && (
                    <div
                      className={`h-0.5 flex-1 transition-all ${isCompleted ? 'bg-green-500' : 'bg-border'
                        }`}
                    />
                  )}
                </div>
              );
            });
          })()}
        </div>
      </div>

      {/* ── Step content ───────────────────────────────────────────────── */}
      <div className="bg-surface border border-border rounded-lg p-6 mb-6 min-h-[400px]">

        {/* Step 1 — Upload */}
        {currentStep === 'upload' && (
          <FileUpload
            onUploadSuccess={handleUploadSuccess}
            onUploadError={(error) => console.error(error)}
          />
        )}

        {/* Step 2 — Select Account */}
        {currentStep === 'account' && (
          <div className="space-y-5">
            <p className="text-sm text-text-secondary">
              {t('wizard.accountSelection.description')}
            </p>

            {/* ── Scenario banners ─────────────────────────────────────────── */}

            {/* Scenario 1: account detected AND matched to an existing account */}
            {session?.accountId && session?.suggestedAccountName && (
              <div className="flex items-start gap-3 p-3 rounded-lg border border-green-200 bg-green-50 text-sm text-green-800">
                <CheckCircle className="h-4 w-4 mt-0.5 flex-shrink-0 text-green-600" />
                <div>
                  <span className="font-semibold">{t('wizard.accountSelection.matched')}</span>{' '}
                  <span className="font-mono">{session.suggestedAccountName}</span> {t('wizard.accountSelection.matchedSuffix')}
                </div>
              </div>
            )}

            {/* Scenario 2: account detected but NOT matched to any existing account */}
            {!session?.accountId && session?.suggestedAccountName && (
              <div className="flex items-start gap-3 p-3 rounded-lg border border-amber-200 bg-amber-50 text-sm text-amber-800">
                <Users className="h-4 w-4 mt-0.5 flex-shrink-0 text-amber-600" />
                <div>
                  <span className="font-semibold">{t('wizard.accountSelection.detected')}</span>{' '}
                  <span className="font-mono">{session.suggestedAccountName}</span>.{' '}
                  {t('wizard.accountSelection.detectedSuffix', { name: session.suggestedAccountName })}
                </div>
              </div>
            )}

            {/* Scenario 3: no account information in the file at all */}
            {!session?.suggestedAccountName && session?.readyForReview && (
              <div className="flex items-start gap-3 p-3 rounded-lg border border-border bg-app-bg text-sm text-text-secondary">
                <FileSearch className="h-4 w-4 mt-0.5 flex-shrink-0 text-text-tertiary" />
                <div>
                  {t('wizard.accountSelection.noneFound', { name: fileName.replace(/\.[^.]+$/, '') || 'Imported' })}
                </div>
              </div>
            )}

            {/* ── Parsing in progress ──────────────────────────────────────── */}
            {(!session || (!session.readyForReview && session.status !== 'FAILED')) && !startImport.isError && (
              <div className="flex items-center gap-2 text-sm text-primary bg-primary/5 p-3 rounded-lg border border-primary/20">
                <Loader2 className="h-4 w-4 animate-spin flex-shrink-0" />
                <span>{t('wizard.accountSelection.parsing')}</span>
              </div>
            )}

            {/* ── Start Import Error (network/server error before session exists) ── */}
            {startImport.isError && !sessionId && (
              <div className="flex flex-col gap-2 text-sm text-red-800 bg-red-50 p-4 rounded-lg border border-red-200">
                <div className="flex items-start gap-2">
                  <AlertTriangle className="h-5 w-5 text-red-600 mt-0.5" />
                  <div>
                    <span className="font-semibold block">{t('wizard.accountSelection.uploadFailed')}</span>
                    <span>
                      {startImport.error?.message
                        ? startImport.error.message
                        : t('wizard.accountSelection.uploadFailedDesc')}
                    </span>
                  </div>
                </div>
                <div className="mt-2 ml-7">
                  <Button variant="outline" size="sm" onClick={() => window.location.reload()} className="bg-white">
                    {t('wizard.accountSelection.startOver')}
                  </Button>
                </div>
              </div>
            )}

            {/* ── Parsing Error ────────────────────────────────────────────── */}
            {session?.status === 'FAILED' && (
              <div className="flex flex-col gap-2 text-sm text-red-800 bg-red-50 p-4 rounded-lg border border-red-200">
                <div className="flex items-start gap-2">
                  <AlertTriangle className="h-5 w-5 text-red-600 mt-0.5" />
                  <div>
                    <span className="font-semibold block">{t('wizard.accountSelection.parsingFailed')}</span>
                    <span>{t('wizard.accountSelection.parsingFailedDesc')}</span>
                  </div>
                </div>
                <div className="mt-2 ml-7">
                  <Button variant="outline" size="sm" onClick={() => window.location.reload()} className="bg-white">
                    {t('wizard.accountSelection.startOver')}
                  </Button>
                </div>
              </div>
            )}

            {/* ── Account dropdown ─────────────────────────────────────────── */}
            <div>
              <label className="block text-sm font-medium text-text-primary mb-1.5">
                {t('wizard.accountSelection.label')}{' '}
                <span className="text-text-tertiary text-xs font-normal">({t('common:labels.optional')})</span>
              </label>
              <SimpleSelect
                value={accountId?.toString() ?? ''}
                onChange={(e) =>
                  setAccountId(e.target.value ? parseInt(e.target.value, 10) : null)
                }
                disabled={
                  isLoadingSession || startImport.isPending || updateAccount.isPending
                }
              >
                <option value="">
                  {session?.suggestedAccountName
                    ? t('wizard.accountSelection.autoCreateLabel', { name: session.suggestedAccountName })
                    : fileName
                      ? t('wizard.accountSelection.autoCreateLabel', { name: fileName.replace(/\.[^.]+$/, '') || 'Imported' })
                      : t('wizard.accountSelection.leaveBlank')}
                </option>
                {accounts.map((account) => (
                  <option key={account.id} value={account.id}>
                    {account.name} ({account.type})
                  </option>
                ))}
              </SimpleSelect>
            </div>

            {/* ── File name ────────────────────────────────────────────────── */}
            {fileName && (
              <div className="text-xs text-text-tertiary">
                {t('summary.file')}: <span className="text-text-secondary font-medium">{fileName}</span>
              </div>
            )}

            {/* ── Applying spinner ─────────────────────────────────────────── */}
            {updateAccount.isPending && (
              <div className="flex items-center gap-2 text-sm text-primary">
                <Loader2 className="h-4 w-4 animate-spin" />
                <span>{t('wizard.accountSelection.applying')}</span>
              </div>
            )}
          </div>
        )}

        {/* Step 3 — Review Transactions */}
        {currentStep === 'review' && (
          <div>
            {isLoadingTransactions ? (
              <div className="flex flex-col items-center justify-center py-12 gap-4">
                <Loader2 className="h-8 w-8 animate-spin text-primary" />
                <div className="text-center">
                  <p className="text-sm font-medium text-text-primary">
                    {t('review.loadingTitle')}
                  </p>
                  <p className="text-xs text-text-secondary mt-1">
                    {t('review.loadingDescription')}
                  </p>
                </div>
              </div>
            ) : (
              <ImportReview
                transactions={localTransactions}
                onTransactionsChange={setLocalTransactions}
                categoryMappings={categoryMappings}
                onCategoryMappingsChange={setCategoryMappings}
                newCategoryNames={newCategoryNames}
                onNewCategoryNamesChange={setNewCategoryNames}
              />
            )}
          </div>
        )}

        {/* Step 4 — Confirm Import */}
        {currentStep === 'confirm' && (
          <div className="space-y-6">
            <h3 className="text-lg font-semibold text-text-primary">{t('summary.title')}</h3>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="bg-app-bg border border-border rounded-lg p-4">
                <div className="text-sm text-text-secondary">{t('summary.file')}</div>
                <div className="text-base font-medium text-text-primary mt-1 truncate">
                  {fileName}
                </div>
              </div>

              <div className="bg-app-bg border border-border rounded-lg p-4">
                <div className="text-sm text-text-secondary">{t('summary.account')}</div>
                <div className="text-base font-medium text-text-primary mt-1">
                  {accountId
                    ? (accounts.find((a) => a.id === accountId)?.name ?? 'Unknown')
                    : (session?.suggestedAccountName
                      ? t('wizard.accountSelection.autoCreateLabel', { name: session.suggestedAccountName })
                      : t('wizard.accountSelection.autoCreateLabel', { name: fileName.replace(/\.[^.]+$/, '') || 'Imported' }))}
                </div>
              </div>

              <div className="bg-app-bg border border-border rounded-lg p-4">
                <div className="text-sm text-text-secondary">{t('summary.transactionsToImport')}</div>
                <div className="text-2xl font-bold text-text-primary mt-1">
                  {localTransactions.length}
                </div>
              </div>

              <div className="bg-app-bg border border-border rounded-lg p-4">
                <div className="text-sm text-text-secondary">{t('summary.categorized')}</div>
                <div className="text-2xl font-bold text-text-primary mt-1">
                  {localTransactions.filter((t) => !!t.category).length}
                  <span className="text-sm font-normal text-text-tertiary ml-1">
                    / {localTransactions.length}
                  </span>
                </div>
              </div>
            </div>

            <div className="flex items-center space-x-2 pt-2">
              <input
                type="checkbox"
                id="skipDuplicates"
                checked={skipDuplicates}
                onChange={(e) => setSkipDuplicates(e.target.checked)}
                className="rounded border-border text-primary focus:ring-primary"
              />
              <label
                htmlFor="skipDuplicates"
                className="text-sm text-text-secondary cursor-pointer"
              >
                {t('summary.skipDuplicates')}
              </label>
            </div>

            {confirmImport.isPending && (
              <div className="flex items-center space-x-2 text-sm text-primary">
                <Loader2 className="h-4 w-4 animate-spin" />
                <span>{t('summary.confirming')}</span>
              </div>
            )}
          </div>
        )}

        {/* Step 5 — Progress */}
        {currentStep === 'progress' && session && (
          <ImportProgress
            session={session}
            onViewTransactions={handleViewTransactions}
            onClose={handleCancel}
          />
        )}
      </div>

      {/* ── Navigation buttons ─────────────────────────────────────────── */}
      {currentStep !== 'progress' && (
        <div className="flex justify-between">
          <Button
            variant="secondary"
            onClick={handlePrevious}
            disabled={!canGoPrevious()}
          >
            <ChevronLeft className="h-4 w-4 mr-2" />
            {t('common:buttons.previous')}
          </Button>

          <Button
            variant="primary"
            onClick={handleNext}
            disabled={!canGoNext()}
            isLoading={
              startImport.isPending ||
              confirmImport.isPending ||
              updateAccount.isPending ||
              updateTransactions.isPending
            }
          >
            {currentStep === 'confirm' ? t('summary.confirm') : t('common:buttons.next')}
            {currentStep !== 'confirm' && <ChevronRight className="h-4 w-4 ml-2" />}
          </Button>
        </div>
      )}
    </div>
  );
}
