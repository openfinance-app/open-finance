package org.openfinance.service;

import java.util.List;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.entity.*;
import org.openfinance.repository.*;
import org.openfinance.repository.RecurringTransactionRepository;
import org.openfinance.security.EncryptionService;
import org.openfinance.security.KeyManagementService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Repair runner to detect and re-encrypt plaintext/malformed ciphertext stored in the DB. Disabled
 * by default; enable with property `app.repair.enabled=true`.
 *
 * <p>This is intentionally conservative: by default it runs in "dry-run" mode and only logs
 * candidates. If you enable it with `app.repair.execute=true` it will perform in-place
 * re-encryption for the demo user using `app.repair.demo-master-password` to derive the key.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RepairRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final BudgetRepository budgetRepository;
    private final RecurringTransactionRepository recurringTransactionRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final AssetRepository assetRepository;
    private final RealEstateRepository realEstateRepository;
    private final LiabilityRepository liabilityRepository;
    private final EncryptionService encryptionService;
    private final KeyManagementService keyManagementService;

    @Value("${app.repair.enabled:false}")
    private boolean repairEnabled;

    @Value("${app.repair.execute:false}")
    private boolean repairExecute;

    @Value("${app.repair.demo-master-password:master123}")
    private String demoMasterPassword;

    @Override
    public void run(String... args) throws Exception {
        if (!repairEnabled) return;
        log.warn(
                "RepairRunner is incompatible with JPA AttributeConverter-based encryption. "
                        + "Encryption/decryption is now handled transparently by converters. "
                        + "This runner is disabled and will be removed in a future release.");
    }

    private void repairForUser(User user, SecretKey key) {
        Long userId = user.getId();
        repairBudgets(userId, key);
        repairRecurringTransactions(userId, key);
        repairTransactions(userId, key);
        repairAccounts(userId, key);
        repairAssets(userId, key);
        repairRealEstate(userId, key);
        repairLiabilities(userId, key);
    }

    private boolean looksLikePlaintext(String value) {
        if (value == null) return false;
        if (value.length() < 24) return true; // too short for IV+ciphertext
        if (!value.matches("^[A-Za-z0-9+/=]+$")) return true; // contains non-base64 chars
        return false;
    }

    private boolean canDecrypt(String value, SecretKey key) {
        if (value == null) return true;
        try {
            encryptionService.decrypt(value, key);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void maybeReencryptAndSave(
            Object entity,
            java.util.function.Consumer<Object> saver,
            String fieldName,
            String storedValue,
            SecretKey key) {
        if (storedValue == null) return;
        if (canDecrypt(storedValue, key)) return; // ok

        boolean plaintext = looksLikePlaintext(storedValue);
        if (!plaintext) {
            log.warn(
                    "RepairRunner: stored {} looks encrypted but failed decryption for value='{}'. Skipping to avoid data corruption.",
                    fieldName,
                    abbreviate(storedValue));
            return;
        }

        log.info(
                "RepairRunner: will re-encrypt {} (userId={})",
                fieldName,
                (entity instanceof HasUserId ? ((HasUserId) entity).getUserId() : "?"));
        if (repairExecute) {
            String newEnc = encryptionService.encrypt(storedValue, key);
            // caller should set field on entity and save - we use the provided saver
            saver.accept(newEnc);
        }
    }

    private String abbreviate(String s) {
        if (s == null) return null;
        return s.length() > 40 ? s.substring(0, 36) + "..." : s;
    }

    private void repairBudgets(Long userId, SecretKey key) {
        List<org.openfinance.entity.Budget> budgets = budgetRepository.findByUserId(userId);
        for (var b : budgets) {
            String amt = b.getAmount();
            maybeReencryptAndSave(
                    b, newVal -> b.setAmount((String) newVal), "budget.amount", amt, key);
            if (repairExecute) budgetRepository.save(b);
        }
    }

    private void repairRecurringTransactions(Long userId, SecretKey key) {
        List<org.openfinance.entity.RecurringTransaction> list =
                recurringTransactionRepository.findByUserId(userId);
        for (var r : list) {
            String desc = r.getDescription();
            String notes = r.getNotes();
            maybeReencryptAndSave(
                    r,
                    newVal -> r.setDescription((String) newVal),
                    "recurring.description",
                    desc,
                    key);
            maybeReencryptAndSave(
                    r, newVal -> r.setNotes((String) newVal), "recurring.notes", notes, key);
            if (repairExecute) recurringTransactionRepository.save(r);
        }
    }

    private void repairTransactions(Long userId, SecretKey key) {
        List<org.openfinance.entity.Transaction> list = transactionRepository.findByUserId(userId);
        for (var t : list) {
            String desc = t.getDescription();
            String notes = t.getNotes();
            maybeReencryptAndSave(
                    t,
                    newVal -> t.setDescription((String) newVal),
                    "transaction.description",
                    desc,
                    key);
            maybeReencryptAndSave(
                    t, newVal -> t.setNotes((String) newVal), "transaction.notes", notes, key);
            if (repairExecute) transactionRepository.save(t);
        }
    }

    private void repairAccounts(Long userId, SecretKey key) {
        List<org.openfinance.entity.Account> list = accountRepository.findByUserId(userId);
        for (var a : list) {
            String name = a.getName();
            String accnum = a.getAccountNumber();
            maybeReencryptAndSave(
                    a, newVal -> a.setName((String) newVal), "account.name", name, key);
            maybeReencryptAndSave(
                    a,
                    newVal -> a.setAccountNumber((String) newVal),
                    "account.account_number",
                    accnum,
                    key);
            if (repairExecute) accountRepository.save(a);
        }
    }

    private void repairAssets(Long userId, SecretKey key) {
        List<org.openfinance.entity.Asset> list = assetRepository.findByUserId(userId);
        for (var a : list) {
            String name = a.getName();
            String notes = a.getNotes();
            maybeReencryptAndSave(a, newVal -> a.setName((String) newVal), "asset.name", name, key);
            maybeReencryptAndSave(
                    a, newVal -> a.setNotes((String) newVal), "asset.notes", notes, key);
            if (repairExecute) assetRepository.save(a);
        }
    }

    private void repairRealEstate(Long userId, SecretKey key) {
        List<org.openfinance.entity.RealEstateProperty> list =
                realEstateRepository.findByUserId(userId);
        for (var p : list) {
            maybeReencryptAndSave(
                    p, newVal -> p.setName((String) newVal), "realestate.name", p.getName(), key);
            maybeReencryptAndSave(
                    p,
                    newVal -> p.setAddress((String) newVal),
                    "realestate.address",
                    p.getAddress(),
                    key);
            maybeReencryptAndSave(
                    p,
                    newVal -> p.setPurchasePrice((String) newVal),
                    "realestate.purchase_price",
                    p.getPurchasePrice(),
                    key);
            maybeReencryptAndSave(
                    p,
                    newVal -> p.setCurrentValue((String) newVal),
                    "realestate.current_value",
                    p.getCurrentValue(),
                    key);
            maybeReencryptAndSave(
                    p,
                    newVal -> p.setRentalIncome((String) newVal),
                    "realestate.rental_income",
                    p.getRentalIncome(),
                    key);
            maybeReencryptAndSave(
                    p,
                    newVal -> p.setNotes((String) newVal),
                    "realestate.notes",
                    p.getNotes(),
                    key);
            maybeReencryptAndSave(
                    p,
                    newVal -> p.setDocuments((String) newVal),
                    "realestate.documents",
                    p.getDocuments(),
                    key);
            if (repairExecute) realEstateRepository.save(p);
        }
    }

    private void repairLiabilities(Long userId, SecretKey key) {
        List<org.openfinance.entity.Liability> list =
                liabilityRepository.findByUserIdOrderByCreatedAtDesc(userId);
        for (var l : list) {
            maybeReencryptAndSave(
                    l, newVal -> l.setName((String) newVal), "liability.name", l.getName(), key);
            maybeReencryptAndSave(
                    l,
                    newVal -> l.setPrincipal((String) newVal),
                    "liability.principal",
                    l.getPrincipal(),
                    key);
            maybeReencryptAndSave(
                    l,
                    newVal -> l.setCurrentBalance((String) newVal),
                    "liability.current_balance",
                    l.getCurrentBalance(),
                    key);
            maybeReencryptAndSave(
                    l,
                    newVal -> l.setInterestRate((String) newVal),
                    "liability.interest",
                    l.getInterestRate(),
                    key);
            maybeReencryptAndSave(
                    l,
                    newVal -> l.setMinimumPayment((String) newVal),
                    "liability.minimum_payment",
                    l.getMinimumPayment(),
                    key);
            if (repairExecute) liabilityRepository.save(l);
        }
    }

    // Minimal interface so we can try to show userId in logs when available
    private interface HasUserId {
        Long getUserId();
    }
}
