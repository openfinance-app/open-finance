import { beforeEach, describe, expect, it } from 'vitest';
import { useEffect } from 'react';
import { Link, Route, Routes, useNavigate, useSearchParams } from 'react-router';
import { screen, waitFor } from '@testing-library/react';
import { mockAuthentication, renderWithProviders, userEvent } from '@/test/test-utils';
import { useViewState } from '@/hooks/useViewState';
import { useViewStateStore } from '@/stores/viewState';

function List() {
  const [params, setParams] = useSearchParams();
  const [filters, setFilters] = useViewState(
    'filters',
    { account: params.get('account') ?? '', keyword: '' },
    params.has('account')
  );
  const [tab, setTab] = useViewState('detailTab', 'Overview');
  useEffect(() => {
    if (params.has('account')) setParams({}, { replace: true });
  }, [params, setParams]);
  return (
    <>
      <output aria-label="Account scope">{filters.account}</output>
      <input
        aria-label="Keyword"
        value={filters.keyword}
        onChange={event => setFilters(current => ({ ...current, keyword: event.target.value }))}
      />
      <button onClick={() => setTab('Transactions')}>{tab}</button>
      <Link to="/other">Account detail</Link>
      <Link to="/list?account=2">Second account</Link>
    </>
  );
}

function Workflow() {
  const navigate = useNavigate();
  return (
    <>
      <button onClick={() => navigate(-1)}>Back</button>
      <button onClick={() => navigate(1)}>Forward</button>
      <Link to="/list">Transactions view</Link>
      <Routes>
        <Route path="/list" element={<List />} />
        <Route path="/other" element={<p>Other view</p>} />
      </Routes>
    </>
  );
}

describe('retained view state', () => {
  beforeEach(() => {
    mockAuthentication();
    useViewStateStore.getState().clear();
    window.history.replaceState({}, '', '/list?account=1');
  });

  it('restores filters and detail tabs on Back and through ordinary navigation', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Workflow />);
    await user.type(screen.getByRole('textbox', { name: 'Keyword' }), 'Coffee');
    await user.click(screen.getByRole('button', { name: 'Overview' }));
    await user.click(screen.getByRole('link', { name: 'Account detail' }));
    await user.click(screen.getByRole('button', { name: 'Back' }));
    await waitFor(() => expect(screen.getByRole('textbox')).toHaveValue('Coffee'));
    expect(screen.getByRole('button', { name: 'Transactions' })).toBeInTheDocument();
    await user.click(screen.getByRole('link', { name: 'Account detail' }));
    await user.click(screen.getByRole('link', { name: 'Transactions view' }));
    expect(screen.getByRole('textbox')).toHaveValue('Coffee');
  });

  it('applies a same-route deep link without merging old filters and restores each history entry independently', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Workflow />);
    await user.type(screen.getByRole('textbox'), 'Coffee');
    await user.click(screen.getByRole('link', { name: 'Second account' }));
    expect(screen.getByLabelText('Account scope')).toHaveTextContent('2');
    expect(screen.getByRole('textbox')).toHaveValue('');
    await user.type(screen.getByRole('textbox'), 'Rent');
    await user.click(screen.getByRole('button', { name: 'Back' }));
    await waitFor(() => expect(screen.getByLabelText('Account scope')).toHaveTextContent('1'));
    expect(screen.getByRole('textbox')).toHaveValue('Coffee');
    await user.click(screen.getByRole('button', { name: 'Forward' }));
    await waitFor(() => expect(screen.getByLabelText('Account scope')).toHaveTextContent('2'));
    expect(screen.getByRole('textbox')).toHaveValue('Rent');
  });

  it('clears view history when the session ends', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Workflow />);
    await user.type(screen.getByRole('textbox'), 'Private filter');
    await user.click(screen.getByRole('link', { name: 'Account detail' }));
    useViewStateStore.getState().clear();
    await user.click(screen.getByRole('link', { name: 'Transactions view' }));
    expect(screen.getByRole('textbox')).toHaveValue('');
    expect(screen.getByLabelText('Account scope')).toBeEmptyDOMElement();
  });
});
