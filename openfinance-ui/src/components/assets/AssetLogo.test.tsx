import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { AssetLogo } from '@/components/assets/AssetLogo';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { server } from '@/test/mocks/server';
import type { AssetLogoIdentity } from '@/hooks/useAssetLogo';

const logo = 'data:image/png;base64,cHJvdmlkZXI=';
const asset: AssetLogoIdentity = {
  id: 17,
  userId: 1,
  name: 'A coin outside the former bundled catalog',
  type: 'CRYPTO',
  symbol: 'NEAR-EUR',
};

describe('AssetLogo', () => {
  beforeEach(() => {
    mockAuthentication();
  });

  it('displays the authenticated resolver response for any supported symbol', async () => {
    server.use(
      http.get('/api/v1/assets/17/logo', ({ request }) => {
        expect(request.headers.get('authorization')).toMatch(/^Bearer /);
        return HttpResponse.json({ logo });
      })
    );
    renderWithProviders(<AssetLogo asset={asset} />);
    expect(await screen.findByAltText('')).toHaveAttribute('src', logo);
    // The adjacent asset name labels the row; the logo is decorative.
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });

  it('shares one lookup between the desktop and mobile representations', async () => {
    const lookup = vi.fn(() => HttpResponse.json({ logo }));
    server.use(http.get('/api/v1/assets/17/logo', lookup));
    renderWithProviders(
      <>
        <AssetLogo asset={asset} />
        <AssetLogo asset={asset} />
      </>
    );
    expect(await screen.findAllByAltText('')).toHaveLength(2);
    expect(lookup).toHaveBeenCalledTimes(1);
  });

  it.each([null, 'unavailable'])('keeps a type icon when the resolver is %s', async response => {
    const lookup = vi.fn(() =>
      response === null
        ? HttpResponse.json({ logo: null })
        : new HttpResponse(null, { status: 503 })
    );
    server.use(http.get('/api/v1/assets/17/logo', lookup));
    const { container } = renderWithProviders(<AssetLogo asset={asset} />);
    await waitFor(() => expect(lookup).toHaveBeenCalledTimes(1));
    expect(screen.queryByAltText('')).not.toBeInTheDocument();
    expect(container.querySelector('svg')).toBeInTheDocument();
  });

  it('falls back on an image failure and resolves again when asset identity changes', async () => {
    server.use(
      http.get('/api/v1/assets/17/logo', () => HttpResponse.json({ logo })),
      http.get('/api/v1/assets/18/logo', () => HttpResponse.json({ logo: `${logo}AA` }))
    );
    const { container, rerender } = renderWithProviders(<AssetLogo asset={asset} />);
    fireEvent.error(await screen.findByAltText(''));
    expect(screen.queryByAltText('')).not.toBeInTheDocument();
    expect(container.querySelector('svg')).toBeInTheDocument();

    rerender(<AssetLogo asset={{ ...asset, id: 18, type: 'STOCK', symbol: 'NEWCO.PA' }} />);
    expect(await screen.findByAltText('')).toHaveAttribute('src', `${logo}AA`);
    expect(container.querySelector('svg')).not.toBeInTheDocument();
  });

  it('does not query the market provider for physical assets', () => {
    const lookup = vi.fn(() => HttpResponse.json({ logo }));
    server.use(http.get('/api/v1/assets/17/logo', lookup));
    const { container } = renderWithProviders(<AssetLogo asset={{ ...asset, type: 'VEHICLE' }} />);
    expect(lookup).not.toHaveBeenCalled();
    expect(container.querySelector('svg')).toBeInTheDocument();
  });
});
