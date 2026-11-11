-- Backfill missing countries from InstitutionSeeder's existing system catalog.
-- Preserve custom institutions, existing country choices and unknown institutions.
-- Names shared by countries (ING) require the catalog BIC to disambiguate them.
WITH seed_countries(name, bic, country) AS (
    VALUES
    ('BNP Paribas', 'BNPAFRPP', 'FR'),
    ('Société Générale', 'SOGEFRPP', 'FR'),
    ('Crédit Agricole', 'AGRIFRPP', 'FR'),
    ('LCL', 'LYORFRPP', 'FR'),
    ('Boursorama', 'BOUSFRPP', 'FR'),
    ('Hello bank!', 'BNPAFRPPXXX', 'FR'),
    ('Fortuneo', 'FTTOFRPP', 'FR'),
    ('Monabanq', 'MABAFRPP', 'FR'),
    ('Orange Bank', 'CDCGFRPP', 'FR'),
    ('N26', 'NTSBDEB1', 'FR'),
    ('Revolut', 'REVOFRPP', 'FR'),
    ('Banque Populaire', 'BPOFRPP', 'FR'),
    ('Caisse d''Epargne', 'CEEPFRPP', 'FR'),
    ('Crédit Mutuel', 'CMCIFRPP', 'FR'),
    ('La Banque Postale', 'PSSTFRPP', 'FR'),
    ('Deutsche Bank', 'DEUTDEFF', 'DE'),
    ('Commerzbank', 'COBADEFF', 'DE'),
    ('Postbank', 'PBNKDEFF', 'DE'),
    ('Sparkasse', 'SPKAT', 'DE'),
    ('Volksbank', 'GENODE', 'DE'),
    ('ING DiBa', 'INGDDEFF', 'DE'),
    ('Comdirect', 'COMPDEDD', 'DE'),
    ('DKB', 'BYLADEM1001', 'DE'),
    ('Consorsbank', 'CONSDEDD', 'DE'),
    ('Onvista Bank', 'AONTDEE1', 'DE'),
    ('Banco Santander', 'BSCHESMM', 'ES'),
    ('BBVA', 'BBVAESMM', 'ES'),
    ('CaixaBank', 'CAIXESBB', 'ES'),
    ('Banco Sabadell', 'BSABESBB', 'ES'),
    ('Banco Popular', 'POPULARES', 'ES'),
    ('ING', 'INGDESMM', 'ES'),
    ('Openbank', 'OPENESMM', 'ES'),
    ('Evo Banco', 'EVOBESMM', 'ES'),
    ('Kutxabank', 'KUTXESBB', 'ES'),
    ('Unicaja', 'UCJAES2M', 'ES'),
    ('Intesa Sanpaolo', 'IBSPITRA', 'IT'),
    ('UniCredit', 'UNCRITMM', 'IT'),
    ('Monte dei Paschi di Siena', 'PASCITMM', 'IT'),
    ('Banco BPM', 'BPMOIT22', 'IT'),
    ('CheBanca!', 'CREDITMM', 'IT'),
    ('Widiba', 'WIDIITMM', 'IT'),
    ('Banca Fineco', 'FEBIITMM', 'IT'),
    ('Banca Sella', 'SELLITR1', 'IT'),
    ('Carige', 'CRGEITRA', 'IT'),
    ('BNL', 'BNLIITRR', 'IT'),
    ('ING', 'INGBNL2A', 'NL'),
    ('ABN AMRO', 'ABNANL2A', 'NL'),
    ('Rabobank', 'RABONL2U', 'NL'),
    ('ASN Bank', 'ASNBNL21', 'NL'),
    ('RegioBank', 'RBRBNL21', 'NL'),
    ('SNS Bank', 'SNSBNL21', 'NL'),
    ('Triodos Bank', 'TRIONL2U', 'NL'),
    ('Knab', 'KNABNL2A', 'NL'),
    ('Bunq', 'BUNQNL2A', 'NL'),
    ('Van Lanschot', 'FVLBNL22', 'NL'),
    ('KBC', 'KREDBEBB', 'BE'),
    ('Belfius', 'GKCCBEBB', 'BE'),
    ('BNP Paribas Fortis', 'BNPABEBB', 'BE'),
    ('ING Belgium', 'INGBBEBB', 'BE'),
    ('AXA Bank', 'AXABBE22', 'BE'),
    ('Bpost Bank', 'BPOTBE21', 'BE'),
    ('Crelan', 'CRELBE22', 'BE'),
    ('Keytrade Bank', 'KEYTBEBB', 'BE'),
    ('Nagelmackers', 'NAGLBE21', 'BE'),
    ('Erste Bank', 'GIBAATWW', 'AT'),
    ('Raiffeisenbank', 'RZBAATWW', 'AT'),
    ('UniCredit Bank Austria', 'UNCRATWW', 'AT'),
    ('Bank Austria', 'BKAUATWW', 'AT'),
    ('PSK Bank', 'PSKTAT21', 'AT'),
    ('Interactive Brokers', 'IBKRUS33', 'US'),
    ('DEGIRO', 'FIOADEU2XXX', 'DE'),
    ('eToro', 'ETOROUS', 'US'),
    ('XTB', 'XTBIPLP1', 'PL'),
    ('Trading 212', 'TRDNGD21', 'GB'),
    ('Flatex', 'FTKODE51', 'DE'),
    ('Binance', 'BINPCPHH', 'MT'),
    ('Coinbase', 'FINRAUS33', 'US'),
    ('Kraken', 'OTHRUS', 'US'),
    ('Bitpanda', 'BITPATR1', 'AT'),
    ('Crypto.com', 'CRYPTOGB', 'GB')
), matched_institutions AS (
    SELECT i.id, MIN(seed.country) AS country
    FROM institutions i
    JOIN seed_countries seed ON seed.name = TRIM(i.name)
    WHERE i.is_system = TRUE
      AND NULLIF(TRIM(i.country), '') IS NULL
      AND (
          UPPER(TRIM(i.bic)) = seed.bic
          OR NOT EXISTS (
              SELECT 1 FROM seed_countries other
              WHERE other.name = seed.name AND other.country <> seed.country
          )
      )
    GROUP BY i.id
    HAVING COUNT(DISTINCT seed.country) = 1
)
UPDATE institutions
SET country = (
    SELECT country FROM matched_institutions WHERE matched_institutions.id = institutions.id
)
WHERE id IN (SELECT id FROM matched_institutions);
