/**
 * RealEstateToolsSkeleton Component
 *
 * Loading skeleton for real estate tools
 * Requirements: REQ-6.3
 */

import React from 'react';
import { Card, CardContent, CardHeader } from '@/components/ui/Card';
import { Skeleton } from '@/components/ui/Skeleton';

export const BuyRentFormSkeleton: React.FC = () => {
  return (
    <div className="space-y-6">
      {/* Summary Card Skeleton */}
      <Card className="p-4">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          {[...Array(4)].map((_, i) => (
            <div key={i}>
              <Skeleton className="h-3 w-20 mb-2" />
              <Skeleton className="h-5 w-24" />
            </div>
          ))}
        </div>
      </Card>

      {/* Form Grid Skeleton */}
      <div className="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-4 gap-6">
        {[...Array(4)].map((_, i) => (
          <Card key={i} className="h-full">
            <CardHeader className="bg-muted/50">
              <Skeleton className="h-5 w-32" />
            </CardHeader>
            <CardContent className="p-4 space-y-4">
              {[...Array(6)].map((_, j) => (
                <div key={j}>
                  <Skeleton className="h-3 w-24 mb-2" />
                  <Skeleton className="h-9 w-full" />
                </div>
              ))}
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Action Buttons Skeleton */}
      <div className="flex justify-center gap-4">
        <Skeleton className="h-10 w-40" />
        <Skeleton className="h-10 w-32" />
      </div>
    </div>
  );
};

export const ResultsPanelSkeleton: React.FC = () => {
  return (
    <div className="space-y-6">
      {/* Export Button */}
      <div className="flex justify-end">
        <Skeleton className="h-9 w-32" />
      </div>

      {/* Recommendation Card */}
      <Card className="p-6">
        <Skeleton className="h-6 w-3/4 mx-auto" />
      </Card>

      {/* Summary Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {[...Array(2)].map((_, i) => (
          <Card key={i} className="h-full">
            <CardHeader className="bg-muted/50">
              <Skeleton className="h-5 w-32" />
            </CardHeader>
            <CardContent className="p-4">
              <div className="grid grid-cols-2 gap-4">
                {[...Array(6)].map((_, j) => (
                  <div key={j}>
                    <Skeleton className="h-3 w-24 mb-1" />
                    <Skeleton className="h-5 w-28" />
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Comparison Card */}
      <Card>
        <CardHeader className="bg-muted/50">
          <Skeleton className="h-5 w-40" />
        </CardHeader>
        <CardContent className="p-6">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {[...Array(3)].map((_, i) => (
              <div key={i} className="text-center">
                <Skeleton className="h-3 w-32 mx-auto mb-2" />
                <Skeleton className="h-8 w-24 mx-auto" />
                <Skeleton className="h-3 w-20 mx-auto mt-1" />
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  );
};

export const RegimeComparisonSkeleton: React.FC = () => {
  return (
    <div className="space-y-6">
      {/* Summary Card */}
      <Card className="p-6">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          {[...Array(4)].map((_, i) => (
            <div key={i} className="text-center">
              <Skeleton className="h-3 w-24 mx-auto mb-2" />
              <Skeleton className="h-8 w-16 mx-auto" />
            </div>
          ))}
        </div>
      </Card>

      {/* Regime Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-6">
        {[...Array(4)].map((_, i) => (
          <Card key={i} className="h-full">
            <CardHeader className="bg-muted/50">
              <div className="flex justify-between items-center">
                <Skeleton className="h-5 w-28" />
                <Skeleton className="h-5 w-16" />
              </div>
              <Skeleton className="h-3 w-full mt-2" />
            </CardHeader>
            <CardContent className="p-4 space-y-4">
              {/* Performance Metrics */}
              <div className="grid grid-cols-3 gap-2">
                {[...Array(3)].map((_, j) => (
                  <div key={j} className="bg-muted/50 p-2 rounded text-center">
                    <Skeleton className="h-2 w-12 mx-auto mb-1" />
                    <Skeleton className="h-5 w-16 mx-auto" />
                  </div>
                ))}
              </div>
              {/* Tables */}
              {[...Array(4)].map((_, k) => (
                <div key={k}>
                  <Skeleton className="h-4 w-24 mb-2" />
                  <div className="space-y-1">
                    {[...Array(3)].map((_, l) => (
                      <div key={l} className="flex justify-between">
                        <Skeleton className="h-3 w-20" />
                        <Skeleton className="h-3 w-16" />
                      </div>
                    ))}
                  </div>
                </div>
              ))}
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
};

export const ToolsHubSkeleton: React.FC = () => {
  return (
    <div className="container mx-auto px-4 py-8 max-w-6xl space-y-8">
      {/* Header */}
      <div className="space-y-2">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-4 w-96" />
      </div>

      {/* Tools Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
        {[...Array(2)].map((_, i) => (
          <Card key={i} className="h-full">
            <CardHeader className="pb-4">
              <div className="flex items-center gap-4 mb-4">
                <Skeleton className="h-12 w-12 rounded-lg" />
                <Skeleton className="h-8 w-8 rounded-lg" />
              </div>
              <Skeleton className="h-6 w-48 mb-2" />
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-4 w-3/4" />
            </CardHeader>
            <CardContent className="space-y-6">
              <div className="space-y-2">
                {[...Array(4)].map((_, j) => (
                  <div key={j} className="flex items-center gap-2">
                    <Skeleton className="h-1.5 w-1.5 rounded-full" />
                    <Skeleton className="h-3 w-40" />
                  </div>
                ))}
              </div>
              <Skeleton className="h-10 w-full" />
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Info Card */}
      <Card>
        <CardHeader>
          <Skeleton className="h-5 w-48" />
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {[...Array(2)].map((_, i) => (
              <div key={i}>
                <Skeleton className="h-4 w-32 mb-2" />
                <Skeleton className="h-3 w-full" />
                <Skeleton className="h-3 w-full" />
                <Skeleton className="h-3 w-3/4" />
              </div>
            ))}
          </div>
          <Skeleton className="h-16 w-full" />
        </CardContent>
      </Card>
    </div>
  );
};

export default {
  BuyRentFormSkeleton,
  ResultsPanelSkeleton,
  RegimeComparisonSkeleton,
  ToolsHubSkeleton,
};
