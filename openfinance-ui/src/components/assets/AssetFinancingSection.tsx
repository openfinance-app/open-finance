import { useEffect, useId, useState } from 'react';
import { Link } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { isAxiosError } from 'axios';
import { Link2, Plus } from 'lucide-react';
import { useAssets } from '@/hooks/useAssets';
import { useLiabilities, useCreateLiability } from '@/hooks/useLiabilities';
import { LiabilityForm } from '@/components/liabilities/LiabilityForm';
import apiClient from '@/services/apiClient';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { Button } from '@/components/ui/Button';
import { HelpTooltip } from '@/components/ui/HelpTooltip';
import { Input } from '@/components/ui/Input';
import { SimpleSelect } from '@/components/ui/SimpleSelect';
import { sum, subtract } from '@/utils/money';
import type { LiabilityRequest } from '@/types/liability';

interface FinancingLink {
  assetId: number;
  liabilityId?: number;
  relationship: 'FINANCING' | 'COLLATERAL';
  allocationPercentage: number;
  assetName?: string;
  liabilityName?: string;
  allocatedBalance?: number;
  assetCurrency?: string;
}

export function AssetFinancingSection({
  assetId,
  liabilityId,
}: {
  assetId?: number;
  liabilityId?: number;
}) {
  const { t } = useTranslation('assets');
  const id = useId();
  const client = useQueryClient();
  const assets = useAssets();
  const liabilities = useLiabilities();
  const createLoan = useCreateLiability();
  const [linking, setLinking] = useState(false);
  const [creatingLoan, setCreatingLoan] = useState(false);
  const [chosenLoan, setChosenLoan] = useState<number | undefined>(liabilityId);
  const [draft, setDraft] = useState<FinancingLink[] | null>(null);
  useEffect(() => {
    setChosenLoan(liabilityId);
    setDraft(null);
    setLinking(false);
    setCreatingLoan(false);
  }, [liabilityId, assetId]);

  // Keep the item's relationships visible while a separate loan allocation is edited.
  const linked = useQuery<FinancingLink[]>({
    queryKey: ['assetFinancing', liabilityId ? 'liability' : 'asset', liabilityId ?? assetId],
    queryFn: async () =>
      (
        await apiClient.get<FinancingLink[]>(
          liabilityId ? `/liabilities/${liabilityId}/asset-links` : `/assets/${assetId}/liabilities`
        )
      ).data,
    enabled: liabilityId != null || assetId != null,
  });
  const loanLinks = useQuery<FinancingLink[]>({
    queryKey: ['assetFinancing', 'liability', chosenLoan],
    queryFn: async () =>
      (await apiClient.get<FinancingLink[]>(`/liabilities/${chosenLoan}/asset-links`)).data,
    enabled: linking && chosenLoan != null,
  });
  useEffect(() => {
    if (!linking || loanLinks.isFetching || !loanLinks.data || draft !== null) return;
    const rows = loanLinks.data.map(row => ({ ...row }));
    if (assetId && !rows.some(row => row.assetId === assetId && row.relationship === 'FINANCING')) {
      const allocated = sum(
        rows.filter(row => row.relationship === 'FINANCING').map(row => row.allocationPercentage)
      );
      rows.push({
        assetId,
        relationship: 'FINANCING',
        allocationPercentage: Math.max(subtract(100, allocated), 0),
      });
    }
    setDraft(rows);
  }, [linking, loanLinks.data, loanLinks.isFetching, draft, assetId]);

  const closeEditor = () => {
    setLinking(false);
    setDraft(null);
    setChosenLoan(liabilityId);
  };
  const save = useMutation({
    mutationFn: async (rows: FinancingLink[]) =>
      apiClient.put(
        `/liabilities/${chosenLoan}/asset-links`,
        rows.map(({ assetId: id, relationship, allocationPercentage }) => ({
          assetId: id,
          relationship,
          allocationPercentage,
        }))
      ),
    onSuccess: async () => {
      closeEditor();
      await Promise.all(
        [
          'assetFinancing',
          'liabilities',
          'realEstate',
          'assets',
          'dashboard',
          'networth',
          'history',
        ].map(key => client.invalidateQueries({ queryKey: [key] }))
      );
    },
  });
  const startEditing = (loanId?: number) => {
    save.reset();
    setChosenLoan(loanId);
    setDraft(null);
    setLinking(true);
  };
  const addLoan = async (data: LiabilityRequest) => {
    try {
      const loan = await createLoan.mutateAsync(data);
      setCreatingLoan(false);
      startEditing(loan.id);
    } catch {
      // Retain the form and display the mutation error below it.
    }
  };
  const update = (index: number, patch: Partial<FinancingLink>) =>
    setDraft(rows => rows!.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  const allocated = sum(
    (draft ?? [])
      .filter(row => row.relationship === 'FINANCING')
      .map(row => row.allocationPercentage)
  );
  const duplicate = (draft ?? []).some((row, i, rows) =>
    rows.some(
      (other, j) =>
        i !== j && row.assetId === other.assetId && row.relationship === other.relationship
    )
  );
  const invalid =
    allocated > 100 ||
    duplicate ||
    (draft ?? []).some(
      row =>
        !row.assetId ||
        !Number.isFinite(row.allocationPercentage) ||
        (row.relationship === 'FINANCING' &&
          (row.allocationPercentage <= 0 || row.allocationPercentage > 100))
    );
  const asset = assets.data?.find(item => item.id === assetId);
  const allocatedDebt = sum((linked.data ?? []).map(row => row.allocatedBalance ?? 0));
  const selectedLoan = liabilities.data?.find(loan => loan.id === chosenLoan);
  const availableAssets = (assets.data ?? []).filter(
    item => item.isActive !== false || draft?.some(row => row.assetId === item.id)
  );

  return (
    <section className="space-y-4 rounded-xl border border-border bg-surface p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <h3 className="flex items-center gap-2 font-semibold">
            <Link2 className="h-4 w-4" aria-hidden="true" />
            {t(liabilityId ? 'financing.linkedAssets' : 'financing.title')}
          </h3>
          <HelpTooltip text={t('financing.hint')} />
        </div>
        {!linking && !creatingLoan && (
          <Button
            variant={liabilityId ? 'secondary' : 'outline'}
            size="sm"
            onClick={() => startEditing(liabilityId)}
          >
            <Plus className="mr-2 h-4 w-4" aria-hidden="true" />
            {t(liabilityId ? 'financing.edit' : 'financing.linkLoan')}
          </Button>
        )}
      </div>
      {asset && asset.type !== 'REAL_ESTATE' && linked.data && !linked.isError && (
        <dl className="grid grid-cols-1 gap-3 rounded-lg bg-background p-3 text-sm sm:grid-cols-3">
          {(
            [
              ['value', asset.totalValue],
              ['debt', allocatedDebt],
              ['equity', subtract(asset.totalValue, allocatedDebt)],
            ] as const
          ).map(([key, amount]) => (
            <div key={key}>
              <dt className="text-text-secondary">{t(`financing.${key}`)}</dt>
              <dd className="mt-1 font-semibold">
                <ConvertedAmount amount={amount} currency={asset.currency} inline />
              </dd>
            </div>
          ))}
        </dl>
      )}
      {linked.isLoading ? (
        <p role="status">{t('financing.loading')}</p>
      ) : linked.error ? (
        <p role="alert" className="text-error">
          {t('financing.error')}
        </p>
      ) : linked.data?.length ? (
        <ul className="divide-y divide-border">
          {linked.data.map(row => (
            <li
              key={`${row.assetId}-${row.liabilityId}-${row.relationship}`}
              className="flex flex-wrap items-center justify-between gap-3 py-3 text-sm"
            >
              <div>
                <Link
                  to={
                    liabilityId
                      ? `/assets?highlight=${row.assetId}`
                      : `/liabilities?highlight=${row.liabilityId}`
                  }
                  className="font-medium text-primary hover:underline"
                >
                  {liabilityId ? row.assetName : row.liabilityName}
                </Link>
                <p className="text-xs text-text-secondary">
                  {t(`financing.${row.relationship}`)}
                  {row.relationship === 'FINANCING' && ` · ${row.allocationPercentage}%`}
                </p>
              </div>
              <div className="flex items-center gap-3">
                {row.relationship === 'FINANCING' && (
                  <ConvertedAmount
                    amount={row.allocatedBalance ?? 0}
                    currency={
                      row.assetCurrency ?? asset?.currency ?? selectedLoan?.currency ?? 'EUR'
                    }
                    inline
                  />
                )}
                {!linking && !creatingLoan && (
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => startEditing(row.liabilityId ?? liabilityId)}
                  >
                    {t('financing.edit')}
                  </Button>
                )}
              </div>
            </li>
          ))}
        </ul>
      ) : (
        <p className="rounded-lg bg-background p-4 text-sm text-text-secondary">
          {t('financing.empty')}
        </p>
      )}

      {creatingLoan && (
        <div className="space-y-3 border-t border-border pt-4">
          <h4 className="font-medium">{t('financing.createLoan')}</h4>
          <LiabilityForm
            showPropertyLink={false}
            initialCurrency={asset?.currency}
            initialType={
              asset?.type === 'REAL_ESTATE'
                ? 'MORTGAGE'
                : asset?.type === 'VEHICLE'
                  ? 'AUTO_LOAN'
                  : 'LOAN'
            }
            onSubmit={addLoan}
            onCancel={() => setCreatingLoan(false)}
            isLoading={createLoan.isPending}
          />
          {createLoan.error && (
            <p role="alert" className="text-error">
              {t('financing.error')}
            </p>
          )}
        </div>
      )}
      {linking && (
        <div className="space-y-4 border-t border-border pt-4">
          {!liabilityId && (
            <div>
              <label className="block text-sm font-medium">
                {t('financing.loan')}
                <SimpleSelect
                  value={chosenLoan ?? ''}
                  disabled={save.isPending}
                  onChange={e => {
                    setChosenLoan(Number(e.target.value) || undefined);
                    setDraft(null);
                    save.reset();
                  }}
                  className="mt-1.5"
                >
                  <option value="">{t('financing.selectLoan')}</option>
                  {liabilities.data?.map(loan => (
                    <option key={loan.id} value={loan.id}>
                      {loan.name} · {loan.currency}
                    </option>
                  ))}
                </SimpleSelect>
              </label>
              {liabilities.isError && (
                <p role="alert" className="text-error">
                  {t('financing.error')}
                </p>
              )}
              <Button
                variant="ghost"
                size="sm"
                onClick={() => {
                  closeEditor();
                  createLoan.reset();
                  setCreatingLoan(true);
                }}
              >
                {t('financing.createLoan')}
              </Button>
            </div>
          )}
          {chosenLoan && loanLinks.isLoading && <p role="status">{t('financing.loading')}</p>}
          {chosenLoan && loanLinks.isError && (
            <p role="alert" className="text-error">
              {t('financing.error')}
            </p>
          )}
          {draft !== null && chosenLoan && (
            <>
              <fieldset disabled={save.isPending} className="space-y-3">
                {draft.map((row, index) => (
                  <div
                    className="flex flex-wrap items-end gap-2 rounded-lg border border-border p-3"
                    key={index}
                  >
                    <label className="min-w-40 flex-1 text-sm">
                      {t('financing.asset')}
                      <SimpleSelect
                        value={row.assetId}
                        onChange={e => update(index, { assetId: Number(e.target.value) })}
                        className="mt-1"
                      >
                        {availableAssets.map(item => (
                          <option key={item.id} value={item.id}>
                            {item.name}
                          </option>
                        ))}
                      </SimpleSelect>
                    </label>
                    <div className="text-sm">
                      <div className="flex items-center gap-2">
                        <label htmlFor={`${id}-relationship-${index}`}>
                          {t('financing.relationship')}
                        </label>
                        <HelpTooltip text={t('financing.collateralHint')} />
                      </div>
                      <SimpleSelect
                        id={`${id}-relationship-${index}`}
                        value={row.relationship}
                        onChange={e =>
                          update(index, {
                            relationship: e.target.value as FinancingLink['relationship'],
                            allocationPercentage:
                              e.target.value === 'FINANCING'
                                ? Math.max(subtract(100, allocated), 0)
                                : 0,
                          })
                        }
                        className="mt-1"
                      >
                        <option value="FINANCING">{t('financing.FINANCING')}</option>
                        <option value="COLLATERAL">{t('financing.COLLATERAL')}</option>
                      </SimpleSelect>
                    </div>
                    {row.relationship === 'FINANCING' && (
                      <label className="w-24 text-sm">
                        {t('financing.share')}
                        <Input
                          type="number"
                          min="0.01"
                          max="100"
                          step="0.01"
                          value={row.allocationPercentage}
                          onChange={e =>
                            update(index, { allocationPercentage: Number(e.target.value) })
                          }
                          className="mt-1"
                        />
                      </label>
                    )}
                    <Button
                      variant="ghost"
                      aria-label={t('financing.removeNamed', {
                        name:
                          assets.data?.find(item => item.id === row.assetId)?.name ??
                          row.assetName ??
                          '',
                      })}
                      onClick={() => setDraft(draft.filter((_, i) => i !== index))}
                    >
                      {t('financing.remove')}
                    </Button>
                  </div>
                ))}
                <p
                  className={invalid ? 'text-sm text-error' : 'text-sm text-text-secondary'}
                  role="status"
                >
                  {t('financing.allocated', { percentage: allocated })}
                  <HelpTooltip
                    className="ml-2"
                    text={t('financing.distributionHint', { name: selectedLoan?.name ?? '' })}
                  />
                </p>
                {duplicate && (
                  <p role="alert" className="text-sm text-error">
                    {t('financing.duplicate')}
                  </p>
                )}
                {draft.some(
                  row => row.relationship === 'FINANCING' && row.allocationPercentage <= 0
                ) && (
                  <p role="alert" className="text-sm text-error">
                    {t('financing.shareRequired')}
                  </p>
                )}
                <Button
                  variant="secondary"
                  disabled={
                    !availableAssets.some(
                      item =>
                        !draft.some(
                          row => row.assetId === item.id && row.relationship === 'FINANCING'
                        )
                    )
                  }
                  onClick={() => {
                    const next = availableAssets.find(
                      item =>
                        !draft.some(
                          row => row.assetId === item.id && row.relationship === 'FINANCING'
                        )
                    );
                    if (next)
                      setDraft([
                        ...draft,
                        {
                          assetId: next.id,
                          relationship: 'FINANCING',
                          allocationPercentage: Math.max(subtract(100, allocated), 0),
                        },
                      ]);
                  }}
                >
                  {t('financing.add')}
                </Button>
              </fieldset>
            </>
          )}
          {save.error && (
            <p role="alert" className="text-sm text-error">
              {isAxiosError<{ message?: string }>(save.error) && save.error.response?.data.message
                ? save.error.response.data.message
                : t('financing.error')}
            </p>
          )}
          <div className="flex justify-end gap-2">
            <Button variant="ghost" disabled={save.isPending} onClick={closeEditor}>
              {t('financing.cancel')}
            </Button>
            <Button
              disabled={
                !chosenLoan || draft === null || invalid || assets.isError || loanLinks.isError
              }
              isLoading={save.isPending}
              onClick={() => draft && save.mutate(draft)}
            >
              {t('financing.save')}
            </Button>
          </div>
        </div>
      )}
    </section>
  );
}
