import { find } from 'linkifyjs';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

function WebLink({ href, children }: { href: string; children: string }) {
  const { t } = useTranslation('common');
  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      title={t('opensInNewTab')}
      aria-description={t('opensInNewTab')}
      onClick={event => event.stopPropagation()}
      onKeyDown={event => {
        if (event.key === 'Enter' || event.key === ' ') event.stopPropagation();
      }}
      className="normal-case text-primary underline underline-offset-2 [overflow-wrap:anywhere] hover:text-brass-bright focus-visible:rounded-sm focus-visible:outline-2 focus-visible:outline-primary"
    >
      {children}
    </a>
  );
}

/** Render stored text verbatim, or just its web links beneath an editable field. */
export function LinkedText({
  text,
  linksOnly = false,
}: {
  text?: string | null;
  linksOnly?: boolean;
}) {
  if (!text) return null;
  const matches = find(text, 'url', { defaultProtocol: 'https' }).filter(link => {
    try {
      const { protocol } = new URL(link.href);
      return protocol === 'https:' || protocol === 'http:';
    } catch {
      return false;
    }
  });
  if (!matches.length) return linksOnly ? null : text;

  const parts: ReactNode[] = [];
  let offset = 0;
  for (const match of matches) {
    if (!linksOnly && offset < match.start) parts.push(text.slice(offset, match.start));
    parts.push(
      <WebLink key={match.start} href={match.href}>
        {match.value}
      </WebLink>
    );
    offset = match.end;
  }
  if (linksOnly)
    return <span className="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-xs">{parts}</span>;
  return (
    <>
      {parts}
      {text.slice(offset)}
    </>
  );
}
