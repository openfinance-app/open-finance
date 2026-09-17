/**
 * SettingsPage - User settings management
 *
 * Implements TASK-6.3:
 * - Tabbed interface for different settings categories
 * - General settings (base currency)
 * - Security settings (future: password change)
 * - Display settings (future: theme, date format)
 *
 * Requirements: REQ-6.3 (User Settings & Preferences)
 */
import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { GeneralSettings } from '@/components/settings/GeneralSettings';
import { DisplaySettings } from '@/components/settings/DisplaySettings';
import { SecuritySettings } from '@/components/settings/SecuritySettings';
import { BackupSettings } from '@/components/settings/BackupSettings';

type SettingsTab = 'general' | 'security' | 'display' | 'backup';

/**
 * Main settings page with tabbed interface
 */
export default function SettingsPage() {
  const { t } = useTranslation('settings');
  const [activeTab, setActiveTab] = useState<SettingsTab>('general');
  const [generalHasChanges, setGeneralHasChanges] = useState(false);

  // Warn user before leaving the page via browser/tab close while unsaved changes exist
  useEffect(() => {
    if (!generalHasChanges) return;
    const handler = (e: BeforeUnloadEvent) => {
      e.preventDefault();
    };
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [generalHasChanges]);

  const handleTabChange = (tab: SettingsTab) => {
    if (activeTab === 'general' && generalHasChanges && tab !== 'general') {
      if (!window.confirm(t('general.currencies.unsavedChangesWarning'))) return;
    }
    setActiveTab(tab);
  };

  return (
    <div className="min-h-screen bg-background p-6">
      <div className="max-w-4xl mx-auto">
        {/* Page Header */}
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-text-primary">{t('title')}</h1>
          <p className="text-text-secondary mt-2">{t('description')}</p>
        </div>

        {/* Tabs */}
        <div className="border-b border-border mb-6">
          <div className="flex space-x-8">
            <button
              onClick={() => handleTabChange('general')}
              className={`pb-4 px-2 text-sm font-medium transition-colors relative ${
                activeTab === 'general'
                  ? 'text-primary'
                  : 'text-text-secondary hover:text-text-primary'
              }`}
            >
              {t('tabs.general')}
              {activeTab === 'general' && (
                <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-primary" />
              )}
            </button>

            <button
              onClick={() => handleTabChange('security')}
              className={`pb-4 px-2 text-sm font-medium transition-colors relative ${
                activeTab === 'security'
                  ? 'text-primary'
                  : 'text-text-secondary hover:text-text-primary'
              }`}
            >
              {t('tabs.security')}
              {activeTab === 'security' && (
                <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-primary" />
              )}
            </button>

            <button
              onClick={() => handleTabChange('display')}
              className={`pb-4 px-2 text-sm font-medium transition-colors relative ${
                activeTab === 'display'
                  ? 'text-primary'
                  : 'text-text-secondary hover:text-text-primary'
              }`}
            >
              {t('tabs.display')}
              {activeTab === 'display' && (
                <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-primary" />
              )}
            </button>

            <button
              onClick={() => handleTabChange('backup')}
              className={`pb-4 px-2 text-sm font-medium transition-colors relative ${
                activeTab === 'backup'
                  ? 'text-primary'
                  : 'text-text-secondary hover:text-text-primary'
              }`}
            >
              {t('tabs.backup')}
              {activeTab === 'backup' && (
                <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-primary" />
              )}
            </button>
          </div>
        </div>

        {/* Tab Content */}
        <div>
          {activeTab === 'general' && <GeneralSettings onHasChanges={setGeneralHasChanges} />}

          {activeTab === 'security' && <SecuritySettings />}

          {activeTab === 'display' && <DisplaySettings />}

          {activeTab === 'backup' && <BackupSettings />}
        </div>
      </div>
    </div>
  );
}
