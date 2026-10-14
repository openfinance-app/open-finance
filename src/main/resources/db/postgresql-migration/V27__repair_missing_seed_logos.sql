-- Repair only known missing bundled assets, preserving customized logos.
UPDATE payees SET logo = '/logos/institutions/axa-bank.png' WHERE logo = '/logos/payees/axa.png';
UPDATE payees SET logo = NULL WHERE logo = '/logos/payees/rakuten.png';
UPDATE institutions SET logo = '/logos/institutions/unicredit.png' WHERE logo = '/logos/institutions/unicredit-austria.png';
UPDATE institutions SET logo = NULL WHERE logo = '/logos/institutions/caisse-epargne.png';
