/**
 * SimulationHeader Component
 *
 * Header with save/load simulation functionality
 * Requirements: REQ-1.7.x
 */

import React from 'react';
import { Save, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Card } from '@/components/ui/Card';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/Select';
import { useTranslation } from 'react-i18next';
import type { SavedSimulation } from '@/types/realEstateTools';

export interface SimulationHeaderProps {
  simulationName: string;
  onNameChange: (name: string) => void;
  onSave: () => void;
  onLoad: (id: string) => void;
  onDelete: (id: string) => Promise<boolean>;
  simulations: SavedSimulation[];
  canSave: boolean;
}

export const SimulationHeader: React.FC<SimulationHeaderProps> = ({
  simulationName,
  onNameChange,
  onSave,
  onLoad,
  onDelete,
  simulations,
  canSave,
}) => {
  const buyRentSimulations = simulations.filter(s => s.metadata.type === 'buy_rent');
  const { t } = useTranslation('realEstate');

  const handleDelete = async (id: string, name: string) => {
    if (confirm(t('comparator.confirmDelete', { name }))) {
      await onDelete(id);
    }
  };

  return (
    <Card className="mb-6 p-4">
      <div className="flex flex-col md:flex-row gap-4 items-start md:items-center">
        {/* Save Section */}
        <div className="flex-1 flex gap-2 w-full md:w-auto">
          <Input
            placeholder={t('comparator.simulationName')}
            value={simulationName}
            onChange={e => onNameChange(e.target.value)}
            className="flex-1"
            maxLength={100}
          />
          <Button onClick={onSave} disabled={!canSave} variant="default">
            <Save className="mr-2 h-4 w-4" />
            {t('comparator.save')}
          </Button>
        </div>

        {/* Load Section */}
        <div className="flex-1 flex gap-2 w-full md:w-auto">
          <Select onValueChange={onLoad}>
            <SelectTrigger className="flex-1">
              <SelectValue placeholder={t('comparator.loadSimulation')} />
            </SelectTrigger>
            <SelectContent>
              {buyRentSimulations.length === 0 ? (
                <div className="px-2 py-3 text-sm text-muted-foreground">
                  {t('comparator.noSimulationsSaved')}
                </div>
              ) : (
                buyRentSimulations.map(sim => (
                  <SelectItem key={sim.metadata.id} value={sim.metadata.id}>
                    <div className="flex items-center justify-between w-full">
                      <span>{sim.metadata.name}</span>
                      <span className="text-xs text-muted-foreground ml-2">
                        {new Date(sim.metadata.updatedAt).toLocaleDateString('fr-FR')}
                      </span>
                    </div>
                  </SelectItem>
                ))
              )}
            </SelectContent>
          </Select>

          {buyRentSimulations.length > 0 && (
            <Select
              onValueChange={id => {
                const sim = buyRentSimulations.find(s => s.metadata.id === id);
                if (sim) handleDelete(id, sim.metadata.name);
              }}
            >
              <SelectTrigger className="w-[120px]">
                <Trash2 className="mr-2 h-4 w-4" />
                {t('comparator.delete')}
              </SelectTrigger>
              <SelectContent>
                {buyRentSimulations.map(sim => (
                  <SelectItem key={sim.metadata.id} value={sim.metadata.id}>
                    {sim.metadata.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        </div>
      </div>
    </Card>
  );
};

export default SimulationHeader;
