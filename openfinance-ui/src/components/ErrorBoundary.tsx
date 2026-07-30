import { Component, type ErrorInfo, type ReactNode } from 'react';
import { Button } from './ui/Button';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from './ui/Card';
import i18n from '@/i18n';

interface ErrorBoundaryProps {
  children: ReactNode;
  fallback?: ReactNode;
}

interface ErrorBoundaryState {
  hasError: boolean;
  error?: Error;
}

/**
 * ErrorBoundary component to catch and display React errors
 * Provides a fallback UI with error details and recovery option
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    console.error('ErrorBoundary caught an error:', error, errorInfo);
  }

  handleReset = (): void => {
    this.setState({ hasError: false, error: undefined });
  };

  render(): ReactNode {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <div className="flex items-center justify-center min-h-screen bg-background p-4">
          <Card className="max-w-md w-full">
            <CardHeader>
              <CardTitle className="text-error">{i18n.t('errorBoundary.title')}</CardTitle>
              <CardDescription>
                {i18n.t('errorBoundary.description')}
              </CardDescription>
            </CardHeader>
            <CardContent>
              {this.state.error && (
                <div className="bg-surface-elevated p-4 rounded-lg">
                  <p className="text-sm font-mono text-text-secondary break-all">
                    {this.state.error.toString()}
                  </p>
                </div>
              )}
            </CardContent>
            <CardFooter className="flex gap-2">
              <Button variant="primary" onClick={this.handleReset}>
                {i18n.t('errorBoundary.tryAgain')}
              </Button>
              <Button
                variant="secondary"
                onClick={() => window.location.reload()}
              >
                {i18n.t('errorBoundary.reloadPage')}
              </Button>
            </CardFooter>
          </Card>
        </div>
      );
    }

    return this.props.children;
  }
}
