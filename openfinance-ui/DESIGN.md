---
name: Open Finance — The Vault
description: A private safe-deposit room rendered as an interface — machined steel plates, engraved labels, brass hardware, instrument numerals.
colors:
  background: "#0c0e10"
  surface: "#14171b"
  surface-elevated: "#1b1f24"
  border: "#262c33"
  border-strong: "#39414b"
  primary: "#c5a254"
  brass-bright: "#e3c06a"
  secondary: "#8f7a5e"
  accent-pink: "#c96f7d"
  accent-purple: "#8a80cc"
  accent-teal: "#45a191"
  accent-blue: "#5e84b0"
  text-primary: "#eceff2"
  text-secondary: "#a5aeb8"
  text-tertiary: "#89929d"
  text-muted: "#7d8691"
  success: "#3db28e"
  error: "#e07368"
  warning: "#d29a40"
  primary-foreground: "#1a1408"
typography:
  display:
    fontFamily: "Marcellus, 'Times New Roman', serif"
    fontSize: "22px"
    fontWeight: 400
    lineHeight: 1.27
    letterSpacing: "0.08em"
  headline:
    fontFamily: "Marcellus, 'Times New Roman', serif"
    fontSize: "16px"
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: "0.06em"
  title:
    fontFamily: "Inter, 'SF Pro Display', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
    fontSize: "11px"
    fontWeight: 600
    lineHeight: 1.45
    letterSpacing: "0.18em"
  body:
    fontFamily: "Inter, 'SF Pro Display', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: "normal"
  label:
    fontFamily: "Inter, 'SF Pro Display', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
    fontSize: "12px"
    fontWeight: 500
    lineHeight: 1.33
    letterSpacing: "normal"
  instrument:
    fontFamily: "'JetBrains Mono', 'Fira Code', monospace"
    fontSize: "44px"
    fontWeight: 700
    lineHeight: 1
    letterSpacing: "-0.02em"
    fontFeature: "tnum"
rounded:
  card: "12px"
  row: "10px"
  control: "8px"
  chip: "6px"
  pill: "9999px"
spacing:
  sidebar: "240px"
  sidebar-collapsed: "72px"
  topbar: "64px"
  card-padding: "24px"
  control-padding-x: "16px"
  control-padding-x-sm: "12px"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.primary-foreground}"
    typography: label
    rounded: "{rounded.control}"
    padding: "10px 16px"
  button-primary-hover:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.primary-foreground}"
    rounded: "{rounded.control}"
    padding: "10px 16px"
  button-secondary:
    backgroundColor: "{colors.surface-elevated}"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.control}"
    padding: "10px 16px"
  button-ghost:
    backgroundColor: "transparent"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.control}"
    padding: "10px 16px"
  button-danger:
    backgroundColor: "{colors.error}"
    textColor: "#ffffff"
    rounded: "{rounded.control}"
    padding: "10px 16px"
  card-plate:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.card}"
    padding: "24px"
  input-slot:
    backgroundColor: "{colors.background}"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.control}"
    padding: "8px 12px"
    height: "40px"
  badge-status:
    backgroundColor: "{colors.surface-elevated}"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.chip}"
    padding: "4px 10px"
  nav-item:
    backgroundColor: "transparent"
    textColor: "{colors.text-secondary}"
    rounded: "{rounded.control}"
    padding: "12px 12px"
  nav-item-active:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.control}"
    padding: "12px 12px"
---

# Design System: Open Finance — The Vault

## Overview

**Creative North Star: "The Private Safe-Deposit Room"**

Opening the app is opening the vault. The interface is the user's own safe-deposit room: cool charcoal-steel grounds, machined plates with hairline bevels, engraved labels, aged-brass hardware, and instrument numerals calibrated like a bank gauge. It refuses the flat-black fintech card grid where every card shouts the same neon accent — here, one warm metal speaks, and only when it matters.

Density is instrument-grade: compact rows, tabular numerals, engraved caps for plate titles, and a single authored motion (the bolt-slide) reserved for the one mechanical master lock — the amounts privacy toggle. The master plate carries the net-worth readout behind a guilloché security rosette; every account is a labeled deposit box; the sidebar is the vault index. Dark is the world's own scene — the safe-deposit room under its lamp — and the light theme is the same room with the lamp switched on (brushed aluminum daylight), with full token parity including the shadow vocabulary.

