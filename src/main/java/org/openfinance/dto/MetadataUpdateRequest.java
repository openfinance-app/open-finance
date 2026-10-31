package org.openfinance.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.openfinance.entity.AccountType;
import org.openfinance.entity.PaymentMethod;

/**
 * Partial edits for list bulk actions. Omitted fields are unchanged; empty text clears text.
 * Presence flags distinguish an omitted association from an explicit null (unlink). Financial
 * amounts and movement fields deliberately are not part of these requests.
 */
public final class MetadataUpdateRequest {
    private MetadataUpdateRequest() {}

    @Data
    public static class Asset {
        @Size(max = 1000, message = "{asset.notes.max}")
        private String notes;

        @Positive private Long accountId;
        @JsonIgnore private boolean accountIdPresent;

        @Size(max = 255, message = "{asset.brand.max}")
        private String brand;

        @Size(max = 255, message = "{asset.model.max}")
        private String model;

        public void setAccountId(Long value) {
            accountId = value;
            accountIdPresent = true;
        }
    }

    @Data
    public static class Account {
        private AccountType type;

        @Size(max = 500, message = "{account.description.max}")
        private String description;

        @Positive private Long institutionId;
        @JsonIgnore private boolean institutionIdPresent;

        public void setInstitutionId(Long value) {
            institutionId = value;
            institutionIdPresent = true;
        }
    }

    @Data
    public static class Liability {
        @Size(max = 1000, message = "{liability.notes.max}")
        private String notes;

        @Positive private Long institutionId;
        @JsonIgnore private boolean institutionIdPresent;

        public void setInstitutionId(Long value) {
            institutionId = value;
            institutionIdPresent = true;
        }
    }

    @Data
    public static class Transaction {
        @Size(max = 255, message = "{transaction.description.max}")
        private String description;

        @Size(max = 1000, message = "{transaction.notes.max}")
        private String notes;

        @Size(max = 500, message = "{transaction.tags.max}")
        private String tags;

        @Size(max = 100, message = "{transaction.payee.max}")
        private String payee;

        private Boolean isReconciled;
        private PaymentMethod paymentMethod;
        @JsonIgnore private boolean paymentMethodPresent;
        @Positive private Long categoryId;
        @JsonIgnore private boolean categoryIdPresent;

        public void setCategoryId(Long value) {
            categoryId = value;
            categoryIdPresent = true;
        }

        public void setPaymentMethod(PaymentMethod value) {
            paymentMethod = value;
            paymentMethodPresent = true;
        }
    }

    @Data
    public static class Payee {
        @Positive private Long categoryId;
        @JsonIgnore private boolean categoryIdPresent;

        public void setCategoryId(Long value) {
            categoryId = value;
            categoryIdPresent = true;
        }
    }
}
