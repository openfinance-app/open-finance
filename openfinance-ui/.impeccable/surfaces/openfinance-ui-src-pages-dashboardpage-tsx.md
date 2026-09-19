---
version: 1
slug: "openfinance-ui-src-pages-dashboardpage-tsx"
primary_target: "openfinance-ui/src/pages/DashboardPage.tsx"
related_targets: ["openfinance-ui/src/components/dashboard/NetWorthCard.tsx"]
---

# Surface: Dashboard (/dashboard)

Mode: Operate. The vault wall — the user opens the room and reads where they stand.

- Audience/job: the owner checking net worth, cash flow, and budget health in seconds; period selector is the time dial, cards are racked trays under the master plate.
- Direction: master instrument plate (NetWorthCard, guilloché, one calibrated readout) + engraved-caps card trays on hairlines; vertical compaction with explicit lg/md/sm rack defaults — no dead bays.
- Memorable moment: the vault lock (topbar eye) bolt-masks every amount on the page at once.
- Constraints: card-id registry and localStorage layout keys are stable (user customization persists across releases); every amount flows through PrivateAmount/ConvertedAmount; all strings en+fr.
- Unresolved: none.
