import { useId, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { SimpleSelect } from '@/components/ui/SimpleSelect';
import { useUserSettings, useUpdateUserSettings } from '@/hooks/useUserSettings';
import { availableTimezones, resolveTimezone } from '@/utils/timezone';

export function TimezoneSettings() {
  const { t } = useTranslation('settings');
  const { data: settings } = useUserSettings();
  const updateSettings = useUpdateUserSettings();
  const id = useId();
  const timezone = resolveTimezone(settings?.timezone, settings?.useDeviceTimezone ?? true);
  const zones = useMemo(() => availableTimezones(settings?.timezone), [settings?.timezone]);
  const value = settings?.useDeviceTimezone === false ? settings.timezone : '__device__';
  const [status, setStatus] = useState<'saved' | 'error' | null>(null);

  const changeTimezone = (selected: string): void => {
    setStatus(null);
    updateSettings.mutate(
      selected === '__device__'
        ? { useDeviceTimezone: true }
        : { useDeviceTimezone: false, timezone: selected },
      { onSuccess: () => setStatus('saved'), onError: () => setStatus('error') }
    );
  };

  return (
    <div className="bg-surface rounded-lg p-6 border border-border min-w-0">
      <label htmlFor={id} className="block text-sm font-medium text-text-secondary mb-2">
        {t('display.timezone.label')}
      </label>
      <p id={`${id}-help`} className="text-xs text-text-muted mb-4">
        {t('display.timezone.description')}
      </p>
      <SimpleSelect
        id={id}
        value={value}
        onChange={event => changeTimezone(event.target.value)}
        disabled={updateSettings.isPending}
        aria-describedby={`${id}-help ${id}-zone`}
        className="min-h-11 max-w-full text-base"
      >
        <option value="__device__">{t('display.timezone.device')}</option>
        {zones.map(zone => (
          <option key={zone} value={zone}>
            {zone.replaceAll('_', ' ')}
          </option>
        ))}
      </SimpleSelect>
      <p id={`${id}-zone`} className="mt-2 text-xs text-text-muted break-words">
        {t('display.timezone.current', { timezone })}
      </p>
      {status && (
        <p
          role={status === 'error' ? 'alert' : 'status'}
          className={`mt-2 text-sm ${status === 'error' ? 'text-error' : 'text-text-secondary'}`}
        >
          {t(`display.timezone.${status === 'error' ? 'updateError' : 'updateSuccess'}`)}
        </p>
      )}
    </div>
  );
}
