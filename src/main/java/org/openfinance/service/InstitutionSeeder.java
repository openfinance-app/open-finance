package org.openfinance.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.entity.Institution;
import org.openfinance.repository.InstitutionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for seeding default EU financial institutions.
 *
 * <p>Creates a standard set of European banks and financial institutions with their BIC (ISO 9362)
 * codes and logo paths. These system institutions can be associated with accounts to help users
 * identify their financial institutions.
 *
 * <p><strong>Supported Countries:</strong>
 *
 * <ul>
 *   <li>France (FR) - Major banks and online platforms
 *   <li>Germany (DE) - Major banks and savings banks
 *   <li>Spain (ES) - Major Spanish banks
 *   <li>Italy (IT) - Major Italian banks
 *   <li>Netherlands (NL) - Major Dutch banks
 *   <li>Belgium (BE) - Major Belgian banks
 *   <li>Austria (AT) - Major Austrian banks
 * </ul>
 *
 * <p><strong>Logos:</strong> Uses local logos from /logos/institutions/ Falls back to default bank
 * icon if logo not available.
 *
 * <p><strong>Integration:</strong> This service runs automatically on application startup via
 * {@link CommandLineRunner}. It is idempotent - can be called multiple times safely.
 *
 * <p>Requirement REQ-2.6.1.3: Predefined Financial Institutions
 *
 * @see Institution
 * @see InstitutionRepository
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InstitutionSeeder implements CommandLineRunner {

    private final InstitutionRepository institutionRepository;

    /** Simple SVG bank icon as default logo. Used for institutions without custom logos. */
    private static final String DEFAULT_BANK_LOGO =
            "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0ibm9uZSIgc3Ryb2tlPSIjMzMzMzMzIiBzdHJva2Utd2lkdGg9IjIiPjxjaXJjbGUgY3g9IjEyIiBjeT0iMTAiIHI9IjEwIi8+PHBhdGggZD0iTTEwIDZIMThWOEgxMFY2eiIvPjxwYXRoIGQ9Ik04IDExSDE2djExSDhWMTF6Ii8+PHBhdGggZD0iTTEwIDExSDE0djExSDEweiIvPjwvc3ZnPg==";

    /**
     * Map of institution names to their local logo paths. Logos are stored in /logos/institutions/
     * directory.
     */
    private static final Map<String, String> LOGO_PATHS = new HashMap<>();

    static {
        // France
        LOGO_PATHS.put("BNP Paribas", "/logos/institutions/bnp-paribas.png");
        LOGO_PATHS.put("Société Générale", "/logos/institutions/societe-generale.png");
        LOGO_PATHS.put("Crédit Agricole", "/logos/institutions/credit-agricole.png");
        LOGO_PATHS.put("LCL", "/logos/institutions/lcl.png");
        LOGO_PATHS.put("Boursorama", "/logos/institutions/boursorama.png");
        LOGO_PATHS.put("Hello bank!", "/logos/institutions/hellobank.png");
        LOGO_PATHS.put("Fortuneo", "/logos/institutions/fortuneo.png");
        LOGO_PATHS.put("Monabanq", "/logos/institutions/monabanq.png");
        LOGO_PATHS.put("Orange Bank", "/logos/institutions/orange-bank.png");
        LOGO_PATHS.put("N26", "/logos/institutions/n26.png");
        LOGO_PATHS.put("Revolut", "/logos/institutions/revolut.png");
        LOGO_PATHS.put("Banque Populaire", "/logos/institutions/banque-populaire.png");
        LOGO_PATHS.put("Caisse d'Epargne", "/logos/institutions/caisse-epargne.png");
        LOGO_PATHS.put("Crédit Mutuel", "/logos/institutions/credit-mutuel.png");
        LOGO_PATHS.put("La Banque Postale", "/logos/institutions/la-banque-postale.png");

        // Germany
        LOGO_PATHS.put("Deutsche Bank", "/logos/institutions/deutsche-bank.png");
        LOGO_PATHS.put("Commerzbank", "/logos/institutions/commerzbank.png");
        LOGO_PATHS.put("Postbank", "/logos/institutions/postbank.png");
        LOGO_PATHS.put("Sparkasse", null); // No logo available
        LOGO_PATHS.put("Volksbank", null); // No logo available
        LOGO_PATHS.put("ING DiBa", "/logos/institutions/ing-diba.png");
        LOGO_PATHS.put("Comdirect", "/logos/institutions/comdirect.png");
        LOGO_PATHS.put("DKB", "/logos/institutions/dkb.png");
        LOGO_PATHS.put("Consorsbank", "/logos/institutions/consorsbank.png");
        LOGO_PATHS.put("Onvista Bank", "/logos/institutions/onvista-bank.png");

        // Spain
        LOGO_PATHS.put("Banco Santander", "/logos/institutions/banco-santander.png");
        LOGO_PATHS.put("BBVA", "/logos/institutions/bbva.png");
        LOGO_PATHS.put("CaixaBank", "/logos/institutions/caixabank.png");
        LOGO_PATHS.put("Banco Sabadell", "/logos/institutions/banco-sabadell.png");
        LOGO_PATHS.put("Banco Popular", null); // No logo available
        LOGO_PATHS.put("ING", "/logos/institutions/ing-spain.png");
        LOGO_PATHS.put("Openbank", "/logos/institutions/openbank.png");
        LOGO_PATHS.put("Evo Banco", "/logos/institutions/evo-banco.png");
        LOGO_PATHS.put("Kutxabank", "/logos/institutions/kutxabank.png");
        LOGO_PATHS.put("Unicaja", "/logos/institutions/unicaja.png");

        // Italy
        LOGO_PATHS.put("Intesa Sanpaolo", "/logos/institutions/intesa-sanpaolo.png");
        LOGO_PATHS.put("UniCredit", "/logos/institutions/unicredit.png");
        LOGO_PATHS.put("Monte dei Paschi di Siena", "/logos/institutions/monte-dei-paschi.png");
        LOGO_PATHS.put("Banco BPM", "/logos/institutions/banco-bpm.png");
        LOGO_PATHS.put("CheBanca!", "/logos/institutions/chebanca.png");
        LOGO_PATHS.put("Widiba", "/logos/institutions/widiba.png");
        LOGO_PATHS.put("Banca Fineco", "/logos/institutions/fineco.png");
        LOGO_PATHS.put("Banca Sella", "/logos/institutions/banca-sella.png");
        LOGO_PATHS.put("Carige", null); // No logo available
        LOGO_PATHS.put("BNL", null); // No logo available

        // Netherlands
        LOGO_PATHS.put("ING", "/logos/institutions/ing-nl.png");
        LOGO_PATHS.put("ABN AMRO", "/logos/institutions/abn-amro.png");
        LOGO_PATHS.put("Rabobank", "/logos/institutions/rabobank.png");
        LOGO_PATHS.put("ASN Bank", null); // No logo available
        LOGO_PATHS.put("RegioBank", null); // No logo available
        LOGO_PATHS.put("SNS Bank", null); // No logo available
        LOGO_PATHS.put("Triodos Bank", "/logos/institutions/triodos.png");
        LOGO_PATHS.put("Knab", null); // No logo available
        LOGO_PATHS.put("Bunq", "/logos/institutions/bunq.png");
        LOGO_PATHS.put("Van Lanschot", "/logos/institutions/van-lanschot.png");

        // Belgium
        LOGO_PATHS.put("KBC", "/logos/institutions/kbc.png");
        LOGO_PATHS.put("Belfius", "/logos/institutions/belfius.png");
        LOGO_PATHS.put("BNP Paribas Fortis", "/logos/institutions/bnp-paribas-fortis.png");
        LOGO_PATHS.put("ING Belgium", "/logos/institutions/ing-belgium.png");
        LOGO_PATHS.put("AXA Bank", "/logos/institutions/axa-bank.png");
        LOGO_PATHS.put("Bpost Bank", "/logos/institutions/bpost-bank.png");
        LOGO_PATHS.put("Crelan", null); // No logo available
        LOGO_PATHS.put("Keytrade Bank", null); // No logo available
        LOGO_PATHS.put("Nagelmackers", null); // No logo available

        // Austria
        LOGO_PATHS.put("Erste Bank", "/logos/institutions/erste-bank.png");
        LOGO_PATHS.put("Raiffeisenbank", "/logos/institutions/raiffeisenbank.png");
        LOGO_PATHS.put("UniCredit Bank Austria", "/logos/institutions/unicredit-austria.png");
        LOGO_PATHS.put("Bank Austria", null); // No logo available
        LOGO_PATHS.put("PSK Bank", null); // No logo available
        LOGO_PATHS.put("Hypo Oberösterreich", null); // No logo available
        LOGO_PATHS.put("Hypo Tirol", null); // No logo available
        LOGO_PATHS.put("S Creditbank", null); // No logo available

        // Investment platforms
        LOGO_PATHS.put("Interactive Brokers", "/logos/institutions/interactive-brokers.png");
        LOGO_PATHS.put("DEGIRO", "/logos/institutions/degiro.png");
        LOGO_PATHS.put("eToro", "/logos/institutions/etoro.png");
        LOGO_PATHS.put("XTB", "/logos/institutions/xtb.png");
        LOGO_PATHS.put("Trading 212", "/logos/institutions/trading-212.png");
        LOGO_PATHS.put("Flatex", "/logos/institutions/flatex.png");

        // Crypto exchanges
        LOGO_PATHS.put("Binance", "/logos/institutions/binance.png");
        LOGO_PATHS.put("Coinbase", "/logos/institutions/coinbase.png");
        LOGO_PATHS.put("Kraken", "/logos/institutions/kraken.png");
        LOGO_PATHS.put("Bitpanda", "/logos/institutions/bitpanda.png");
        LOGO_PATHS.put("Crypto.com", "/logos/institutions/crypto-com.png");
    }

    /**
     * Seeds default EU institutions on application startup. This method is called by Spring Boot
     * automatically.
     */
    @Override
    public void run(String... args) throws Exception {
        seedDefaultInstitutions();
    }

    /**
     * Seeds default institutions if they don't exist. Idempotent operation - safe to call multiple
     * times.
     */
    @Transactional
    public void seedDefaultInstitutions() {
        log.info("Seeding default EU institutions...");

        // Check if already seeded
        long existingCount = institutionRepository.count();
        if (existingCount > 0) {
            log.info("Found {} existing institutions, skipping seed", existingCount);
            return;
        }

        List<Institution> institutions = createDefaultInstitutions();
        institutionRepository.saveAll(institutions);

        log.info("Successfully seeded {} default EU institutions", institutions.size());
    }

    /**
     * Creates list of default EU financial institutions. Includes major banks from France, Germany,
     * Spain, Italy, Netherlands, Belgium, and Austria.
     */
    private List<Institution> createDefaultInstitutions() {
        return List.of(
                // France (FR)
                createInstitution("BNP Paribas", "BNPAFRPP", "FR"),
                createInstitution("Société Générale", "SOGEFRPP", "FR"),
                createInstitution("Crédit Agricole", "AGRIFRPP", "FR"),
                createInstitution("LCL", "LYORFRPP", "FR"),
                createInstitution("Boursorama", "BOUSFRPP", "FR"),
                createInstitution("Hello bank!", "BNPAFRPPXXX", "FR"),
                createInstitution("Fortuneo", "FTTOFRPP", "FR"),
                createInstitution("Monabanq", "MABAFRPP", "FR"),
                createInstitution("Orange Bank", "CDCGFRPP", "FR"),
                createInstitution("N26", "NTSBDEB1", "FR"),
                createInstitution("Revolut", "REVOFRPP", "FR"),
                createInstitution("Banque Populaire", "BPOFRPP", "FR"),
                createInstitution("Caisse d'Epargne", "CEEPFRPP", "FR"),
                createInstitution("Crédit Mutuel", "CMCIFRPP", "FR"),
                createInstitution("La Banque Postale", "PSSTFRPP", "FR"),

                // Germany (DE)
                createInstitution("Deutsche Bank", "DEUTDEFF", "DE"),
                createInstitution("Commerzbank", "COBADEFF", "DE"),
                createInstitution("Postbank", "PBNKDEFF", "DE"),
                createInstitution("Sparkasse", "SPKAT", "DE"),
                createInstitution("Volksbank", "GENODE", "DE"),
                createInstitution("ING DiBa", "INGDDEFF", "DE"),
                createInstitution("Comdirect", "COMPDEDD", "DE"),
                createInstitution("DKB", "BYLADEM1001", "DE"),
                createInstitution("Consorsbank", "CONSDEDD", "DE"),
                createInstitution("Onvista Bank", "AONTDEE1", "DE"),

                // Spain (ES)
                createInstitution("Banco Santander", "BSCHESMM", "ES"),
                createInstitution("BBVA", "BBVAESMM", "ES"),
                createInstitution("CaixaBank", "CAIXESBB", "ES"),
                createInstitution("Banco Sabadell", "BSABESBB", "ES"),
                createInstitution("Banco Popular", "POPULARES", "ES"),
                createInstitution("ING", "INGDESMM", "ES"),
                createInstitution("Openbank", "OPENESMM", "ES"),
                createInstitution("Evo Banco", "EVOBESMM", "ES"),
                createInstitution("Kutxabank", "KUTXESBB", "ES"),
                createInstitution("Unicaja", "UCJAES2M", "ES"),

                // Italy (IT)
                createInstitution("Intesa Sanpaolo", "IBSPITRA", "IT"),
                createInstitution("UniCredit", "UNCRITMM", "IT"),
                createInstitution("Monte dei Paschi di Siena", "PASCITMM", "IT"),
                createInstitution("Banco BPM", "BPMOIT22", "IT"),
                createInstitution("CheBanca!", "CREDITMM", "IT"),
                createInstitution("Widiba", "WIDIITMM", "IT"),
                createInstitution("Banca Fineco", "FEBIITMM", "IT"),
                createInstitution("Banca Sella", "SELLITR1", "IT"),
                createInstitution("Carige", "CRGEITRA", "IT"),
                createInstitution("BNL", "BNLIITRR", "IT"),

                // Netherlands (NL)
                createInstitution("ING", "INGBNL2A", "NL"),
                createInstitution("ABN AMRO", "ABNANL2A", "NL"),
                createInstitution("Rabobank", "RABONL2U", "NL"),
                createInstitution("ASN Bank", "ASNBNL21", "NL"),
                createInstitution("RegioBank", "RBRBNL21", "NL"),
                createInstitution("SNS Bank", "SNSBNL21", "NL"),
                createInstitution("Triodos Bank", "TRIONL2U", "NL"),
                createInstitution("Knab", "KNABNL2A", "NL"),
                createInstitution("Bunq", "BUNQNL2A", "NL"),
                createInstitution("Van Lanschot", "FVLBNL22", "NL"),

                // Belgium (BE)
                createInstitution("KBC", "KREDBEBB", "BE"),
                createInstitution("Belfius", "GKCCBEBB", "BE"),
                createInstitution("BNP Paribas Fortis", "BNPABEBB", "BE"),
                createInstitution("ING Belgium", "INGBBEBB", "BE"),
                createInstitution("AXA Bank", "AXABBE22", "BE"),
                createInstitution("Bpost Bank", "BPOTBE21", "BE"),
                createInstitution("Crelan", "CRELBE22", "BE"),
                createInstitution("Keytrade Bank", "KEYTBEBB", "BE"),
                createInstitution("Nagelmackers", "NAGLBE21", "BE"),

                // Austria (AT)
                createInstitution("Erste Bank", "GIBAATWW", "AT"),
                createInstitution("Raiffeisenbank", "RZBAATWW", "AT"),
                createInstitution("UniCredit Bank Austria", "UNCRATWW", "AT"),
                createInstitution("Bank Austria", "BKAUATWW", "AT"),
                createInstitution("PSK Bank", "PSKTAT21", "AT"),

                // Investment platforms
                createInstitution("Interactive Brokers", "IBKRUS33", "US"),
                createInstitution("DEGIRO", "FIOADEU2XXX", "DE"),
                createInstitution("eToro", "ETOROUS", "US"),
                createInstitution("XTB", "XTBIPLP1", "PL"),
                createInstitution("Trading 212", "TRDNGD21", "UK"),
                createInstitution("Flatex", "FTKODE51", "DE"),

                // Crypto exchanges
                createInstitution("Binance", "BINPCPHH", "MT"),
                createInstitution("Coinbase", "FINRAUS33", "US"),
                createInstitution("Kraken", "OTHRUS", "US"),
                createInstitution("Bitpanda", "BITPATR1", "AT"),
                createInstitution("Crypto.com", "CRYPTOGB", "UK"));
    }

    /**
     * Creates an Institution entity with local logo path. Uses predefined logo path from LOGO_PATHS
     * map, falls back to default icon.
     *
     * @param name institution name
     * @param bic BIC code (ISO 9362)
     * @param country country code (ISO 3166-1 alpha-2)
     * @return Institution entity
     */
    private Institution createInstitution(String name, String bic, String country) {
        String logo = LOGO_PATHS.getOrDefault(name, DEFAULT_BANK_LOGO);

        // If logo is explicitly set to null in the map, use default
        if (logo == null && LOGO_PATHS.containsKey(name)) {
            logo = DEFAULT_BANK_LOGO;
        }

        return Institution.builder()
                .name(name)
                .bic(bic)
                .country(country)
                .logo(logo)
                .isSystem(true)
                .build();
    }
}
