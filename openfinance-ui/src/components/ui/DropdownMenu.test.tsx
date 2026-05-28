import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  DropdownMenuCheckboxItem,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuShortcut,
  DropdownMenuSub,
  DropdownMenuSubTrigger,
  DropdownMenuSubContent,
} from './DropdownMenu';

describe('DropdownMenu', () => {
  it('renders trigger', () => {
    render(
      <DropdownMenu>
        <DropdownMenuTrigger>Open menu</DropdownMenuTrigger>
        <DropdownMenuContent>
          <DropdownMenuItem>Item 1</DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    );
    expect(screen.getByText('Open menu')).toBeInTheDocument();
  });

  it('shows items when triggered', () => {
    render(
      <DropdownMenu open={true}>
        <DropdownMenuTrigger>Open menu</DropdownMenuTrigger>
        <DropdownMenuContent>
          <DropdownMenuItem>Item 1</DropdownMenuItem>
          <DropdownMenuItem>Item 2</DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    );
    expect(screen.getByText('Item 1')).toBeInTheDocument();
    expect(screen.getByText('Item 2')).toBeInTheDocument();
  });

  it('renders checkbox items', () => {
    render(
      <DropdownMenu open={true}>
        <DropdownMenuTrigger>Open</DropdownMenuTrigger>
        <DropdownMenuContent>
          <DropdownMenuCheckboxItem checked={true}>Checked option</DropdownMenuCheckboxItem>
          <DropdownMenuCheckboxItem checked={false}>Unchecked option</DropdownMenuCheckboxItem>
        </DropdownMenuContent>
      </DropdownMenu>
    );
    expect(screen.getByText('Checked option')).toBeInTheDocument();
    expect(screen.getByText('Unchecked option')).toBeInTheDocument();
  });

  it('renders radio items within a radio group', () => {
    render(
      <DropdownMenu open={true}>
        <DropdownMenuTrigger>Open</DropdownMenuTrigger>
        <DropdownMenuContent>
          <DropdownMenuRadioGroup value="a">
            <DropdownMenuRadioItem value="a">Option A</DropdownMenuRadioItem>
            <DropdownMenuRadioItem value="b">Option B</DropdownMenuRadioItem>
          </DropdownMenuRadioGroup>
        </DropdownMenuContent>
      </DropdownMenu>
    );
    expect(screen.getByText('Option A')).toBeInTheDocument();
    expect(screen.getByText('Option B')).toBeInTheDocument();
  });

  it('renders label with and without inset', () => {
    render(
      <DropdownMenu open={true}>
        <DropdownMenuTrigger>Open</DropdownMenuTrigger>
        <DropdownMenuContent>
          <DropdownMenuLabel>Normal label</DropdownMenuLabel>
          <DropdownMenuLabel inset>Inset label</DropdownMenuLabel>
        </DropdownMenuContent>
      </DropdownMenu>
    );
    expect(screen.getByText('Normal label')).toBeInTheDocument();
    const insetLabel = screen.getByText('Inset label');
    expect(insetLabel).toBeInTheDocument();
    expect(insetLabel.className).toContain('pl-8');
  });

  it('renders separator', () => {
    render(
      <DropdownMenu open={true}>
        <DropdownMenuTrigger>Open</DropdownMenuTrigger>
        <DropdownMenuContent>
          <DropdownMenuItem>Item 1</DropdownMenuItem>
          <DropdownMenuSeparator data-testid="sep" />
          <DropdownMenuItem>Item 2</DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    );
    expect(screen.getByTestId('sep')).toBeInTheDocument();
  });

  it('renders shortcut text', () => {
    render(
      <DropdownMenu open={true}>
        <DropdownMenuTrigger>Open</DropdownMenuTrigger>
        <DropdownMenuContent>
          <DropdownMenuItem>
            Copy <DropdownMenuShortcut>⌘C</DropdownMenuShortcut>
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    );
    expect(screen.getByText('⌘C')).toBeInTheDocument();
  });

  it('renders sub menu trigger and content', () => {
    render(
      <DropdownMenu open={true}>
        <DropdownMenuTrigger>Open</DropdownMenuTrigger>
        <DropdownMenuContent>
          <DropdownMenuSub open={true}>
            <DropdownMenuSubTrigger>More options</DropdownMenuSubTrigger>
            <DropdownMenuSubContent>
              <DropdownMenuItem>Sub item</DropdownMenuItem>
            </DropdownMenuSubContent>
          </DropdownMenuSub>
        </DropdownMenuContent>
      </DropdownMenu>
    );
    expect(screen.getByText('More options')).toBeInTheDocument();
    expect(screen.getByText('Sub item')).toBeInTheDocument();
  });

  it('renders sub trigger with inset', () => {
    render(
      <DropdownMenu open={true}>
        <DropdownMenuTrigger>Open</DropdownMenuTrigger>
        <DropdownMenuContent>
          <DropdownMenuSub>
            <DropdownMenuSubTrigger inset>Inset sub</DropdownMenuSubTrigger>
          </DropdownMenuSub>
        </DropdownMenuContent>
      </DropdownMenu>
    );
    const trigger = screen.getByText('Inset sub');
    expect(trigger.closest('[class]')?.className).toContain('pl-8');
  });
});
