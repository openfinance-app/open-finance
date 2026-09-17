/**
 * Animation utility classes and configurations for Open-Finance UI.
 * Provides consistent animation behavior across the application.
 */

/**
 * Animation duration presets (in milliseconds)
 */
export const DURATIONS = {
  fast: 150,
  normal: 200,
  slow: 300,
  verySlow: 500,
} as const;

/**
 * Animation easing functions
 */
export const EASINGS = {
  ease: 'ease',
  easeIn: 'ease-in',
  easeOut: 'ease-out',
  easeInOut: 'ease-in-out',
  linear: 'linear',
  spring: 'cubic-bezier(0.68, -0.55, 0.265, 1.55)',
} as const;

/**
 * Tailwind animation classes
 */
export const ANIMATIONS = {
  fadeIn: 'animate-fade-in',
  fadeOut: 'animate-fade-out',
  slideInUp: 'animate-slide-in-up',
  slideInDown: 'animate-slide-in-down',
  slideInLeft: 'animate-slide-in-left',
  slideInRight: 'animate-slide-in-right',
  scaleIn: 'animate-scale-in',
  scaleOut: 'animate-scale-out',
  spin: 'animate-spin',
  pulse: 'animate-pulse',
  bounce: 'animate-bounce',
  shimmer: 'animate-shimmer',
} as const;

/**
 * Page transition variants
 */
export const pageTransition = {
  initial: { opacity: 0, y: 20 },
  animate: { opacity: 1, y: 0 },
  exit: { opacity: 0, y: -20 },
  transition: { duration: DURATIONS.normal / 1000 },
};

/**
 * Modal/Dialog transition variants
 */
export const modalTransition = {
  initial: { opacity: 0, scale: 0.95 },
  animate: { opacity: 1, scale: 1 },
  exit: { opacity: 0, scale: 0.95 },
  transition: { duration: DURATIONS.fast / 1000 },
};

/**
 * Dropdown/Menu transition variants
 */
export const dropdownTransition = {
  initial: { opacity: 0, y: -10 },
  animate: { opacity: 1, y: 0 },
  exit: { opacity: 0, y: -10 },
  transition: { duration: DURATIONS.fast / 1000 },
};

/**
 * Toast notification transition variants
 */
export const toastTransition = {
  initial: { opacity: 0, x: 100 },
  animate: { opacity: 1, x: 0 },
  exit: { opacity: 0, x: 100 },
  transition: { duration: DURATIONS.normal / 1000 },
};

/**
 * Card hover animation
 */
export const cardHover = {
  scale: 1.02,
  transition: { duration: DURATIONS.fast / 1000 },
};

/**
 * Button press animation
 */
export const buttonPress = {
  scale: 0.95,
  transition: { duration: DURATIONS.fast / 1000 },
};

/**
 * Utility function to create custom transition
 */
export function createTransition(
  duration: number = DURATIONS.normal,
  easing: string = EASINGS.easeInOut
) {
  return {
    duration: duration / 1000,
    ease: easing,
  };
}

/**
 * Stagger children animation
 */
export function staggerChildren(delayBetweenChildren: number = 0.1) {
  return {
    animate: {
      transition: {
        staggerChildren: delayBetweenChildren,
      },
    },
  };
}

/**
 * CSS class utilities for animations
 */
export const animationClasses = {
  /**
   * Fade in animation
   */
  fadeIn: 'opacity-0 animate-[fade-in_200ms_ease-in-out_forwards]',

  /**
   * Fade out animation
   */
  fadeOut: 'opacity-100 animate-[fade-out_200ms_ease-in-out_forwards]',

  /**
   * Slide in from bottom
   */
  slideInUp: 'translate-y-4 opacity-0 animate-[slide-in-up_200ms_ease-out_forwards]',

  /**
   * Slide in from top
   */
  slideInDown: 'translate-y-[-16px] opacity-0 animate-[slide-in-down_200ms_ease-out_forwards]',

  /**
   * Scale in animation
   */
  scaleIn: 'scale-95 opacity-0 animate-[scale-in_200ms_ease-out_forwards]',

  /**
   * Shimmer loading animation
   */
  shimmer:
    'relative overflow-hidden before:absolute before:inset-0 before:-translate-x-full before:animate-[shimmer_2s_infinite] before:bg-gradient-to-r before:from-transparent before:via-white/10 before:to-transparent',

  /**
   * Pulse animation
   */
  pulse: 'animate-[pulse_2s_cubic-bezier(0.4,0,0.6,1)_infinite]',

  /**
   * Spin animation
   */
  spin: 'animate-[spin_1s_linear_infinite]',

  /**
   * Hover lift animation
   */
  hoverLift:
    'transition-transform duration-150 ease-in-out hover:scale-[1.02] hover:shadow-lg active:scale-[0.98]',

  /**
   * Button hover animation
   */
  buttonHover: 'transition-all duration-150 ease-in-out hover:brightness-110 active:scale-95',

  /**
   * Card hover animation
   */
  cardHover:
    'transition-all duration-200 ease-in-out hover:shadow-xl hover:scale-[1.01] hover:brightness-105',
};

/**
 * Micro-interaction utilities
 */
export const microInteractions = {
  /**
   * Button press effect
   */
  buttonPress: 'active:scale-95 transition-transform duration-100',

  /**
   * Link hover effect
   */
  linkHover: 'hover:opacity-80 transition-opacity duration-150',

  /**
   * Icon hover effect
   */
  iconHover: 'hover:scale-110 transition-transform duration-150',

  /**
   * Input focus effect
   */
  inputFocus: 'focus:ring-2 focus:ring-primary focus:border-primary transition-all duration-200',

  /**
   * Checkbox/Radio check effect
   */
  checkEffect: 'transition-all duration-200 ease-in-out',
};
