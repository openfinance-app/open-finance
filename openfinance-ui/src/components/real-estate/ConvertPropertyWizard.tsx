import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { isAxiosError } from 'axios';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { NumberInput } from '@/components/ui/NumberInput';
import { LiabilitySelector } from '@/components/ui/LiabilitySelector';
import { OptionalDetails } from '@/components/ui/OptionalDetails';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { FormDialog } from '@/components/ui/FormDialog';
import {
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/Dialog';
import { useFormDraft } from '@/hooks/useFormDraft';
import { useDateFormatter } from '@/hooks/useDateFormatter';
import { useLiabilities } from '@/hooks/useLiabilities';
import {
  useConvertProperty,
  usePropertyConversionCandidates,
  usePropertyConversionEnabled,
} from '@/hooks/usePropertyConversion';
import type {
  PropertyConversionCandidate,
  PropertyConversionRequest,
} from '@/hooks/usePropertyConversion';
import { PropertyType, getPropertyTypeName } from '@/types/realEstate';
import { isValidDecimalString } from '@/utils/money';
import { ExactDecimal } from '@/utils/exact-decimal';
import { apiErrorMessage } from '@/utils/api-error';

interface Props {
  initialAssetId?: number;
  onClose: () => void;
  onConverted: (propertyId: number) => void;
}

export function ConvertPropertyWizard(props: Props) {
  const { t } = useTranslation('realEstate');
  const conversionEnabled = usePropertyConversionEnabled();
  if (!conversionEnabled) return null;
  return (
    <FormDialog open onOpenChange={open => !open && props.onClose()}>
      <DialogContent className="sm:max-w-[640px] max-h-[90dvh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{t('conversion.title')}</DialogTitle>
          <DialogDescription>{t('conversion.description')}</DialogDescription>
        </DialogHeader>
        <ConversionFlow {...props} />
      </DialogContent>
    </FormDialog>
  );
}

function Steps({ step }: { step: number }) {
  const { t } = useTranslation('realEstate');
  return (
    <ol aria-label={t('conversion.stepsLabel')} className="flex flex-wrap gap-x-5 gap-y-2 text-sm">
      {['source', 'details', 'review'].map((key, index) => (
        <li
          key={key}
          aria-current={index === step ? 'step' : undefined}
          className={index === step ? 'font-semibold text-primary' : 'text-text-secondary'}
        >
          {index + 1}. {t(`conversion.steps.${key}`)}
        </li>
      ))}
    </ol>
  );
}

function ConversionFlow({ initialAssetId, onClose, onConverted }: Props) {
  const { t } = useTranslation('realEstate');
  const query = usePropertyConversionCandidates();
  const [selected, setSelected] = useState(initialAssetId);
  const [search, setSearch] = useState('');
  const [source, setSource] = useState<PropertyConversionCandidate | null>(null);
  const filtered = (query.data ?? []).filter(item =>
    `${item.name} ${item.accountName}`.toLocaleLowerCase().includes(search.toLocaleLowerCase())
  );
  const candidate = query.data?.find(item => item.assetId === selected);

  if (source)
    return (
      <ConversionDetails
        source={source}
        onClose={onClose}
        onConverted={onConverted}
        onBack={() => {
          setSource(null);
          void query.refetch();
        }}
      />
    );

  return (
    <div className="space-y-5">
      <Steps step={0} />
      {query.isPending ? (
        <p role="status">{t('conversion.loading')}</p>
      ) : query.isError ? (
        <div className="space-y-3">
          <p role="alert" className="text-error">
            {t('conversion.loadError')}
          </p>
          <Button variant="outline" onClick={() => void query.refetch()}>
            {t('conversion.retry')}
          </Button>
        </div>
      ) : !query.data?.length ? (
        <p className="text-sm text-text-secondary">{t('conversion.empty')}</p>
      ) : (
        <>
          <Input
            id="property-source-search"
            label={t('conversion.search')}
            value={search}
            onChange={event => setSearch(event.target.value)}
          />
          <fieldset className="min-w-0 space-y-2">
            <legend className="mb-2 text-sm font-medium">{t('conversion.selectSource')}</legend>
            <div className="max-h-64 overflow-y-auto space-y-2 p-1">
              {filtered.map(item => (
                <label
                  key={item.assetId}
                  className={`flex min-w-0 cursor-pointer items-center gap-3 rounded-lg border p-3 hover:bg-surface-elevated ${selected === item.assetId ? 'border-primary' : 'border-border'}`}
                >
                  <input
                    type="radio"
                    name="property-source"
                    value={item.assetId}
                    checked={selected === item.assetId}
                    onChange={() => setSelected(item.assetId)}
                    className="h-4 w-4 shrink-0 accent-primary focus-visible:ring-2 focus-visible:ring-primary"
                  />
                  <span className="min-w-0 flex-1">
                    <span className="block break-words font-medium">{item.name}</span>
                    <span className="block break-words text-sm text-text-secondary">
                      {item.accountName}
                    </span>
                    <ConvertedAmount
                      amount={item.currentValue}
                      currency={item.currency}
                      className="text-sm font-mono tabular-nums"
                    />
                  </span>
                </label>
              ))}
              {!filtered.length && (
                <p role="status" className="text-sm text-text-secondary">
                  {t('conversion.noMatch')}
                </p>
              )}
            </div>
          </fieldset>
        </>
      )}
      <div className="flex flex-col-reverse gap-2 border-t border-border pt-4 sm:flex-row sm:justify-between">
        <Button variant="ghost" onClick={onClose}>
          {t('form.cancel')}
        </Button>
        <Button
          disabled={!candidate || query.isError || query.isPending}
          onClick={() => candidate && setSource(candidate)}
        >
          {t('wizard.next')}
        </Button>
      </div>
    </div>
  );
}

function ConversionDetails({
  source,
  onBack,
  onClose,
  onConverted,
}: {
  source: PropertyConversionCandidate;
  onBack: () => void;
  onClose: () => void;
  onConverted: (id: number) => void;
}) {
  const { t } = useTranslation('realEstate');
  const { date } = useDateFormatter();
  const [review, setReview] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState<PropertyConversionRequest | null>(null);
  const [operationId] = useState(() => crypto.randomUUID());
  const mutation = useConvertProperty();
  const { data: liabilities = [] } = useLiabilities();
  const heading = useRef<HTMLHeadingElement>(null);
  const schema = z.object({
    name: z
      .string()
      .trim()
      .min(1, t('form.validation.nameRequired'))
      .max(255, t('conversion.nameLimit')),
    address: z
      .string()
      .trim()
      .min(1, t('form.validation.addressRequired'))
      .max(500, t('conversion.addressLimit')),
    propertyType: z.enum(PropertyType),
    mortgageId: z.number().optional(),
    rentalIncome: z
      .string()
      .refine(
        value => value === '' || (isValidDecimalString(value) && new ExactDecimal(value).gte(0)),
        t('form.validation.rentalIncomeNonNegative')
      ),
  });
  const {
    register,
    control,
    handleSubmit,
    formState: { errors, isDirty },
  } = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: source.name,
      address: '',
      propertyType: 'RESIDENTIAL',
      rentalIncome: '',
    },
  });
  const cancel = useFormDraft(isDirty || review || pending !== null, mutation.isPending, onClose);
  const backToSource = useFormDraft(isDirty, mutation.isPending, onBack);
  const values = useWatch({ control });
  useEffect(() => {
    heading.current?.focus();
  }, [review]);

  const submit = handleSubmit(async details => {
    if (!review) {
      setReview(true);
      return;
    }
    if (mutation.isPending) return;
    const request = pending ?? {
      ...details,
      rentalIncome: details.rentalIncome || undefined,
      operationId,
      assetId: source.assetId,
      sourceRevision: source.sourceRevision,
    };
    setPending(request);
    setError(null);
    try {
      const result = await mutation.mutateAsync(request);
      onConverted(result.propertyId);
    } catch (failure) {
      if (
        isAxiosError(failure) &&
        failure.response &&
        failure.response.status >= 400 &&
        failure.response.status < 500
      )
        setPending(null);
      setError(apiErrorMessage(failure, t('conversion.error')));
    }
  });

  return (
    <form onSubmit={submit} className="space-y-5" noValidate>
      <Steps step={review ? 2 : 1} />
      <h3 ref={heading} tabIndex={-1} className="font-semibold focus:outline-none">
        {t(review ? 'conversion.reviewTitle' : 'conversion.detailsTitle')}
      </h3>
      <div hidden={review} className="space-y-4">
        <Input
          id="conversion-name"
          label={t('form.propertyName')}
          required
          maxLength={255}
          {...register('name')}
          error={errors.name?.message}
        />
        <Input
          id="conversion-address"
          label={t('form.address')}
          required
          maxLength={500}
          {...register('address')}
          error={errors.address?.message}
        />
        <div>
          <label htmlFor="conversion-type" className="mb-1.5 block text-sm font-medium">
            {t('form.propertyType')}
          </label>
          <select
            id="conversion-type"
            {...register('propertyType')}
            className="h-11 w-full rounded-lg border border-border bg-background px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            {Object.values(PropertyType).map(type => (
              <option key={type} value={type}>
                {getPropertyTypeName(type)}
              </option>
            ))}
          </select>
        </div>
        <OptionalDetails title={t('form.financingAndRent')} hasErrors={!!errors.rentalIncome}>
          <label htmlFor="conversion-mortgage" className="block text-sm font-medium">
            {t('form.linkedMortgage')}
          </label>
          <Controller
            name="mortgageId"
            control={control}
            render={({ field }) => (
              <LiabilitySelector
                id="conversion-mortgage"
                value={field.value}
                onValueChange={field.onChange}
                placeholder={t('form.selectMortgage')}
                liabilityFilter={loan => loan.type === 'MORTGAGE'}
              />
            )}
          />
          <label htmlFor="conversion-rent" className="block text-sm font-medium">
            {t('form.monthlyRentalIncome')} ({source.currency})
          </label>
          <Controller
            name="rentalIncome"
            control={control}
            render={({ field }) => (
              <NumberInput
                id="conversion-rent"
                value={field.value}
                onChange={field.onChange}
                min={0}
                error={errors.rentalIncome?.message}
              />
            )}
          />
        </OptionalDetails>
      </div>
      {review && (
        <>
          <dl className="space-y-3 text-sm">
            <ReviewRow label={t('conversion.source')}>
              <span>
                {source.name} · {source.accountName}
              </span>
            </ReviewRow>
            <ReviewRow label={t('form.propertyName')}>{values.name}</ReviewRow>
            <ReviewRow label={t('form.address')}>{values.address}</ReviewRow>
            <ReviewRow label={t('form.propertyType')}>
              {getPropertyTypeName(values.propertyType ?? 'RESIDENTIAL')}
            </ReviewRow>
            <ReviewRow label={t('conversion.recordedSince')}>{date(source.purchaseDate)}</ReviewRow>
            <ReviewRow label={t('conversion.cost')}>
              <ConvertedAmount
                amount={source.purchasePrice}
                currency={source.currency}
                className="font-mono tabular-nums"
              />
            </ReviewRow>
            <ReviewRow label={t('conversion.value')}>
              <ConvertedAmount
                amount={source.currentValue}
                currency={source.currency}
                className="font-mono tabular-nums"
              />
            </ReviewRow>
            {values.mortgageId && (
              <ReviewRow label={t('form.linkedMortgage')}>
                {liabilities.find(loan => loan.id === values.mortgageId)?.name}
              </ReviewRow>
            )}
            {values.rentalIncome && (
              <ReviewRow label={t('form.monthlyRentalIncome')}>
                <ConvertedAmount
                  amount={Number(values.rentalIncome)}
                  currency={source.currency}
                  className="font-mono tabular-nums"
                />
              </ReviewRow>
            )}
          </dl>
          <p className="text-sm text-text-secondary">
            {t('conversion.preserved', {
              transactions: source.transactionCount,
              valuations: source.valuationCount,
            })}
          </p>
          <p className="text-sm text-text-secondary">{t('conversion.noPayment')}</p>
          <p className="text-sm text-text-secondary">
            {t(source.closesSourceAccount ? 'conversion.closeAccount' : 'conversion.keepAccount', {
              account: source.accountName,
            })}
          </p>
        </>
      )}
      {pending && error && (
        <p className="text-sm text-text-secondary">{t('conversion.retrySame')}</p>
      )}
      {error && (
        <p role="alert" className="text-sm text-error">
          {error}
        </p>
      )}
      <div className="flex flex-col-reverse gap-2 border-t border-border pt-4 sm:flex-row sm:items-center">
        <Button type="button" variant="ghost" onClick={cancel} disabled={mutation.isPending}>
          {t('form.cancel')}
        </Button>
        <Button
          type="button"
          variant="outline"
          className="sm:ml-auto"
          disabled={mutation.isPending || !!pending}
          onClick={() => (review ? setReview(false) : backToSource())}
        >
          {t('wizard.back')}
        </Button>
        <Button type="submit" isLoading={mutation.isPending} disabled={mutation.isPending}>
          {t(review ? 'conversion.confirm' : 'wizard.next')}
        </Button>
      </div>
    </form>
  );
}

function ReviewRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="grid gap-1 sm:grid-cols-[10rem_minmax(0,1fr)] sm:gap-4">
      <dt className="text-text-secondary">{label}</dt>
      <dd className="min-w-0 break-words font-medium">{children}</dd>
    </div>
  );
}
