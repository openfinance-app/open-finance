import { useWatch, type Control, type FieldPath, type FieldValues } from 'react-hook-form';
import { LinkedText } from '@/components/ui/LinkedText';

export function FormFieldLinks<T extends FieldValues, TOutput>({
  control,
  name,
}: {
  control: Control<T, unknown, TOutput>;
  name: FieldPath<T>;
}) {
  const value = useWatch({ control, name, exact: true });
  return <LinkedText text={typeof value === 'string' ? value : undefined} linksOnly />;
}
