/**
 * Password strength indicator component
 */
import { useMemo } from 'react';
import { cn } from '@/lib/utils';

interface PasswordStrengthProps {
  password: string;
  className?: string;
}

/**
 * Calculate password strength score (0-4)
 * @param password - Password string to evaluate
 * @returns Strength score from 0 (weak) to 4 (very strong)
 */
function calculateStrength(password: string): number {
  if (!password) return 0;

  let score = 0;

  // Minimum length
  if (password.length >= 8) score++;

  // Character variety: mixed case, digits, special characters
  if (/[a-z]/.test(password) && /[A-Z]/.test(password)) score++;
  if (/[0-9]/.test(password)) score++;
  if (/[^A-Za-z0-9]/.test(password)) score++;

  return Math.min(Math.max(score, 0), 4);
}

/**
 * Get strength label and color based on score
 */
function getStrengthInfo(score: number): { label: string; color: string } {
  switch (score) {
    case 0:
      return { label: 'Too weak', color: 'bg-error' };
    case 1:
      return { label: 'Weak', color: 'bg-error' };
    case 2:
      return { label: 'Fair', color: 'bg-warning' };
    case 3:
      return { label: 'Good', color: 'bg-success/80' };
    case 4:
      return { label: 'Strong', color: 'bg-success' };
    default:
      return { label: '', color: '' };
  }
}

/**
 * PasswordStrength component displays a visual indicator of password strength
 * Shows colored bars and a label (Too weak, Weak, Fair, Good, Strong)
 */
export function PasswordStrength({ password, className }: PasswordStrengthProps) {
  const score = useMemo(() => calculateStrength(password), [password]);
  const { label, color } = getStrengthInfo(score);

  if (!password) return null;

  return (
    <div className={cn('mt-2', className)}>
      {/* Decorative bars for sighted users */}
      <div className="flex gap-1 mb-1" aria-hidden>
        {[1, 2, 3, 4].map(level => (
          <div
            key={level}
            className={cn(
              'h-1 flex-1 rounded-full transition-colors duration-200',
              level <= score ? color : 'bg-surface-elevated'
            )}
          />
        ))}
      </div>

      {/* Accessible textual feedback */}
      <p
        className={cn(
          'text-xs',
          score <= 1 ? 'text-error' : score === 2 ? 'text-warning' : 'text-success'
        )}
        role="status"
        aria-live="polite"
      >
        {label}
      </p>

      {/* Hidden progressbar for assistive tech */}
      <div
        className="sr-only"
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={4}
        aria-valuenow={score}
        aria-label={`Password strength: ${label}`}
      />
    </div>
  );
}
