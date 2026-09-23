# Financial Insights

← [Wiki Home](HOME.md)

---

## Overview

The Insights engine analyses your financial data and generates actionable, prioritised recommendations. Unlike the chat-based AI Assistant, insights are generated automatically and surfaced in the dashboard without requiring any user interaction.

---

## Insight Types

| Type                  | Description                                          | Example                                                                     |
| --------------------- | ---------------------------------------------------- | --------------------------------------------------------------------------- |
| Spending Anomaly      | Unusual spending pattern vs prior period             | “Restaurant spending up 45% vs last month”                                  |
| Budget Warning        | Budget close to or exceeding its limit               | “85% of grocery budget used with 12 days left”                              |
| Budget Recommendation | Budget amount misaligned with actual spending        | “Utilities average $150/mo — your $100 budget is consistently missed”       |
| Savings Opportunity   | Recurring charge or over-spend that could be reduced | “3 streaming subscriptions totalling $45/mo detected”                       |
| Investment Suggestion | Portfolio rebalancing or idle cash                   | “Cash holdings represent 35% of portfolio — consider investing the surplus” |
| Debt Alert            | High-interest debt or approaching due date           | “Credit card balance at 92% utilisation”                                    |
| Cash Flow Warning     | Projected negative cash flow                         | “Estimated expenses exceed income by $300 this month”                       |
| Tax Optimization      | Tax-advantaged opportunities                         | “Potential pension contribution room available”                             |
| Goal Progress         | Progress toward a savings or FIRE target             | “On track to reach financial freedom in 8.5 years”                          |
| General Tip           | General financial education tip                      | —                                                                           |

---

## Priority Levels

| Priority | Description                  |
| -------- | ---------------------------- |
| High     | Requires immediate attention |
| Medium   | Worth reviewing this week    |
| Low      | Informational / nice-to-know |

---

## Generating Insights

Insights refresh automatically based on your transactions and budget activity. You can also generate fresh insights from the **AI Insights** section of the [Dashboard](dashboard.md) using the **Generate Insights** (or **Refresh**) button — there is no dedicated Insights page.

When refreshed, the engine analyses your transactions, budgets, accounts, and assets and replaces the current insight list with fresh recommendations. A notification is sent to your notification centre when new insights are ready.

> Tip: use the **Refresh** button when you want an up-to-date view after a large import or budget change, rather than waiting for the next automatic run.

### Automatic Anomaly Detection

Spending anomalies are also detected automatically every day in the background. See [Anomaly Detection](anomaly-detection.md) for details.

---

## Viewing Insights

Your top 3 insights appear in the **AI Insights** section of the [Dashboard](dashboard.md), ordered by priority.

---

## Dismissing Insights

If an insight is not relevant, click **Dismiss** to hide it. Dismissed insights no longer appear in your main view.

---

## Related Pages

- [AI Assistant](ai-assistant.md)
- [Anomaly Detection](anomaly-detection.md)
- [Budget Alerts](budget-alerts.md)
- [Notifications](notifications.md)
