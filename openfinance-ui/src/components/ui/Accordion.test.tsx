import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { Accordion, AccordionItem, AccordionTrigger, AccordionContent } from './Accordion';

describe('Accordion', () => {
  function renderAccordion(defaultValue?: string) {
    return render(
      <Accordion defaultValue={defaultValue}>
        <AccordionItem value="item-1">
          <AccordionTrigger>Section 1</AccordionTrigger>
          <AccordionContent>Content 1</AccordionContent>
        </AccordionItem>
        <AccordionItem value="item-2">
          <AccordionTrigger>Section 2</AccordionTrigger>
          <AccordionContent>Content 2</AccordionContent>
        </AccordionItem>
      </Accordion>
    );
  }

  it('renders trigger buttons', () => {
    renderAccordion();
    expect(screen.getByText('Section 1')).toBeInTheDocument();
    expect(screen.getByText('Section 2')).toBeInTheDocument();
  });

  it('shows content when defaultValue matches', () => {
    renderAccordion('item-1');
    const content1 = screen.getByText('Content 1').closest('div[class*="overflow-hidden"]') as HTMLElement;
    expect(content1.className).toContain('max-h-[2000px]');
  });

  it('hides content when not selected', () => {
    renderAccordion('item-1');
    const content2 = screen.getByText('Content 2').closest('div[class*="overflow-hidden"]') as HTMLElement;
    expect(content2.className).toContain('max-h-0');
  });

  it('toggles content on trigger click', () => {
    renderAccordion();
    fireEvent.click(screen.getByText('Section 1'));
    const content1 = screen.getByText('Content 1').closest('div[class*="overflow-hidden"]') as HTMLElement;
    expect(content1.className).toContain('max-h-[2000px]');
  });

  it('closes open item on second click', () => {
    renderAccordion('item-1');
    fireEvent.click(screen.getByText('Section 1'));
    const content1 = screen.getByText('Content 1').closest('div[class*="overflow-hidden"]') as HTMLElement;
    expect(content1.className).toContain('max-h-0');
  });

  it('applies className to root', () => {
    const { container } = render(
      <Accordion className="custom-accordion">
        <AccordionItem value="a">
          <AccordionTrigger>T</AccordionTrigger>
          <AccordionContent>C</AccordionContent>
        </AccordionItem>
      </Accordion>
    );
    expect((container.firstChild as HTMLElement).className).toContain('custom-accordion');
  });
});
