# Anomaly Detection

← [Wiki Home](HOME.md)

---

## Overview

The Anomaly Detection system scans your transactions daily and identifies patterns that deviate significantly from your historical norms. Detected anomalies are surfaced as high-priority insights and notifications — no manual review of every transaction required.

---

## What Gets Detected

Open-Finance runs several checks on your recent transactions:

### Category Spending Spike

If your spending in a category this month is more than 50% higher than your 3-month rolling average, it flags an anomaly. At least 2 months of history are needed to establish a baseline.

**Example:** “Restaurant spending is 72% higher than your 3-month average ($520 vs $302).”

### Unusually Large Transaction

If a single transaction is much larger than your typical spend in that category (more than 2 standard deviations above the average for the last 90 days), it is flagged.

**Example:** A $500 grocery transaction when your usual grocery spend is $80.

### Unusual Transaction Frequency

If a payee suddenly appears far more often than usual in a week (more than 3× your weekly average for that payee), it is flagged.

**Example:** Useful for catching duplicate imports or unexpected recurring charges.

### Unexpected Timing

Transactions in categories where you historically never transact at certain times (e.g., a weekday-only utility payment appearing on a Sunday) can also be flagged.

---

## How Alerts Are Delivered

When an anomaly is detected:

1. A **high-priority insight** is created and appears in the **AI Insights** section of your [Dashboard](dashboard.md).
2. A **notification** is sent to your notification centre.

You can dismiss insights that turn out to be false positives. See [Financial Insights](insights.md) for more.

---

## Automatic Schedule

Anomaly detection runs automatically every day in the background. You don’t need to do anything — new anomalies will appear in your notifications when detected.

---

## Related Pages

- [Financial Insights](insights.md)
- [Notifications](notifications.md)
- [Transactions](transactions.md)
