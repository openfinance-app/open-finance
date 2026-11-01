package org.openfinance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.openfinance.entity.OwnershipOperationReceipt;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.repository.OwnershipOperationReceiptRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OwnershipReceipts {
    private final OwnershipOperationReceiptRepository receipts;
    private final ObjectMapper json;

    public String fingerprint(Object request) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(json.writeValueAsBytes(request)));
        } catch (JsonProcessingException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Cannot identify ownership operation", failure);
        }
    }

    public Long previous(Long userId, UUID operationId, String hash) {
        return receipts.findByUserIdAndOperationId(userId, operationId.toString())
                .map(
                        receipt -> {
                            if (!receipt.getRequestHash().equals(hash)) {
                                throw new InvalidTransactionException(
                                        "This retry identifier was already used for different details");
                            }
                            return receipt.getResultId();
                        })
                .orElse(null);
    }

    public void remember(Long userId, UUID operationId, String hash, Long resultId) {
        OwnershipOperationReceipt receipt = new OwnershipOperationReceipt();
        receipt.setUserId(userId);
        receipt.setOperationId(operationId.toString());
        receipt.setRequestHash(hash);
        receipt.setResultId(resultId);
        receipts.save(receipt);
    }
}
