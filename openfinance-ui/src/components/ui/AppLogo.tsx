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
 * Logomark: a rounded gold square containing a stylised ascending stock-chart
 * line with a shaded area beneath it — a universally-understood symbol of
 * financial growth.
 */
export function AppLogo({ size = 32, showText = true, className }: AppLogoProps) {
  const id = 'of-logo';

  return (
    <div className={cn('flex items-center gap-2', className)}>
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
          {/* Gold gradient background */}
          <linearGradient
            id={`${id}-bg`}
            x1="0"
            y1="0"
            x2="32"
            y2="32"
            gradientUnits="userSpaceOnUse"
          >
            <stop offset="0%" stopColor="#f7b733" />
            <stop offset="100%" stopColor="#d4881a" />
          </linearGradient>

          {/* Clip to the rounded square */}
          <clipPath id={`${id}-clip`}>
            <rect width="32" height="32" rx="7.5" />
          </clipPath>
        </defs>

        {/* Background */}
        <rect width="32" height="32" rx="7.5" fill={`url(#${id}-bg)`} />

        <g clipPath={`url(#${id}-clip)`}>
          {/* Area fill under the trend line */}
          <path
            d="M4 23.5 L9.5 17.5 L15.5 20 L21.5 12.5 L28 7 L28 28 L4 28 Z"
            fill="white"
            fillOpacity="0.22"
          />

          {/* Trend line */}
          <polyline
            points="4,23.5 9.5,17.5 15.5,20 21.5,12.5 28,7"
            stroke="white"
            strokeWidth="2.5"
            strokeLinecap="round"
            strokeLinejoin="round"
          />

          {/* Peak dot */}
          <circle cx="28" cy="7" r="2.5" fill="white" />

          {/* Small upward arrow at the peak */}
          <path
            d="M25.5 5 L28 2.5 L30.5 5"
            stroke="white"
            strokeWidth="1.8"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </g>
      </svg>

      {/* ── Wordmark ── */}
      {showText && (
        <span className="text-xl font-bold text-text-primary tracking-tight">
          Open <span className="text-primary">Finance</span>
        </span>
      )}
    </div>
  );
}
