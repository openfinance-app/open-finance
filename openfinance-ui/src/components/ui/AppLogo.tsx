import { cn } from '@/lib/utils';

interface AppLogoProps {
  /** Icon size in pixels (default: 32) */
  size?: number;
  /** Show "Open Finance" wordmark next to the icon (default: true) */
  showText?: boolean;
  className?: string;
}
/**
 * Open Finance brand logo.
 *
 * Logomark: a vault-door wheel — a knurled brass dial ring (fine machined
 * notches, never clock hours), three spokes around a hub.
 */

// Knurled dial notches, computed once at module scope. 36 fine grips read as
// machined knurling; 12 evenly-spaced ticks read as a clock, so never 12.
const KNURL = Array.from({ length: 36 }, (_, i) => {
  const angle = (i * 10 * Math.PI) / 180;
  const r1 = 12.6;
  const r2 = 14;
  return {
    x1: 16 + r1 * Math.cos(angle),
    y1: 16 + r1 * Math.sin(angle),
    x2: 16 + r2 * Math.cos(angle),
    y2: 16 + r2 * Math.sin(angle),
  };
});

const SPOKES = [90, 210, 330].map(deg => {
  const angle = (deg * Math.PI) / 180;
  return {
    x2: 16 + 8.6 * Math.cos(angle),
    y2: 16 + 8.6 * Math.sin(angle),
  };
});

export function AppLogo({ size = 32, showText = true, className }: AppLogoProps) {
  const id = 'of-logo';

  return (
    <div className={cn('flex items-center gap-2.5', className)}>
      {/* ── Logomark ── */}
      <svg
        width={size}
        height={size}
        viewBox="0 0 32 32"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        aria-label="Open Finance logo"
        role="img"
      >
        <defs>
          {/* Machined brass gradient */}
          <linearGradient
            id={`${id}-brass`}
            x1="6"
            y1="4"
            x2="26"
            y2="28"
            gradientUnits="userSpaceOnUse"
          >
            <stop offset="0%" stopColor="#e3c06a" />
            <stop offset="100%" stopColor="#a98a3e" />
          </linearGradient>
        </defs>

        {/* Knurled dial ring */}
        {KNURL.map((n, i) => (
          <line
            key={i}
            x1={n.x1}
            y1={n.y1}
            x2={n.x2}
            y2={n.y2}
            stroke={`url(#${id}-brass)`}
            strokeWidth="1.1"
            strokeLinecap="round"
          />
        ))}

        {/* Dial face ring */}
        <circle cx="16" cy="16" r="10.6" stroke={`url(#${id}-brass)`} strokeWidth="1.5" />

        {/* Wheel spokes */}
        {SPOKES.map((spoke, i) => (
          <line
            key={i}
            x1="16"
            y1="16"
            x2={spoke.x2}
            y2={spoke.y2}
            stroke={`url(#${id}-brass)`}
            strokeWidth="1.8"
            strokeLinecap="round"
          />
        ))}

        {/* Hub */}
        <circle cx="16" cy="16" r="2.6" fill={`url(#${id}-brass)`} />
      </svg>

      {/* ── Wordmark — engraved bank-plate caps ── */}
      {showText && (
        <span className="font-display text-[15px] uppercase tracking-[0.14em] text-text-primary pt-px">
          Open&nbsp;Finance
        </span>
      )}
    </div>
  );
}
