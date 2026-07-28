/**
 * RealEstateToolsWrapper Component
 * 
 * Wrapper component that handles routing and data sharing between
 * Buy/Rent Comparator and Rental Simulator
 * Requirements: REQ-4.2.1, REQ-5.4
 */

import React, { useState } from 'react';
import { Routes, Route, useNavigate, Navigate } from 'react-router';
import { ROUTES } from '@/constants/routes';
import { BuyRentComparator } from './BuyRentComparator';
import { PropertyRentalSimulator } from './PropertyRentalSimulator';
import type { SharedPropertyData } from '@/types/realEstateTools';
import { useCountryToolConfig } from '@/hooks/useCountryToolConfig';

/** Guard component: redirects non-France users back to the hub */
const RentalSimulatorGuard: React.FC<{
  children: React.ReactNode;
}> = ({ children }) => {
  const { isPropertyRentalAvailable } = useCountryToolConfig();
  if (!isPropertyRentalAvailable) {
    return <Navigate to={ROUTES.REAL_ESTATE_TOOLS} replace />;
  }
  return <>{children}</>;
};

export const RealEstateToolsWrapper: React.FC = () => {
  const navigate = useNavigate();
  const [sharedData, setSharedData] = useState<SharedPropertyData | undefined>();

  // Handle navigation from Buy/Rent to Rental Simulator with shared data
  const handleNavigateToRentalSimulator = (data: SharedPropertyData) => {
    setSharedData(data);
    navigate(ROUTES.REAL_ESTATE_TOOLS_RENTAL);
  };

  // Handle navigation back to Buy/Rent Comparator
  const handleNavigateBack = () => {
    navigate(ROUTES.REAL_ESTATE_TOOLS_BUY_RENT);
  };

  return (
    <Routes>
      <Route
        path="buy-rent"
        element={
          <BuyRentComparator
            onNavigateToRentalSimulator={handleNavigateToRentalSimulator}
          />
        }
      />
      <Route
        path="rental"
        element={
          <RentalSimulatorGuard>
            <PropertyRentalSimulator
              sharedData={sharedData}
              onNavigateBack={handleNavigateBack}
            />
          </RentalSimulatorGuard>
        }
      />
    </Routes>
  );
};

export default RealEstateToolsWrapper;
