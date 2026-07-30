/**
 * CalculationErrorBoundary Component
 * 
 * Error boundary for catching calculation errors gracefully
 * Requirements: REQ-6.x
 */

import { Component } from 'react';
import type { ErrorInfo, ReactNode } from 'react';
import { AlertTriangle, RefreshCw, Home } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Alert, AlertDescription } from '@/components/ui/Alert';
import i18n from '@/i18n';

interface Props {
  children: ReactNode;
  onReset?: () => void;
  fallback?: ReactNode;
}

interface State {
  hasError: boolean;
  error?: Error;
  errorInfo?: ErrorInfo;
}

export class CalculationErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false,
  };

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('Calculation error:', error, errorInfo);
    this.setState({ errorInfo });

    // Could send to error tracking service here
    if (process.env.NODE_ENV === 'production') {
      // Example: Sentry.captureException(error);
    }
  }

  private handleReset = () => {
    this.setState({ hasError: false, error: undefined, errorInfo: undefined });
    this.props.onReset?.();
  };

  private handleReload = () => {
    window.location.reload();
  };

  private handleGoHome = () => {
    window.location.href = '/real-estate/tools';
  };

  public render() {
    if (this.state.hasError) {
      // Custom fallback UI
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <Card className="mx-auto max-w-2xl mt-8 border-error/20">
          <CardHeader className="bg-error/10">
            <CardTitle className="flex items-center gap-2 text-error">
              <AlertTriangle className="h-6 w-6" />
              {i18n.t('realEstate:calculationError.title')}
            </CardTitle>
          </CardHeader>
          <CardContent className="p-6 space-y-4">
            <Alert variant="error">
              <AlertDescription>
                {i18n.t('realEstate:calculationError.description')}
              </AlertDescription>
            </Alert>

            {process.env.NODE_ENV === 'development' && this.state.error && (
              <div className="bg-muted p-4 rounded-lg text-sm font-mono overflow-auto">
                <p className="font-semibold text-error">{this.state.error.message}</p>
                {this.state.errorInfo && (
                  <pre className="mt-2 text-xs text-muted-foreground">
                    {this.state.errorInfo.componentStack}
                  </pre>
                )}
              </div>
            )}

            <div className="flex flex-wrap gap-3 pt-4">
              <Button onClick={this.handleReset} variant="default">
                <RefreshCw className="mr-2 h-4 w-4" />
                {i18n.t('realEstate:calculationError.retry')}
              </Button>
              <Button onClick={this.handleReload} variant="outline">
                <RefreshCw className="mr-2 h-4 w-4" />
                {i18n.t('realEstate:calculationError.reloadPage')}
              </Button>
              <Button onClick={this.handleGoHome} variant="ghost">
                <Home className="mr-2 h-4 w-4" />
                {i18n.t('realEstate:calculationError.backToTools')}
              </Button>
            </div>

            <p className="text-sm text-muted-foreground mt-4">
              {i18n.t('realEstate:calculationError.persistenceMessage')}
            </p>
          </CardContent>
        </Card>
      );
    }

    return this.props.children;
  }
}

export default CalculationErrorBoundary;
