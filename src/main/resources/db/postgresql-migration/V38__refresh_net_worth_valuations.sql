-- These are derived totals, not booked balances. Rebuild them on the next dashboard request
-- with official FX parities and closed-account positions; native financial records stay intact.
DELETE FROM net_worth;
