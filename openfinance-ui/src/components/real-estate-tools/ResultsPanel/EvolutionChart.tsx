/**
 * EvolutionChart Component
 * 
 * Line chart showing net worth evolution over time
 * Requirements: REQ-1.6.5
 */

import React from 'react';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler,
} from 'chart.js';
import { Line } from 'react-chartjs-2';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { useAuthContext } from '@/context/AuthContext';
import i18n from '@/i18n';
import type { BuyRentResults } from '@/types/realEstateTools';

// Register Chart.js components
ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler
);

export interface EvolutionChartProps {
  results: BuyRentResults;
}

export const EvolutionChart: React.FC<EvolutionChartProps> = ({ results }) => {
  const { baseCurrency } = useAuthContext();
  const locale = (i18n.language ?? 'en').startsWith('fr') ? 'fr-FR' : 'en-US';
  const years = results.years.map(y => i18n.t('realEstate:evolutionChart.yearLabel', { year: y.year }));
  
  const buyNetWorth = results.years.map(y => y.buy.propertyValue - y.buy.remainingCapital);
  const rentNetWorth = results.years.map(y => y.rent.savings);

  const data = {
    labels: years,
    datasets: [
      {
        label: i18n.t('realEstate:evolutionChart.buyNetWorth'),
        data: buyNetWorth,
        borderColor: 'rgb(59, 130, 246)', // Blue
        backgroundColor: 'rgba(59, 130, 246, 0.1)',
        fill: true,
        tension: 0.4,
        pointRadius: 3,
        pointHoverRadius: 6,
      },
      {
        label: i18n.t('realEstate:evolutionChart.rentNetWorth'),
        data: rentNetWorth,
        borderColor: 'rgb(234, 179, 8)', // Yellow
        backgroundColor: 'rgba(234, 179, 8, 0.1)',
        fill: true,
        tension: 0.4,
        pointRadius: 3,
        pointHoverRadius: 6,
      },
    ],
  };

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    interaction: {
      mode: 'index' as const,
      intersect: false,
    },
    plugins: {
      legend: {
        position: 'top' as const,
        labels: {
          usePointStyle: true,
          padding: 20,
        },
      },
      title: {
        display: true,
        text: i18n.t('realEstate:evolutionChart.title'),
        font: {
          size: 16,
        },
      },
      tooltip: {
        callbacks: {
          label: (context: any) => {
            let label = context.dataset.label || '';
            if (label) {
              label += ': ';
            }
            if (context.parsed.y !== null) {
              label += new Intl.NumberFormat(locale, {
                style: 'currency',
                currency: baseCurrency,
              }).format(context.parsed.y);
            }
            return label;
          },
        },
      },
    },
    scales: {
      x: {
        grid: {
          display: false,
        },
        ticks: {
          maxTicksLimit: 10,
        },
      },
      y: {
        beginAtZero: true,
        ticks: {
          callback: (value: any) => {
            return new Intl.NumberFormat(locale, {
              style: 'currency',
              currency: baseCurrency,
              notation: 'compact',
            }).format(value);
          },
        },
      },
    },
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>{i18n.t('realEstate:evolutionChart.title')}</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="h-[400px]">
          <Line data={data} options={options} />
        </div>
      </CardContent>
    </Card>
  );
};

export default EvolutionChart;