**Key Characteristics:**
- One warm metal rule: aged brass (#c5a254 dark / #7d6118 light) is hardware only — primary actions, locks, active marks, focus rings
- Machined elevation: bevel-edge highlight + tight contact shadow (`shadow-plate`), never a wide diffuse halo; inset `shadow-slot` for grooves
- Engraved voices: Marcellus roman caps for plate titles/wordmark, 11px debossed `plate-label` caps for field markings
- Instrument numerals: JetBrains Mono `tabular-nums` for every amount on the page
- Palette re-voicing: legacy Tailwind green/red/yellow/blue/gray steps are remapped into the mineral family at the token level, so every gain/loss/info surface speaks steel-and-brass
- The bolt-slide: the world's one authored motion, masking/unmasking amounts with a staggered clip-path sweep
- Browser-surface theming: `theme-color #0c0e10`, `color-scheme dark` — the vault extends past the viewport edge

## Colors

A cool charcoal-steel ground with one warm metal (aged brass) reserved for hardware, and a desaturated mineral-pigment family for data and status.

### Primary
- **Aged Brass** (#c5a254): the hardware metal. Primary action fills (vertical gradient from Brass Bright), the focus ring, active nav marks, selection highlight, transfer-type indicators, crypto/asset accents. In light theme it deepens to Dark Brass (#7d6118) to keep ≥4.5:1 against light label text.
- **Brass Bright** (#e3c06a): the top stop of every brass gradient (buttons, active period pills, the logomark's machined-brass stroke). Never used as a flat fill alone.

### Secondary
- **Aged Leather** (#8f7a5e): the secondary warm neutral — muted brass-brown used where a second warm tone is needed without competing with hardware brass.

### Tertiary (mineral chart pigments)
- **Steel Blue** (#5e84b0): info badges, notification INFO, stock/ETF chart series.
- **Muted Violet** (#8a80cc): chart series (ETF), secondary data accents.
- **Mineral Pink** (#c96f7d): chart series (mutual funds), expense-side Sankey ribbons.
- **Deep Teal** (#45a191): chart series, cashflow expense ribbons.
- **Slate** (#7d8fa3) and **Burnt Sienna** (#c07a45): chart series (real estate, commodities) — defined in `src/constants/colors.ts`.

### Neutral
- **Blackened Steel** (#0c0e10): the room itself — app background, scrollbar track base.
- **Charcoal Plate** (#14171b): the machined plate — cards, sidebar hover wells, muted fills.
- **Lifted Steel** (#1b1f24): elevated surfaces — popovers, hover states, secondary buttons.
- **Hairline Steel** (#262c33): default borders, dividers, card hairlines.
- **Bevel Steel** (#39414b): strong borders — hover states, secondary-button rims, scrollbar thumbs.
- **Etched Silver** (#eceff2): primary text on dark plates.
- **Brushed Silver** (#a5aeb8): secondary text — descriptions, metadata.
- **Engraver's Gray** (#89929d): tertiary text — plate labels, table headers, placeholders.
- **Worn Steel** (#7d8691): muted text — disabled, inactive, timestamps.

### Status (machinery indicators, desaturated mineral tones)
- **Verdigris** (#3db28e): success, gains, income amounts, transfer-destination dots.
- **Oxblood** (#e07368): error, losses, expense amounts, delete/destructive actions.
- **Aged Amber** (#d29a40): warnings, close-account actions, critical-below-exceeded alerts.

### Named Rules
**The One Metal Rule.** Brass is hardware: primary actions, locks, active marks, focus rings, the logomark. It never appears as decorative fill, chart series dominance, or large background tint. Its rarity is the point — on any given screen brass should touch ≤10% of the pixels.

**The Palette Re-Voicing Rule.** Never introduce raw Tailwind green/red/yellow/blue/gray hex values. The legacy scale steps themselves are remapped into the mineral family in `@theme` (`src/index.css`), so `text-green-600`, `bg-red-500/10`, `ring-blue-500` all resolve to vault pigments automatically. New code uses the semantic tokens (`success`, `error`, `warning`, `accent-blue`).

**The Daylight Parity Rule.** Every token overridden in `:root.light` must stay a complete set — surfaces, text, status, shadcn aliases, and all three shadows. A token that only exists in dark is a bug; the light theme is the same room with the lamp on, not a second design.

## Typography

**Display Font:** Marcellus (with Times New Roman fallback)
**Body Font:** Inter (with SF Pro Display / system sans fallback)
**Instrument Font:** JetBrains Mono (with Fira Code fallback), always `tabular-nums` for amounts

**Character:** Marcellus supplies the engraved roman capitals of bank-plate lettering — it is never a UI sans and never appears in lowercase body. Inter is the working voice, quiet and dense. JetBrains Mono renders every amount as a calibrated instrument readout.

### Hierarchy
- **Display** (Marcellus, 400, 22–26px → 32px on auth, 1.27 line-height, 0.08em tracking, UPPERCASE): page titles (`PageHeader`), auth-door headings, the "Open Finance" wordmark (15px, 0.14em tracking). Engraved plate headings.
- **Headline** (Marcellus, 400, 16–18px, 0.06em tracking, UPPERCASE): entity nameplates — account names on `AccountCard`, dialog titles (`DialogTitle`). Fixed identity plates.
- **Title / Plate Label** (Inter, 600, 11px, 0.18em tracking, UPPERCASE, `text-tertiary` with a debossing text-shadow): the `.plate-label` utility — card titles (`CardTitle`), field markings, section labels, sticky date-group headers, table head cells (11px, 0.14em).
- **Body** (Inter, 400, 14px, 1.5): descriptions, metadata, form values. 16px inside `md` inputs/buttons on mobile for zoom-avoidance.
- **Label** (Inter, 500, 12–14px): form labels, badge text, nav item labels (14px, sub-items 12px).
- **Instrument** (JetBrains Mono, 600–700, tabular-nums): every amount. The master readout is 44px/700/−0.02em tracking; account balances 24px/700; transaction amounts 14–16px/600; deltas share the gain/loss color.

### Named Rules
**The Instrument Numerals Rule.** Every monetary amount on the page renders in JetBrains Mono with `tabular-nums` (the `.number-display` utility or `font-mono` classes) — no exceptions, including deltas, projections, and converted-currency tooltips. Proportional digits in an amount is a defect.

**The Engraved Caps Rule.** Plate titles and identity plates are uppercase with wide tracking (0.06–0.18em) — Marcellus for named things (pages, accounts, dialogs), Inter 600 for anonymous markings (field labels, card titles). Sentence-case titles on a plate break the world.

## Layout

The app shell is a fixed left rail plus a machined top bar: sidebar 240px (72px collapsed, `--spacing-sidebar` / `--spacing-sidebar-collapsed`), top bar 64px (`--spacing-topbar`, sticky, backdrop-blurred `bg-background/95`). The sidebar carries an inset right hairline (`shadow-[inset_-1px_0_0_rgb(0_0_0/0.35)]`) like the vault-index drawer edge; on mobile it becomes an overlay drawer with a scrim.

Content pages use generous plate padding (cards `p-6` / 24px at `md`, `p-4`/`p-8` available) and vertical section rhythm of `space-y-6`. The dashboard is a react-grid-layout tray wall (compactType vertical, explicit lg/md/sm breakpoints) whose drag/resize chrome is re-voiced into the world: brass dashed breathing placeholder, diagonal-striped resize grip that tints brass on hover, brass state ring + deep contact shadow while dragging. Transaction ledgers group rows under sticky engraved date headers separated by brass hairlines; list rows enter with a capped stagger (`--stagger-index` × 45ms, max 360ms).

Touch targets meet WCAG 2.5.5: interactive controls are min 44px on mobile/tablet, tightening on `md:` and up (e.g. buttons `h-11 md:h-10`, `sm` `h-10 md:h-8`).

## Elevation & Depth

Depth is machined, not diffused. Plates read as physical stock: a hairline bevel highlight on the top edge plus a tight contact shadow underneath — never a wide soft halo under a hairline border. Recessed elements (inputs, progress tracks, active nav wells, icon slots) invert the relationship: an inset groove (`shadow-slot`) with a bottom bevel catch-light.

### Shadow Vocabulary
- **Plate at rest** (`shadow-plate`: `inset 0 1px 0 0 rgb(255 255 255 / 0.045), 0 1px 2px 0 rgb(0 0 0 / 0.45), 0 12px 28px -20px rgb(0 0 0 / 0.7)`): default cards, the master plate.
- **Lifted plate** (`shadow-plate-lift`: stronger bevel + `0 20px 44px -20px rgb(0 0 0 / 0.75)`): hover-lifted cards, dialogs, the vault-door login plate, highlighted rows.
- **Machined slot** (`shadow-slot`: `inset 0 1px 3px 0 rgb(0 0 0 / 0.5), inset 0 -1px 0 0 rgb(255 255 255 / 0.03)`): inputs, progress tracks, active nav items, icon tiles, the PeriodSelector well.
- **Drag state**: brass ring `0 0 0 1px color-mix(primary 45%)` + `0 24px 48px -16px rgba(0,0,0,0.65)` while a dashboard card is in the pointer's grip.

In light theme the bevel highlight strengthens (up to 0.8 alpha) and the ambient shadow softens — plates in daylight. The privacy lock's engaged state adds a small brass glow (`0 0 12px -2px rgb(197 162 84 / 0.4)`), the system's only halo, reserved for the one lock.

### Named Rules
**The Machined Depth Rule.** Every elevated surface uses `shadow-plate`/`shadow-plate-lift`; every recessed surface uses `shadow-slot`. Do not introduce ad-hoc `shadow-lg`/`shadow-xl` diffusion — depth comes from bevel + contact, not blur radius.

**The One Halo Rule.** The only glow in the system is the engaged privacy lock's brass halo. Status, focus, and hover states use brightness shifts, bevel changes, or hairline color — not box-shadow glows.

## Shapes

The form language is machined furniture: one card radius, slightly tighter control radius, pill only for true chips. Corners are never sharp (0) and never fully round on rectangular plates.

- **Cards / plates / dialogs / grid placeholders:** 12px (`--radius-card`, referenced as `rounded-[var(--radius-card)]`)
- **Transaction/ledger rows:** 10px (`rounded-[10px]`)
- **Controls — buttons, inputs, nav items, icon tiles:** 8px (`rounded-lg`)
- **Badges / status chips:** 6px (`rounded-md`)
- **Pills — category chips, scrollbar thumbs:** fully rounded (`rounded-full`)
- **Avatars / payee logos / connector dots:** circular

Borders are hairlines everywhere: 1px `border` at `--color-border`, strengthening to `--color-border-strong` on hover. The global `* { border-color: var(--color-border) }` default means an unadorned hairline is the baseline; plates without borders are the exception. Keyboard focus is a consistent 2px brass outline (`color-mix(primary 70%)`, 2px offset, 4px corner).

Signature geometry: the guilloché security rosette (`.bg-guilloche`, 120px SVG tile at 9% white, masked radially on the auth door) engraved behind master plates; the 1px brass gradient hairline (`.hairline-brass`) as an engraved separator between plate regions.

## Components

### Buttons
Machined hardware: brass for the one primary action, steel for everything else.
- **Shape:** 8px radius (`rounded-lg`); sizes sm/md/lg/icon with 44px touch floors on mobile.
- **Primary:** vertical brass gradient (`from-brass-bright to-primary`), dark engraved text (`primary-foreground` #1a1408), semibold, with an inset top bevel (`inset_0_1px_0_0 rgb(255 255 255/0.28)`) plus a 1px contact shadow. Hover is a brightness lift (1.07); active presses down (`brightness-95 translate-y-px`).
- **Secondary:** lifted steel plate (`bg-surface-elevated border-border-strong shadow-slot`), darkens to `bg-border` on hover.
- **Ghost / Outline:** transparent, steel hairline for outline; hover fills `bg-surface`.
- **Danger/Destructive:** flat oxblood fill, white text, brightness steps on hover/active.
- **Focus (all):** 2px brass ring offset against the background.

### Badges
Status plates in miniature: 6px radius, `bg-{status}/10` fill, `text-{status}`, `border-{status}/30` hairline — the tint-and-rim formula for success/error/warning/info/destructive. `default`/`secondary`/`outline` are steel variants. Tracking is `wide`; sizes sm–lg (xs–base text).

### Cards / Containers
- **Corner Style:** 12px plate radius.
- **Background:** `bg-surface` with 1px `border-border` hairline; `shadow-plate` at rest.
- **Hover (opt-in `hover` prop):** lifts to `bg-surface-elevated`, border strengthens, `shadow-plate-lift`, −2px translate — the plate rises off the tray.
- **Internal Padding:** 24px default (`p-6`), 16/32 available.
- **CardTitle** is always a `.plate-label` (11px engraved caps); **CardDescription** is 14px secondary text.
- The **master plate** (`NetWorthCard`) adds the guilloché rosette top-right, the 44px instrument readout, gain/loss-colored delta markers, and asset/liability sub-plates behind a hairline.

### Inputs / Fields
- **Style:** recessed slot — `bg-background/60`, `shadow-slot`, 1px `border-border`, 8px radius, 40px tall, 14px text.
- **Hover:** border strengthens to `border-strong`.
- **Focus:** 2px brass ring offset against background; a leading icon tints brass on focus-within.
- **Error:** oxblood border + oxblood focus ring + 14px error line with `role="alert"`.
- **Disabled:** 50% opacity, not-allowed cursor.

### Navigation
- **Sidebar (the vault index):** 240px rail on the app background, inset right hairline. Items are 12px-padded rows, 8px radius, secondary text; hover brightens text and fills `bg-surface`. **Active** is a machined well: `bg-surface shadow-slot` with a 3px brass gradient index pin (`from-brass-bright to-primary`, 60% height, rounded) inset at the left edge. Groups expand into an indented tree with a 50%-alpha hairline rail; collapsed mode shows hover tooltips on steel plates.
- **TopBar:** 64px machined bar, `bg-background/95 backdrop-blur-sm`, bottom hairline plus a tight contact shadow. Left-to-right: global search slot (center), then the hardware cluster.
- **The vault lock (privacy toggle):** the system's one mechanical control. Disengaged: ghost icon button. Engaged: brass-tinted plate — `border-primary/40 bg-primary/15 text-primary` with the brass halo. Every `PrivateAmount` on the page replays the bolt-slide (clip-path sweep, 420ms expo, staggered ≤280ms via `--bolt-index`) and masks with `blur-md select-none`.
- **PeriodSelector:** a recessed well (`shadow-slot`) of preset pills; the active preset is the primary-brass gradient chip with inset bevel — the same hardware voice as the primary button, at pill scale.

### Tables
Register typography: header cells are engraved markings (11px, 600, uppercase, 0.14em tracking, `text-tertiary`), rows separated by hairlines, hover fills `bg-muted/50`. Footer is a `bg-muted/50` band over a top hairline.

### Progress
A machined groove: 8px-tall rounded track in the app background with `shadow-slot` and a hairline; the fill is brass (`bg-primary` or a status override) scaling on `scaleX` (transform-only) with expo easing — 700ms grow on mount, 500ms on update.

### Dialogs
The vault-door plate: 12px radius, `bg-surface border-border-strong shadow-plate-lift`, centered over an 80%-opacity blurred scrim. Titles are Marcellus uppercase (18px, 0.06em tracking). Entrance is the tw-animate fade+zoom+slide set at 200ms.

### Signature: AppLogo (vault-wheel logomark)
A machined vault-door wheel in one glyph: 12 index ticks (every third tick heavier), three spokes, a hub, all stroked with the `#e3c06a → #a98a3e` brass gradient. The wordmark beside it is Marcellus 15px caps at 0.14em tracking. This mark is the world's identity — do not recolor it, restyle it, or substitute a generic icon.

### Signature: Guilloché & plate-label utilities
`.bg-guilloche` engraves the banknote rosette behind hero surfaces (master plate, auth door); `.plate-label` debosses 11px caps with a theme-aware text-shadow (dark: black above; light: white below). `.hairline-brass` draws the engraved brass separator. These three utilities are the engraving kit — reuse them rather than inventing new decoration.

## Do's and Don'ts

### Do:
- **Do** render every amount in JetBrains Mono `tabular-nums` (`font-mono` / `.number-display`) — the Instrument Numerals Rule.
- **Do** use `shadow-plate` / `shadow-plate-lift` / `shadow-slot` for all depth; reach for the tokens, not ad-hoc shadows.
- **Do** title plates and named entities in uppercase Marcellus (`font-display uppercase tracking-[0.06em]`) and anonymous markings in `.plate-label`.
- **Do** express status with the tint-and-rim formula (`bg-{status}/10 text-{status} border-{status}/30`).
- **Do** keep brass on hardware only — primary actions, active marks, the lock, focus rings (One Metal Rule).
- **Do** override the full token set when touching `:root.light`, including all three shadows (Daylight Parity Rule).
- **Do** use the motion tokens (`--ease-out-expo`, `--duration-fast/normal/slow`) and respect the `prefers-reduced-motion` block, which collapses travel to 1ms while keeping state changes.
- **Do** reuse the engraving kit (`.bg-guilloche`, `.plate-label`, `.hairline-brass`) instead of new ornament.

### Don't:
- **Don't** introduce raw Tailwind palette hexes (green-500, red-600, blue-400…) — the scale steps are re-voiced to mineral pigments at the token level; use semantic tokens.
- **Don't** add diffuse halo shadows or glow states (except the one lock halo); depth is bevel + contact, never blur.
- **Don't** use Marcellus for body copy, lowercase text, or numerals — it is engraved caps only.
- **Don't** give charts a dominant neon accent; series come from the desaturated mineral set in `src/constants/colors.ts`.
- **Don't** add a second authored motion or animation signature; the bolt-slide is the world's one mechanical moment, and standard entrances already exist (`.page-enter`, `.stagger-item`, `.pop-enter`).
- **Don't** round cards to pill shape or sharpen controls to 0 — 12px plates, ~8–10px controls/rows.
- **Don't** recolor, restyle, or replace the vault-wheel logomark.
