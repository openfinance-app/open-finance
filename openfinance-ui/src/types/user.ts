/**
 * User-related types
 */

export interface User {
  id: number;
  username: string;
  email: string;
  baseCurrency: string; // ISO 4217 currency code (e.g., "USD", "EUR")
  createdAt: string;
  updatedAt?: string;
  /** Base64 data URL for the user's profile image, or null/undefined when not set. */
  profileImage?: string | null;
}

export interface LoginRequest {
  username: string;
  password: string;
  masterPassword?: string;
  rememberMe?: boolean; // Optional: controls localStorage vs sessionStorage
}

export interface LoginResponse {
  token: string;
  userId: number;
  username: string;
  encryptionKey?: string | null;
  encryptionEnabled?: boolean;
  baseCurrency?: string;
  onboardingComplete?: boolean;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  masterPassword?: string;
}

export interface UpdateProfileRequest {
  email?: string;
  currentPassword: string; // Required for security verification
  newPassword?: string;
}

/**
 * User display and locale settings
 */
export interface UserSettings {
  id: number;
  userId: number;
  theme: 'dark' | 'light';
  dateFormat: 'MM/DD/YYYY' | 'DD/MM/YYYY' | 'YYYY-MM-DD';
  numberFormat: '1,234.56' | '1.234,56' | '1 234,56';
  language: string;
  timezone: string;
  country: string;
  createdAt: string;
  updatedAt: string;
  secondaryCurrency?: string | null;
  /** Amount display mode: 'base' = base currency, 'native' = native currency, 'both' = both inline */
  amountDisplayMode?: 'base' | 'native' | 'both';
}

/**
 * Request to update user settings (all fields optional for partial updates)
 */
export interface UpdateUserSettingsRequest {
  theme?: 'dark' | 'light';
  dateFormat?: 'MM/DD/YYYY' | 'DD/MM/YYYY' | 'YYYY-MM-DD';
  numberFormat?: '1,234.56' | '1.234,56' | '1 234,56';
  language?: string;
  timezone?: string;
  secondaryCurrency?: string;
  country?: string;
  amountDisplayMode?: 'base' | 'native' | 'both';
}

/**
 * Onboarding preferences submitted once after first login.
 */
export interface OnboardingRequest {
  country: string;
  baseCurrency: string;
  secondaryCurrency?: string;
  language: string;
  dateFormat: 'MM/DD/YYYY' | 'DD/MM/YYYY' | 'YYYY-MM-DD';
  numberFormat: '1,234.56' | '1.234,56' | '1 234,56';
  amountDisplayMode: 'base' | 'native' | 'both';
}
