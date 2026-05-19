package org.openfinance.mapper;

import org.mapstruct.*;
import org.openfinance.dto.TransactionRequest;
import org.openfinance.dto.TransactionResponse;
import org.openfinance.entity.Transaction;

/**
 * MapStruct mapper for converting between Transaction entity and DTOs.
 *
 * <p>This mapper handles bidirectional mapping between:
 *
 * <ul>
 *   <li>{@link TransactionRequest} DTO → {@link Transaction} entity (for create/update)
 *   <li>{@link Transaction} entity → {@link TransactionResponse} DTO (for read)
 * </ul>
 *
 * <p><strong>Note:</strong> Encryption and decryption of sensitive fields (description, notes) are
 * handled in the {@link org.openfinance.service.TransactionService}, not in this mapper.
 *
 * <p>Requirement REQ-2.3: Transaction Management - DTO conversions for CRUD operations
 *
 * @see org.openfinance.entity.Transaction
 * @see org.openfinance.dto.TransactionRequest
 * @see org.openfinance.dto.TransactionResponse
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TransactionMapper {

    /**
     * Converts a TransactionRequest DTO to a Transaction entity.
     *
     * <p>Used when creating a new transaction. The userId must be set separately by the service
     * layer after authentication.
     *
     * <p>The date field from the request is mapped to the transaction's date field.
     *
     * @param request the transaction creation/update request
     * @return a new Transaction entity (without id, userId, timestamps)
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "account", ignore = true)
    @Mapping(target = "toAccount", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "transferId", ignore = true)
    @Mapping(target = "isDeleted", constant = "false")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Transaction toEntity(TransactionRequest request);

    /**
     * Updates an existing Transaction entity from a TransactionRequest DTO.
     *
     * <p>This method updates only the fields that are present in the request, preserving the
     * existing values of id, userId, timestamps, and isDeleted flag.
     *
     * @param request the transaction update request
     * @param transaction the existing transaction entity to update
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "account", ignore = true)
    @Mapping(target = "toAccount", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "transferId", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromRequest(
            TransactionRequest request, @MappingTarget Transaction transaction);

    /**
     * Converts a Transaction entity to a TransactionResponse DTO.
     *
     * <p>Used when returning transaction information to clients. The description and notes fields
     * should already be decrypted by the service layer before calling this method.
     *
     * <p>The denormalized fields (accountName, toAccountName, categoryName, categoryIcon,
     * categoryColor) are not automatically populated and must be set by the service layer.
     *
     * @param transaction the transaction entity
     * @return the transaction response DTO
     */
    @Mapping(target = "accountName", ignore = true)
    @Mapping(target = "toAccountName", ignore = true)
    @Mapping(target = "categoryName", ignore = true)
    @Mapping(target = "categoryIcon", ignore = true)
    @Mapping(target = "categoryColor", ignore = true)
    TransactionResponse toResponse(Transaction transaction);
}
