/**
 * Custom hook to fetch user's financial data for pre-populating calculator inputs
 * Task 4.4.2: Create useUserFinancialData hook
 * Task 6.2.2: Connect user's financial data
 */

import { useState, useEffect } from 'react';
import apiClient from '../services/apiClient';
import type { Asset } from '../types/asset';
import type { Transaction } from '../types/transaction';
import { DEFAULT_CURRENCY } from '@/utils/currency';

interface UserFinancialData {
    totalSavings: number;
    averageMonthlyExpenses: number;
    currency: string;
}

interface UseUserFinancialDataReturn {
    data: UserFinancialData | null;
    isLoading: boolean;
    error: string | null;
    refetch: () => Promise<void>;
}

/**
 * Hook to fetch and calculate user's financial data from their assets and transactions
 * 
 * Calculates:
 * - Total savings: Sum of all asset values
 * - Average monthly expenses: Average of expense transactions over the last 6 months
 * 
 * @returns Object containing financial data, loading state, error, and refetch function
 */
export const useUserFinancialData = (): UseUserFinancialDataReturn => {
    const [data, setData] = useState<UserFinancialData | null>(null);
    const [isLoading, setIsLoading] = useState<boolean>(false);
    const [error, setError] = useState<string | null>(null);

    const fetchFinancialData = async () => {
        setIsLoading(true);
        setError(null);

        try {
            // Fetch user's assets to calculate total savings
            const assetsResponse = await apiClient.get<Asset[]>('/assets');
            const assets = assetsResponse.data;

            // Calculate total savings from all assets
            const totalSavings = assets.reduce((sum, asset) => {
                return sum + (asset.totalValue || asset.quantity * asset.currentPrice);
            }, 0);

            // Fetch transactions from the last 6 months to calculate average expenses
            const sixMonthsAgo = new Date();
            sixMonthsAgo.setMonth(sixMonthsAgo.getMonth() - 6);
            const dateFrom = sixMonthsAgo.toISOString().split('T')[0];

            const transactionsResponse = await apiClient.get<Transaction[]>('/transactions', {
                params: {
                    type: 'EXPENSE',
                    dateFrom,
                    dateTo: new Date().toISOString().split('T')[0],
                },
            });

            const expenses = transactionsResponse.data;

            // Calculate average monthly expenses
            let averageMonthlyExpenses = 0;
            if (expenses.length > 0) {
                const totalExpenses = expenses.reduce((sum, transaction) => {
                    return sum + Math.abs(transaction.amount);
                }, 0);

                // Calculate number of months in the period
                const oldestExpense = expenses.reduce((oldest, transaction) => {
                    const transactionDate = new Date(transaction.date);
                    return transactionDate < oldest ? transactionDate : oldest;
                }, new Date());

                const monthsDiff = Math.max(
                    1,
                    Math.ceil(
                        (new Date().getTime() - oldestExpense.getTime()) / (1000 * 60 * 60 * 24 * 30)
                    )
                );

                averageMonthlyExpenses = totalExpenses / monthsDiff;
            }

            // Determine currency (use first asset's currency or the app default)
            const currency = assets.length > 0 ? assets[0].currency : DEFAULT_CURRENCY;

            setData({
                totalSavings,
                averageMonthlyExpenses,
                currency,
            });
        } catch (err) {
            console.error('Error fetching user financial data:', err);
            setError(
                err instanceof Error
                    ? err.message
                    : 'Failed to fetch financial data. Please try again.'
            );
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        fetchFinancialData();
    }, []);

    return {
        data,
        isLoading,
        error,
        refetch: fetchFinancialData,
    };
};
