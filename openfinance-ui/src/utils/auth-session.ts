import { STORAGE_KEYS } from '@/constants/storage';

export const AUTH_TOKEN_RENEWED_EVENT = 'auth-token-renewed';

interface TokenClaims {
  sub: string;
  userId: number;
  tokenVersion: number;
  sid?: string;
  exp: number;
}

// Used only to order server-issued renewals and identify login boundaries.
// Authentication and signature validation remain the server's responsibility.
function readClaims(token: string | null): TokenClaims | null {
  if (!token) return null;
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const bytes = Uint8Array.from(atob(payload), char => char.charCodeAt(0));
    const claims = JSON.parse(new TextDecoder().decode(bytes)) as Partial<TokenClaims>;
    if (
      typeof claims.sub !== 'string' ||
      typeof claims.userId !== 'number' ||
      typeof claims.exp !== 'number' ||
      !Number.isFinite(claims.exp) ||
      (claims.sid !== undefined && typeof claims.sid !== 'string') ||
      (claims.tokenVersion !== undefined && typeof claims.tokenVersion !== 'number')
    ) {
      return null;
    }
    return { ...claims, tokenVersion: claims.tokenVersion ?? 0 } as TokenClaims;
  } catch {
    return null;
  }
}

export function getStoredAuthToken(): string | null {
  return (
    localStorage.getItem(STORAGE_KEYS.AUTH_TOKEN) || sessionStorage.getItem(STORAGE_KEYS.AUTH_TOKEN)
  );
}

export function isSameAuthSession(first: string | null, second: string | null): boolean {
  if (!first || !second) return false;
  if (first === second) return true;
  const a = readClaims(first);
  const b = readClaims(second);
  return !!(
    a?.sid &&
    b?.sid === a.sid &&
    a.sub === b.sub &&
    a.userId === b.userId &&
    a.tokenVersion === b.tokenVersion
  );
}

/** Keep the newest renewal in the storage chosen at login, without reviving a closed session. */
export function acceptRenewedAuthToken(requestToken: string, renewedToken: string): void {
  try {
    const currentToken = getStoredAuthToken();
    if (!isSameAuthSession(currentToken, requestToken)) return;
    const current = readClaims(currentToken);
    const requested = readClaims(requestToken);
    const renewed = readClaims(renewedToken);
    if (
      !current ||
      !requested ||
      !renewed?.sid ||
      renewed.sub !== requested.sub ||
      renewed.userId !== requested.userId ||
      renewed.tokenVersion !== requested.tokenVersion ||
      (requested.sid && requested.sid !== renewed.sid) ||
      renewed.exp <= current.exp
    ) {
      return;
    }
    const storage = localStorage.getItem(STORAGE_KEYS.AUTH_TOKEN) ? localStorage : sessionStorage;
    storage.setItem(STORAGE_KEYS.AUTH_TOKEN, renewedToken);
    // The browser's storage event only reaches other tabs.
    window.dispatchEvent(new Event(AUTH_TOKEN_RENEWED_EVENT));
  } catch {
    // A storage failure must not turn a successful API request into an error.
  }
}
