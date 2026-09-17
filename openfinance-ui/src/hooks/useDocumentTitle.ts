/**
 * useDocumentTitle hook
 * Task 4.4.6: Update document title per page
 *
 * Automatically updates the browser tab title when navigating between pages
 */
import { useEffect } from 'react';

const APP_NAME = 'Open Finance';

/**
 * Hook to set the document title
 *
 * @param title - Page title (will be appended with " | Open Finance")
 * @param options - Optional configuration
 *
 * @example
 * useDocumentTitle('Dashboard'); // Sets title to "Dashboard | Open Finance"
 * useDocumentTitle('Accounts', { appendAppName: false }); // Sets title to "Accounts"
 */
export function useDocumentTitle(
  title: string,
  options?: {
    /** Whether to append app name (default: true) */
    appendAppName?: boolean;
  }
) {
  const { appendAppName = true } = options || {};

  useEffect(() => {
    const previousTitle = document.title;
    const newTitle = appendAppName ? `${title} | ${APP_NAME}` : title;

    document.title = newTitle;

    // Restore previous title on unmount (cleanup)
    return () => {
      document.title = previousTitle;
    };
  }, [title, appendAppName]);
}

/**
 * Hook to get navigation helpers
 * Task 4.4.6: Provide navigation helpers
 *
 * @example
 * const { goBack, goToPage } = useNavigation();
 * goBack(); // Navigate to previous page
 * goToPage('/dashboard'); // Navigate to specific page
 */
export function useNavigation() {
  const goBack = () => {
    window.history.back();
  };

  const goForward = () => {
    window.history.forward();
  };

  const goToPage = (path: string) => {
    window.location.href = path;
  };

  return {
    goBack,
    goForward,
    goToPage,
  };
}

export default useDocumentTitle;
