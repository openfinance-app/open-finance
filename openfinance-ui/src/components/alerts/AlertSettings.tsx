import React, { useState } from 'react';
import { isAxiosError } from 'axios';
import { useTranslation } from 'react-i18next';
import {
  useAlertsByBudget,
  useCreateAlert,
  useUpdateAlert,
  useDeleteAlert,
} from '@/hooks/useAlerts';
import { Plus, Trash2, Edit2, Check, X } from 'lucide-react';
import type { BudgetAlert, CreateAlertRequest, UpdateAlertRequest } from '@/types/alert';
import { NumberInput } from '@/components/ui/NumberInput';

/**
 * AlertSettings Component (Task 8.3.7)
 *
 * Configure alert thresholds per budget.
 * Features:
 * - Embedded in budget detail view or modal
 * - Shows existing alerts for a budget
 * - Add new alert: threshold slider (25%, 50%, 75%, 90%, 100%, 125%)
 * - Toggle enable/disable per alert
 * - Delete alert button
 * - Visual preview of when alert will trigger
 * - Form validation: threshold 1-150%, no duplicate thresholds
 */

interface AlertSettingsProps {
  budgetId: number;
}

export function AlertSettings({ budgetId }: AlertSettingsProps) {
  const { t } = useTranslation('budgets');
  const { data: alerts = [], isLoading } = useAlertsByBudget(budgetId, false);
  const createAlert = useCreateAlert();
  const updateAlert = useUpdateAlert();
  const deleteAlert = useDeleteAlert();

  const [isAddingAlert, setIsAddingAlert] = useState(false);
  const [newThreshold, setNewThreshold] = useState(75);
  const [error, setError] = useState<string | null>(null);

  const onError = (error: unknown): void => {
    setError(
      isAxiosError<{ message?: string }>(error)
        ? (error.response?.data?.message ?? t('alerts.saveError'))
        : t('alerts.saveError')
    );
  };

  const handleCreateAlert = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    // Validate threshold
    if (!Number.isFinite(newThreshold) || newThreshold < 1 || newThreshold > 150) {
      setError(t('alerts.thresholdInvalid'));
      return;
    }

    // Check for duplicate threshold
    const isDuplicate = alerts.some((alert: BudgetAlert) => alert.threshold === newThreshold);
    if (isDuplicate) {
      setError(t('alerts.duplicate', { threshold: newThreshold }));
      return;
    }

    const data: CreateAlertRequest = {
      threshold: newThreshold,
      isEnabled: true,
    };

    createAlert.mutate(
      { budgetId, data },
      {
        onSuccess: () => {
          setIsAddingAlert(false);
          setNewThreshold(75);
        },
        onError,
      }
    );
  };

  const handleToggleEnabled = (alertId: string, isEnabled: boolean) => {
    const data: UpdateAlertRequest = { isEnabled };
    setError(null);
    updateAlert.mutate({ alertId, data }, { onError });
  };

  const handleUpdateThreshold = (alertId: string, threshold: number) => {
    const data: UpdateAlertRequest = { threshold };
    setError(null);
    updateAlert.mutate({ alertId, data }, { onError });
  };

  const handleDeleteAlert = (alertId: string) => {
    if (confirm(t('alerts.confirmDelete'))) {
      setError(null);
      deleteAlert.mutate(alertId, { onError });
    }
  };

  const suggestedThresholds = [25, 50, 75, 90, 100, 125];
  const usedThresholds = new Set(alerts.map((a: BudgetAlert) => a.threshold));
  const availableThresholds = suggestedThresholds.filter(t => !usedThresholds.has(t));

  if (isLoading) {
    return (
      <div className="py-8 text-center text-text-secondary">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary mx-auto" />
        <p className="mt-2 text-sm">{t('alerts.loading')}</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h3 className="plate-label ">{t('alerts.title')}</h3>
        {!isAddingAlert && (
          <button
            onClick={() => setIsAddingAlert(true)}
            className="flex items-center gap-2 px-3 py-1.5 text-sm bg-primary text-black rounded-lg hover:bg-primary-hover transition-colors"
          >
            <Plus className="w-4 h-4" />
            {t('alerts.add')}
          </button>
        )}
      </div>

      {/* Error Message */}
      {error && (
        <div
          role="alert"
          className="p-3 bg-red-500/10 border border-red-500/50 rounded-lg text-red-400 text-sm"
        >
          {error}
        </div>
      )}

      {/* Existing Alerts */}
      {alerts.length > 0 ? (
        <div className="space-y-2">
          {alerts.map((alert: BudgetAlert) => (
            <AlertSettingItem
              key={alert.id}
              alert={alert}
              onToggleEnabled={isEnabled => handleToggleEnabled(alert.id, isEnabled)}
              onUpdateThreshold={threshold => handleUpdateThreshold(alert.id, threshold)}
              onDelete={() => handleDeleteAlert(alert.id)}
              isUpdating={updateAlert.isPending}
              isDeleting={deleteAlert.isPending}
            />
          ))}
        </div>
      ) : (
        <div className="py-8 text-center text-text-secondary">
          <p className="text-sm">{t('alerts.empty')}</p>
          <p className="text-xs mt-1">{t('alerts.emptyDescription')}</p>
        </div>
      )}

      {/* Add alert form */}
      {isAddingAlert && (
        <form
          onSubmit={handleCreateAlert}
          className="p-4 bg-surface rounded-lg border border-border"
        >
          <label
            htmlFor="new-budget-alert-threshold"
            className="block text-sm font-medium text-text-secondary mb-2"
          >
            {t('alerts.threshold')}
          </label>

          {/* Threshold Slider */}
          <div className="space-y-3">
            <input
              id="new-budget-alert-threshold"
              type="range"
              min="1"
              max="150"
              step="1"
              value={newThreshold}
              onChange={e => setNewThreshold(Number(e.target.value))}
              className="w-full h-2 bg-surface-elevated rounded-lg appearance-none cursor-pointer accent-primary"
            />
            <div className="flex items-center justify-between">
              <span className="text-sm text-text-secondary">1%</span>
              <span className="text-lg font-bold text-primary">{newThreshold}%</span>
              <span className="text-sm text-text-secondary">150%</span>
            </div>
          </div>

          {/* Suggested Thresholds */}
          {availableThresholds.length > 0 && (
            <div className="mt-3">
              <p className="text-xs text-text-secondary mb-2">{t('alerts.quickSelect')}</p>
              <div className="flex flex-wrap gap-2">
                {availableThresholds.map(threshold => (
                  <button
                    key={threshold}
                    type="button"
                    onClick={() => setNewThreshold(threshold)}
                    className="px-3 py-1 text-xs bg-surface-elevated hover:bg-surface-elevated/70 text-text-primary rounded transition-colors"
                  >
                    {threshold}%
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* Visual Preview */}
          <div className="mt-4 p-3 bg-background rounded border border-border">
            <p className="text-xs text-text-secondary mb-1">{t('alerts.preview')}</p>
            <p className="text-sm text-text-primary">
              {t('alerts.previewDescription', { threshold: newThreshold })}
            </p>
          </div>

          {/* Actions */}
          <div className="flex gap-2 mt-4">
            <button
              type="submit"
              disabled={createAlert.isPending}
              className="flex-1 flex items-center justify-center gap-2 px-4 py-2 bg-primary text-black rounded-lg hover:bg-primary-hover transition-colors disabled:opacity-50"
            >
              <Check className="w-4 h-4" />
              {t('alerts.create')}
            </button>
            <button
              type="button"
              onClick={() => {
                setIsAddingAlert(false);
                setError(null);
              }}
              className="px-4 py-2 bg-surface-elevated hover:bg-surface-elevated/70 text-text-primary rounded-lg transition-colors"
            >
              {t('alerts.cancel')}
            </button>
          </div>
        </form>
      )}
    </div>
  );
}

interface AlertSettingItemProps {
  alert: BudgetAlert;
  onToggleEnabled: (isEnabled: boolean) => void;
  onUpdateThreshold: (threshold: number) => void;
  onDelete: () => void;
  isUpdating: boolean;
  isDeleting: boolean;
}

function AlertSettingItem({
  alert,
  onToggleEnabled,
  onUpdateThreshold,
  onDelete,
  isUpdating,
  isDeleting,
}: AlertSettingItemProps) {
  const { t } = useTranslation('budgets');
  const [isEditing, setIsEditing] = useState(false);
  const [editThreshold, setEditThreshold] = useState(alert.threshold);
  const [invalidThreshold, setInvalidThreshold] = useState(false);

  const handleSaveEdit = () => {
    if (!Number.isFinite(editThreshold) || editThreshold < 1 || editThreshold > 150) {
      setInvalidThreshold(true);
      return;
    }
    setInvalidThreshold(false);
    if (editThreshold !== alert.threshold) {
      onUpdateThreshold(editThreshold);
    }
    setIsEditing(false);
  };

  const handleCancelEdit = () => {
    setEditThreshold(alert.threshold);
    setInvalidThreshold(false);
    setIsEditing(false);
  };

  return (
    <div
      role="group"
      aria-label={t('alerts.thresholdLabel', { threshold: alert.threshold })}
      className="flex items-center gap-3 p-3 bg-surface rounded-lg border border-border"
    >
      {/* Enable Toggle */}
      <button
        onClick={() => onToggleEnabled(!alert.isEnabled)}
        disabled={isUpdating || isDeleting}
        className={`w-10 h-6 rounded-full transition-colors relative flex-shrink-0 ${
          alert.isEnabled ? 'bg-primary' : 'bg-surface-elevated'
        } disabled:opacity-50`}
        aria-label={t(alert.isEnabled ? 'alerts.disable' : 'alerts.enable')}
      >
        <div
          className={`absolute top-1 w-4 h-4 bg-white rounded-full transition-transform ${
            alert.isEnabled ? 'translate-x-5' : 'translate-x-1'
          }`}
        />
      </button>

      {/* Threshold Display/Edit */}
      {isEditing ? (
        <div className="flex-1 flex flex-wrap items-center gap-2">
          <NumberInput
            min="1"
            max="150"
            aria-label={t('alerts.threshold')}
            aria-invalid={invalidThreshold}
            value={String(editThreshold)}
            onChange={val => {
              setEditThreshold(Number(val));
              setInvalidThreshold(false);
            }}
            className="w-20 px-2 py-1 bg-background border border-border rounded text-text-primary text-sm"
          />
          <span className="text-sm text-text-secondary">%</span>
          <button
            onClick={handleSaveEdit}
            className="p-1 text-green-400 hover:text-green-300 transition-colors"
            aria-label={t('alerts.save')}
          >
            <Check className="w-4 h-4" />
          </button>
          <button
            onClick={handleCancelEdit}
            className="p-1 text-text-secondary hover:text-text-primary transition-colors"
            aria-label={t('alerts.cancel')}
          >
            <X className="w-4 h-4" />
          </button>
          {invalidThreshold && (
            <p role="alert" className="w-full text-xs text-error">
              {t('alerts.thresholdInvalid')}
            </p>
          )}
        </div>
      ) : (
        <div className="flex-1 flex items-center gap-2">
          <span className="text-sm font-semibold text-text-primary">{alert.threshold}%</span>
          <span className="text-xs text-text-secondary">{t('alerts.thresholdSuffix')}</span>
          {alert.lastTriggered && (
            <span className="text-xs text-yellow-500">{t('alerts.triggered')}</span>
          )}
        </div>
      )}

      {/* Actions */}
      {!isEditing && (
        <div className="flex items-center gap-1">
          <button
            onClick={() => setIsEditing(true)}
            disabled={isUpdating || isDeleting}
            className="p-1.5 text-text-secondary hover:text-text-primary transition-colors disabled:opacity-50"
            aria-label={t('alerts.edit')}
          >
            <Edit2 className="w-4 h-4" />
          </button>
          <button
            onClick={onDelete}
            disabled={isUpdating || isDeleting}
            className="p-1.5 text-text-secondary hover:text-red-400 transition-colors disabled:opacity-50"
            aria-label={t('alerts.delete')}
          >
            <Trash2 className="w-4 h-4" />
          </button>
        </div>
      )}
    </div>
  );
}
