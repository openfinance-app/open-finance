/**
 * RealEstateToolsHub Component
 *
 * Landing page for real estate tools
 * Provides navigation to Buy/Rent Comparator and Rental Simulator
 * Requirements: REQ-4.1.1
 */

import React from 'react';
import { useNavigate } from 'react-router';
import { Calculator, Building2, ArrowRight, BarChart3, Home, Key, Lock } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { PageHeader } from '@/components/layout/PageHeader';
import { useCountryToolConfig } from '@/hooks/useCountryToolConfig';
import { useTranslation } from 'react-i18next';

export const RealEstateToolsHub: React.FC = () => {
  const navigate = useNavigate();
  const { t } = useTranslation('realEstate');
  const { isPropertyRentalAvailable, countryCode } = useCountryToolConfig();

  const tools = [
    {
      id: 'buy-rent',
      title: t('toolsHub.buyRent.title'),
      description: t('toolsHub.buyRent.description'),
      icon: <Home className="h-8 w-8" />,
      secondaryIcon: <Key className="h-6 w-6" />,
      path: '/real-estate/tools/buy-rent',
      features: [
        t('toolsHub.buyRent.feature1'),
        t('toolsHub.buyRent.feature2'),
        t('toolsHub.buyRent.feature3'),
        t('toolsHub.buyRent.feature4'),
      ],
      color: 'bg-primary/10 text-primary border-primary/20',
      buttonVariant: 'default' as const,
      locked: false,
    },
    {
      id: 'rental-simulator',
      title: t('toolsHub.rental.title'),
      description: t('toolsHub.rental.description'),
      icon: <Building2 className="h-8 w-8" />,
      secondaryIcon: <BarChart3 className="h-6 w-6" />,
      path: '/real-estate/tools/rental',
      features: [
        t('toolsHub.rental.feature1'),
        t('toolsHub.rental.feature2'),
        t('toolsHub.rental.feature3'),
        t('toolsHub.rental.feature4'),
      ],
      color: 'bg-success/10 text-success border-success/20',
      buttonVariant: 'outline' as const,
      locked: !isPropertyRentalAvailable,
    },
  ];

  return (
    <div className="container mx-auto px-4 py-8 max-w-6xl">
      <PageHeader title={t('toolsHub.pageTitle')} description={t('toolsHub.pageDescription')} />

      {/* Tools Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-8 mt-8">
        {tools.map(tool => (
          <Card
            key={tool.id}
            className={`relative overflow-hidden border-2 ${tool.locked ? 'opacity-70 border-border bg-muted/30' : tool.color}`}
          >
            {/* France-only lock overlay */}
            {tool.locked && (
              <div className="absolute top-3 right-3 flex items-center gap-1.5 rounded-full bg-muted px-3 py-1 text-xs font-medium text-muted-foreground border border-border z-10">
                <Lock className="h-3 w-3" />
                {t('toolsHub.franceOnly')}
              </div>
            )}
            <CardHeader className="pb-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-4">
                  <div className="p-3 rounded-lg bg-background/80">{tool.icon}</div>
                  <div className="p-2 rounded-lg bg-background/60">{tool.secondaryIcon}</div>
                </div>
              </div>
              <CardTitle className="text-2xl mt-4">{tool.title}</CardTitle>
              <CardDescription className="text-base mt-2">{tool.description}</CardDescription>
            </CardHeader>
            <CardContent className="space-y-6">
              {/* Features List */}
              <ul className="space-y-2">
                {tool.features.map((feature, index) => (
                  <li key={index} className="flex items-center gap-2 text-sm">
                    <div className="h-1.5 w-1.5 rounded-full bg-current" />
                    {feature}
                  </li>
                ))}
              </ul>

              {/* Unavailability notice for locked tools */}
              {tool.locked && (
                <p className="text-sm text-muted-foreground bg-muted rounded-md px-3 py-2">
                  {t('toolsHub.franceOnlyNotice', { country: countryCode })}
                </p>
              )}

              {/* Action Button */}
              <Button
                size="lg"
                variant={tool.locked ? 'secondary' : tool.buttonVariant}
                className="w-full"
                disabled={tool.locked}
                onClick={() => !tool.locked && navigate(tool.path)}
              >
                {tool.locked ? (
                  <>
                    <Lock className="mr-2 h-4 w-4" />
                    {t('toolsHub.unavailableForCountry')}
                  </>
                ) : (
                  <>
                    <Calculator className="mr-2 h-4 w-4" />
                    {t('toolsHub.openSimulator')}
                    <ArrowRight className="ml-2 h-4 w-4" />
                  </>
                )}
              </Button>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Information Section */}
      <Card className="mt-12">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Calculator className="h-5 w-5" />
            {t('toolsHub.howToUse')}
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <h4 className="font-semibold mb-2">{t('toolsHub.step1Title')}</h4>
              <p className="text-sm text-muted-foreground">{t('toolsHub.step1Description')}</p>
            </div>
            <div>
              <h4 className="font-semibold mb-2">{t('toolsHub.step2Title')}</h4>
              <p className="text-sm text-muted-foreground">{t('toolsHub.step2Description')}</p>
            </div>
          </div>

          <div className="bg-muted/50 p-4 rounded-lg mt-4">
            <p className="text-sm">{t('toolsHub.tip')}</p>
          </div>
        </CardContent>
      </Card>

      {/* Legal Disclaimer */}
      <p className="text-xs text-muted-foreground text-center mt-8">{t('toolsHub.disclaimer')}</p>
    </div>
  );
};

export default RealEstateToolsHub;
