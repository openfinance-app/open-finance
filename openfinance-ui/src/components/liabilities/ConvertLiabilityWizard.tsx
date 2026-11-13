import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { isAxiosError } from 'axios';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { NumberInput } from '@/components/ui/NumberInput';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { FormDialog } from '@/components/ui/FormDialog';
import {
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/Dialog';
import { useFormDraft } from '@/hooks/useFormDraft';
import { useProperties } from '@/hooks/useRealEstate';
import { useDateFormatter } from '@/hooks/useDateFormatter';
import {
  useConvertLiability,
  useLiabilityConversionCandidates,
  useLiabilityConversionEnabled,
} from '@/hooks/useLiabilityConversion';
import type {
  LiabilityConversionCandidate,
  LiabilityConversionRequest,
} from '@/hooks/useLiabilityConversion';
import type { LiabilityType } from '@/types/liability';
import { format } from 'date-fns';
import { isValidDecimalString } from '@/utils/money';
import { decimalField, ExactDecimal } from '@/utils/exact-decimal';
import { apiErrorMessage } from '@/utils/api-error';
import { getMonetaryScale } from '@/utils/currency';
import { OptionalDetails } from '@/components/ui/OptionalDetails';
import {
  ConversionPaymentSummary,
  LiabilityConversionPayments,
} from '@/components/liabilities/LiabilityConversionPayments';
import {
  conversionPaymentRequest,
  conversionPaymentTotals,
  paymentSelectionComplete,
  type SelectedConversionPayment,
} from '@/components/liabilities/liability-conversion-payments';
import {
  mortgagePresetTerms,
  matchesMortgagePreset,
  matchesMortgagePresetProperty,
} from '@/components/liabilities/imported-mortgage-preset';

interface Props {
  initialAccountId?: number;
  onClose: () => void;
  onConverted: (liabilityId: number) => void;
}

export function ConvertLiabilityWizard(props: Props) {
  const { t } = useTranslation('liabilities');
  const conversionEnabled = useLiabilityConversionEnabled();
  if (!conversionEnabled) return null;
  return (
    <FormDialog open onOpenChange={open => !open && props.onClose()}>
      <DialogContent className="sm:max-w-[720px] max-h-[90dvh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="pr-6 break-words">{t('conversion.title')}</DialogTitle>
          <DialogDescription>{t('conversion.description')}</DialogDescription>
        </DialogHeader>
        <ConversionFlow {...props} />
      </DialogContent>
    </FormDialog>
  );
}

function Steps({ step }: { step: number }) {
  const { t } = useTranslation('liabilities');
  return (
    <ol aria-label={t('conversion.stepsLabel')} className="flex flex-wrap gap-x-5 gap-y-2 text-sm">
      {['source', 'details', 'payments', 'review'].map((key, index) => (
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

function ConversionFlow({ initialAccountId, onClose, onConverted }: Props) {
  const { t } = useTranslation('liabilities');
  const query = useLiabilityConversionCandidates();
  const [selected, setSelected] = useState(initialAccountId);
  const [search, setSearch] = useState('');
  const [source, setSource] = useState<LiabilityConversionCandidate | null>(null);
  const filtered = (query.data ?? []).filter(item =>
    `${item.name} ${item.currency}`.toLocaleLowerCase().includes(search.toLocaleLowerCase())
  );
  const candidate = query.data?.find(item => item.accountId === selected);

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
            id="liability-source-search"
            label={t('conversion.search')}
            value={search}
            onChange={event => setSearch(event.target.value)}
          />
          <fieldset className="min-w-0 space-y-2">
            <legend className="mb-2 text-sm font-medium">{t('conversion.selectSource')}</legend>
            <div className="max-h-64 overflow-y-auto space-y-2 p-1">
              {filtered.map(item => (
                <label
                  key={item.accountId}
                  className={`flex min-w-0 cursor-pointer items-center gap-3 rounded-lg border p-3 hover:bg-surface-elevated ${selected === item.accountId ? 'border-primary' : 'border-border'}`}
                >
                  <input
                    type="radio"
                    name="liability-source"
                    value={item.accountId}
                    checked={selected === item.accountId}
                    onChange={() => setSelected(item.accountId)}
                    className="h-4 w-4 shrink-0 accent-primary focus-visible:ring-2 focus-visible:ring-primary"
                  />
                  <span className="min-w-0 flex-1">
                    <span className="block break-words font-medium">{item.name}</span>
                    <span className="block break-words text-sm text-text-secondary">
                      {item.currency}
                    </span>
                    <ConvertedAmount
                      amount={item.outstandingBalance}
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
  source: LiabilityConversionCandidate;
  onBack: () => void;
  onClose: () => void;
  onConverted: (id: number) => void;
}) {
  const { t, i18n } = useTranslation('liabilities');
  const { date } = useDateFormatter();
  const [step, setStep] = useState(1);
  const review = step === 3;
  const [selectedPayments, setSelectedPayments] = useState<SelectedConversionPayment[]>([]);
  const [paymentsReady, setPaymentsReady] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState<LiabilityConversionRequest | null>(null);
  const [operationId] = useState(() => crypto.randomUUID());
  const mutation = useConvertLiability();
  const propertiesQuery = useProperties();
  const properties = useMemo(() => propertiesQuery.data ?? [], [propertiesQuery.data]);
  const preset = matchesMortgagePreset(source);
  const propertySuggested = useRef(false);
  const heading = useRef<HTMLHeadingElement>(null);
  const today = format(new Date(), 'yyyy-MM-dd');
  const debt = decimalField(source, 'outstandingBalance');
  const types = [
    'LOAN',
    'MORTGAGE',
    'PERSONAL_LOAN',
    'AUTO_LOAN',
    'STUDENT_LOAN',
    'CREDIT_CARD',
    'OTHER',
  ] as const;
  const money = z
    .string()
    .refine(
      value => isValidDecimalString(value) && new ExactDecimal(value).gte(0),
      t('conversion.invalidAmount')
    );
  const schema = z
    .object({
      name: z.string().trim().min(1, t('conversion.nameRequired')).max(255),
      type: z.enum(types),
      principal: money,
      fundedPrincipal: money,
      startDate: z
        .string()
        .regex(/^\d{4}-\d{2}-\d{2}$/, t('conversion.invalidDate'))
        .refine(value => value <= today, t('conversion.invalidDate')),
      interestRate: z
        .string()
        .refine(
          value =>
            value === '' ||
            (isValidDecimalString(value) &&
              new ExactDecimal(value).gte(0) &&
              new ExactDecimal(value).lte(100)),
          t('conversion.invalidRate')
        ),
      minimumPayment: z
        .string()
        .refine(
          value => value === '' || (isValidDecimalString(value) && new ExactDecimal(value).gte(0)),
          t('conversion.invalidAmount')
        ),
      insurancePercentage: z
        .string()
        .refine(
          value =>
            value === '' ||
            (isValidDecimalString(value) &&
              new ExactDecimal(value).gte(0) &&
              new ExactDecimal(value).lte(100)),
          t('conversion.invalidRate')
        ),
      additionalFees: z
        .string()
        .refine(
          value => value === '' || (isValidDecimalString(value) && new ExactDecimal(value).gte(0)),
          t('conversion.invalidAmount')
        ),
      notes: z.string().max(1000, t('form.validation.notesTooLong')),
      endDate: z.string(),
      amortizationStartDate: z.string(),
      realEstateId: z.string(),
    })
    .superRefine((values, context) => {
      if (preset && values.type === 'MORTGAGE' && !values.realEstateId)
        context.addIssue({
          code: 'custom',
          path: ['realEstateId'],
          message: t('conversion.propertyRequired'),
        });
      if (
        isValidDecimalString(values.fundedPrincipal) &&
        new ExactDecimal(values.fundedPrincipal).lt(debt)
      )
        context.addIssue({
          code: 'custom',
          path: ['fundedPrincipal'],
          message: t('conversion.fundedMinimum'),
        });
      if (
        isValidDecimalString(values.principal) &&
        isValidDecimalString(values.fundedPrincipal) &&
        new ExactDecimal(values.principal).lt(values.fundedPrincipal)
      )
        context.addIssue({
          code: 'custom',
          path: ['principal'],
          message: t('conversion.principalMinimum'),
        });
      if (values.endDate && values.endDate < values.startDate)
        context.addIssue({
          code: 'custom',
          path: ['endDate'],
          message: t('conversion.invalidEndDate'),
        });
      if (
        values.amortizationStartDate &&
        (values.amortizationStartDate < values.startDate ||
          (values.endDate && values.amortizationStartDate > values.endDate))
      )
        context.addIssue({
          code: 'custom',
          path: ['amortizationStartDate'],
          message: t('form.validation.amortizationDateOrder'),
        });
    });
  const {
    register,
    control,
    handleSubmit,
    setValue,
    getValues,
    formState: { errors, isDirty },
  } = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: source.name,
      type: 'LOAN',
      principal: debt,
      fundedPrincipal: debt,
      startDate: source.startDate,
      interestRate: '',
      minimumPayment: '',
      endDate: '',
      amortizationStartDate: '',
      realEstateId: '',
      insurancePercentage: '',
      additionalFees: '',
      notes: '',
      ...(preset ? mortgagePresetTerms(i18n.resolvedLanguage || i18n.language) : {}),
    },
  });
  const values = useWatch({ control });
  const cancel = useFormDraft(isDirty || step > 1 || pending !== null, mutation.isPending, onClose);
  const back = useFormDraft(isDirty, mutation.isPending, onBack);
  useEffect(() => {
    if (preset && propertiesQuery.isSuccess && !propertySuggested.current) {
      propertySuggested.current = true;
      const matches = properties.filter(
        property =>
          matchesMortgagePresetProperty(property.name) &&
          property.isActive !== false &&
          property.acquisitionType !== 'PLANNED' &&
          !property.soldDate &&
          !property.mortgageId
      );
      if (matches.length === 1 && !getValues('realEstateId'))
        setValue('realEstateId', String(matches[0].id));
    }
  }, [preset, properties, propertiesQuery.isSuccess, getValues, setValue]);
  useEffect(() => {
    heading.current?.focus({ preventScroll: true });
    heading.current?.closest('[role="dialog"]')?.scrollTo({ top: 0 });
  }, [step]);
  const submit = handleSubmit(async details => {
    setError(null);
    if (step === 1) {
      setStep(2);
      return;
    }
    if (!selectedPayments.every(paymentSelectionComplete)) {
      setError(t('conversion.payments.incomplete'));
      setStep(2);
      return;
    }
    if (
      conversionPaymentTotals(selectedPayments).PRINCIPAL.gt(
        new ExactDecimal(details.fundedPrincipal).minus(debt)
      )
    ) {
      setError(t('conversion.payments.principalExceeded'));
      setStep(2);
      return;
    }
    if (step === 2) {
      if (!paymentsReady) return;
      setStep(3);
      return;
    }
    if (mutation.isPending) return;
    const request = pending ?? {
      operationId,
      accountId: source.accountId,
      sourceRevision: source.sourceRevision,
      fundedPrincipal: details.fundedPrincipal,
      payments: selectedPayments.map(conversionPaymentRequest),
      liability: {
        name: details.name,
        type: details.type as LiabilityType,
        principal: details.principal,
        currentBalance: debt,
        currency: source.currency,
        realEstateId:
          details.type === 'MORTGAGE' && details.realEstateId
            ? Number(details.realEstateId)
            : undefined,
        startDate: details.startDate,
        endDate: details.endDate || undefined,
        amortizationStartDate: details.amortizationStartDate || undefined,
        interestRate: details.interestRate ? Number(details.interestRate) : undefined,
        minimumPayment: details.minimumPayment || undefined,
        insurancePercentage: details.insurancePercentage
          ? Number(details.insurancePercentage)
          : undefined,
        additionalFees: details.additionalFees || undefined,
        notes: details.notes || undefined,
      },
    };
    setPending(request);
    setError(null);
    try {
      onConverted((await mutation.mutateAsync(request)).id);
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
      <Steps step={step} />
      <h3 ref={heading} tabIndex={-1} className="font-semibold focus:outline-none">
        {t(
          review
            ? 'conversion.reviewTitle'
            : step === 2
              ? 'conversion.payments.title'
              : 'conversion.detailsTitle'
        )}
      </h3>
      <div className="flex flex-wrap items-baseline justify-between gap-2 border-b border-border pb-4">
        <span className="text-sm text-text-secondary">{t('conversion.outstanding')}</span>
        <ConvertedAmount
          amount={source.outstandingBalance}
          currency={source.currency}
          className="font-mono font-semibold tabular-nums"
        />
      </div>
      <div hidden={step !== 1} className="space-y-4">
        {preset && <p className="text-sm text-text-secondary">{t('conversion.mortgagePreset')}</p>}
        <Input
          id="liability-conversion-name"
          label={t('conversion.name')}
          required
          maxLength={255}
          {...register('name')}
          error={errors.name?.message}
        />
        <div>
          <label htmlFor="liability-conversion-type" className="mb-1.5 block text-sm font-medium">
            {t('conversion.type')}
          </label>
          <select
            id="liability-conversion-type"
            {...register('type')}
            className="h-11 w-full rounded-lg border border-border bg-background px-3 text-sm focus-visible:ring-2 focus-visible:ring-primary"
          >
            {types.map(type => (
              <option key={type} value={type}>
                {t(`types.${type}`)}
              </option>
            ))}
          </select>
        </div>
        {values.type === 'MORTGAGE' && (
          <div>
            <label
              htmlFor="liability-conversion-property"
              className="mb-1.5 block text-sm font-medium"
            >
              {t('form.linkToProperty')} {preset ? '*' : `(${t('form.optional')})`}
            </label>
            <select
              id="liability-conversion-property"
              {...register('realEstateId')}
              required={preset}
              aria-invalid={!!errors.realEstateId}
              className="h-11 w-full rounded-lg border border-border bg-background px-3 text-sm focus-visible:ring-2 focus-visible:ring-primary"
            >
              <option value="">{t('form.noProperty')}</option>
              {properties
                .filter(
                  p =>
                    p.isActive !== false &&
                    p.acquisitionType !== 'PLANNED' &&
                    !p.soldDate &&
                    !p.mortgageId
                )
                .map(property => (
                  <option key={property.id} value={property.id}>
                    {property.name}
                  </option>
                ))}
            </select>
            {errors.realEstateId && (
              <p role="alert" className="text-sm text-error">
                {errors.realEstateId.message}
              </p>
            )}
            <p className="mt-2 text-sm text-text-secondary">{t('conversion.propertyHint')}</p>
            {propertiesQuery.isError && (
              <p role="alert" className="text-sm text-error">
                {t('conversion.propertyError')}
              </p>
            )}
          </div>
        )}
        <p className="text-sm text-text-secondary">{t('conversion.termsHint')}</p>
        <div className="grid gap-4 sm:grid-cols-2">
          {(['principal', 'fundedPrincipal'] as const).map(name => (
            <div key={name}>
              <label
                htmlFor={`liability-conversion-${name}`}
                className="mb-1.5 block text-sm font-medium"
              >
                {t(`conversion.${name}`)} ({source.currency})
              </label>
              <Controller
                name={name}
                control={control}
                render={({ field }) => (
                  <NumberInput
                    id={`liability-conversion-${name}`}
                    value={field.value}
                    onChange={field.onChange}
                    min={0}
                    error={errors[name]?.message}
                  />
                )}
              />
            </div>
          ))}
          <Input
            id="liability-conversion-start"
            label={t('conversion.startDate')}
            type="date"
            required
            max={today}
            {...register('startDate')}
            error={errors.startDate?.message}
          />
          <Input
            id="liability-conversion-end"
            label={t('conversion.endDate')}
            type="date"
            min={values.startDate}
            {...register('endDate')}
            error={errors.endDate?.message}
          />
          <Input
            id="liability-conversion-amortization"
            label={t('form.amortizationStartDate')}
            type="date"
            min={values.startDate}
            max={values.endDate || undefined}
            {...register('amortizationStartDate')}
            error={errors.amortizationStartDate?.message}
            helperText={t('form.amortizationStartDateHint')}
          />
          {(['interestRate', 'minimumPayment', 'insurancePercentage'] as const).map(name => (
            <div key={name}>
              <label
                htmlFor={`liability-conversion-${name}`}
                className="mb-1.5 block text-sm font-medium"
              >
                {name === 'insurancePercentage'
                  ? t('form.insurancePercentage')
                  : `${t(`conversion.${name}`)} (${name === 'interestRate' ? '%' : source.currency})`}
              </label>
              <Controller
                name={name}
                control={control}
                render={({ field }) => (
                  <NumberInput
                    id={`liability-conversion-${name}`}
                    value={field.value}
                    onChange={field.onChange}
                    min={0}
                    max={name !== 'minimumPayment' ? 100 : undefined}
                    error={errors[name]?.message}
                  />
                )}
              />
            </div>
          ))}
        </div>
        <OptionalDetails
          title={t('conversion.otherDetails')}
          hasErrors={!!errors.additionalFees || !!errors.notes}
        >
          <label htmlFor="liability-conversion-fees" className="block text-sm font-medium">
            {t('form.additionalFees')} ({source.currency})
          </label>
          <Controller
            name="additionalFees"
            control={control}
            render={({ field }) => (
              <NumberInput
                id="liability-conversion-fees"
                value={field.value}
                onChange={field.onChange}
                min={0}
                error={errors.additionalFees?.message}
              />
            )}
          />
          <p className="text-sm text-text-secondary">{t('conversion.feesHint')}</p>
          <label htmlFor="liability-conversion-notes" className="block text-sm font-medium">
            {t('form.notes')}
          </label>
          <textarea
            id="liability-conversion-notes"
            rows={6}
            maxLength={1000}
            {...register('notes')}
            className="w-full resize-y rounded-lg border border-border bg-background p-3 text-base md:text-sm"
            aria-invalid={!!errors.notes}
          />
          {errors.notes && (
            <p role="alert" className="text-sm text-error">
              {errors.notes.message}
            </p>
          )}
        </OptionalDetails>
      </div>
      <div hidden={step !== 2}>
        <LiabilityConversionPayments
          source={source}
          selected={selectedPayments}
          onChange={setSelectedPayments}
          active={step === 2}
          onReadyChange={setPaymentsReady}
        />
      </div>
      {review && (
        <div className="space-y-4">
          <dl className="space-y-3 text-sm">
            <ReviewRow label={t('conversion.source')}>{source.name}</ReviewRow>
            <ReviewRow label={t('conversion.name')}>{values.name}</ReviewRow>
            <ReviewRow label={t('conversion.type')}>{t(`types.${values.type}`)}</ReviewRow>
            <ReviewRow label={t('conversion.principal')}>
              <ConvertedAmount
                className="font-mono tabular-nums"
                amount={Number(values.principal)}
                currency={source.currency}
              />
            </ReviewRow>
            <ReviewRow label={t('conversion.fundedPrincipal')}>
              <ConvertedAmount
                className="font-mono tabular-nums"
                amount={Number(values.fundedPrincipal)}
                currency={source.currency}
              />
            </ReviewRow>
            <ReviewRow label={t('conversion.undrawn')}>
              <ConvertedAmount
                amount={new ExactDecimal(values.principal || 0)
                  .minus(values.fundedPrincipal || 0)
                  .toNumber()}
                currency={source.currency}
                className="font-mono tabular-nums"
              />
            </ReviewRow>
            {values.type === 'MORTGAGE' && values.realEstateId && (
              <ReviewRow label={t('form.linkToProperty')}>
                {properties.find(p => p.id === Number(values.realEstateId))?.name}
              </ReviewRow>
            )}
            <ReviewRow label={t('conversion.startDate')}>{date(values.startDate!)}</ReviewRow>
            {values.endDate && (
              <ReviewRow label={t('conversion.endDate')}>{date(values.endDate)}</ReviewRow>
            )}
            {values.interestRate && (
              <ReviewRow label={t('conversion.interestRate')}>{values.interestRate}%</ReviewRow>
            )}
            {values.amortizationStartDate && (
              <ReviewRow label={t('form.amortizationStartDate')}>
                {date(values.amortizationStartDate)}
              </ReviewRow>
            )}
            {values.minimumPayment && (
              <ReviewRow label={t('conversion.minimumPayment')}>
                <ConvertedAmount
                  className="font-mono tabular-nums"
                  amount={Number(values.minimumPayment)}
                  currency={source.currency}
                />
              </ReviewRow>
            )}
            {values.insurancePercentage && (
              <ReviewRow label={t('form.insurancePercentage')}>
                {values.insurancePercentage}%
              </ReviewRow>
            )}
            {values.insurancePercentage && values.principal && (
              <ReviewRow label={t('conversion.monthlyInsurance')}>
                <ConvertedAmount
                  amount={new ExactDecimal(values.principal)
                    .times(values.insurancePercentage)
                    .div(1200)
                    .toDecimalPlaces(getMonetaryScale(source.currency))
                    .toNumber()}
                  currency={source.currency}
                  className="font-mono tabular-nums"
                />
              </ReviewRow>
            )}
            {values.minimumPayment && values.insurancePercentage && values.principal && (
              <ReviewRow label={t('conversion.paymentWithInsurance')}>
                <ConvertedAmount
                  amount={new ExactDecimal(values.principal)
                    .times(values.insurancePercentage)
                    .div(1200)
                    .toDecimalPlaces(getMonetaryScale(source.currency))
                    .plus(values.minimumPayment)
                    .toNumber()}
                  currency={source.currency}
                  className="font-mono tabular-nums"
                />
              </ReviewRow>
            )}
            {values.additionalFees && (
              <ReviewRow label={t('form.additionalFees')}>
                <ConvertedAmount
                  amount={Number(values.additionalFees)}
                  currency={source.currency}
                  className="font-mono tabular-nums"
                />
              </ReviewRow>
            )}
            {values.notes && <ReviewRow label={t('form.notes')}>{values.notes}</ReviewRow>}
          </dl>
          <ConversionPaymentSummary payments={selectedPayments} currency={source.currency} />
          <p className="text-sm text-text-secondary">{t('conversion.payments.effect')}</p>
          <p className="text-sm text-text-secondary">
            {t('conversion.effect', { count: source.transactionCount })}
          </p>
        </div>
      )}
      {error && (
        <p role="alert" className="text-sm text-error">
          {error}
        </p>
      )}
      {pending && !mutation.isPending && (
        <p role="status" className="text-sm text-text-secondary">
          {t('conversion.retryHint')}
        </p>
      )}
      <div className="sticky -bottom-6 z-10 flex flex-wrap gap-2 border-t border-border bg-surface pt-4 pb-6">
        <Button type="button" variant="ghost" onClick={cancel} disabled={mutation.isPending}>
          {t('form.cancel')}
        </Button>
        <Button
          type="button"
          variant="outline"
          disabled={mutation.isPending || pending !== null}
          onClick={() => {
            setError(null);
            if (step > 1) setStep(step - 1);
            else back();
          }}
        >
          {t('conversion.back')}
        </Button>
        <Button
          type="submit"
          className="ml-auto"
          disabled={mutation.isPending || (step === 2 && !paymentsReady)}
        >
          {t(
            mutation.isPending
              ? 'conversion.converting'
              : pending
                ? 'conversion.retry'
                : review
                  ? 'conversion.confirm'
                  : step === 1
                    ? 'conversion.payments.next'
                    : 'conversion.review'
          )}
        </Button>
      </div>
    </form>
  );
}

function ReviewRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-1 sm:flex-row sm:justify-between sm:gap-4">
      <dt className="text-text-secondary">{label}</dt>
      <dd className="break-words font-medium sm:max-w-[65%] sm:text-right">{children}</dd>
    </div>
  );
}
