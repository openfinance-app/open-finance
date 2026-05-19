package org.openfinance.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;
import org.openfinance.dto.AccountRequest;
import org.openfinance.dto.AccountResponse;
import org.openfinance.entity.Account;

/**
 * MapStruct mapper for converting between Account entity and DTOs.
 *
 * <p>This mapper handles bidirectional mapping between:
 *
 * <ul>
 *   <li>{@link AccountRequest} DTO → {@link Account} entity (for create/update)
 *   <li>{@link Account} entity → {@link AccountResponse} DTO (for read)
 * </ul>
 *
 * <p><strong>Note:</strong> Encryption and decryption of sensitive fields (name, description) are
 * handled in the {@link org.openfinance.service.AccountService}, not in this mapper.
 *
 * <p>Requirement REQ-2.2: Account Management - DTO conversions for CRUD operations
 *
 * @see org.openfinance.entity.Account
 * @see org.openfinance.dto.AccountRequest
 * @see org.openfinance.dto.AccountResponse
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AccountMapper {

    /**
     * Converts an AccountRequest DTO to an Account entity.
     *
     * <p>Used when creating a new account. The userId must be set separately by the service layer
     * after authentication.
     *
     * <p>The balance field is mapped from {@code initialBalance} in the request.
     *
     * @param request the account creation/update request
     * @return a new Account entity (without id, userId, timestamps)
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "balance", source = "initialBalance")
    @Mapping(target = "openingBalance", source = "initialBalance")
    @Mapping(target = "openingDate", source = "openingDate")
    @Mapping(target = "isActive", constant = "true")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "interestRateVariations", ignore = true)
    @Mapping(target = "isInterestEnabled", source = "isInterestEnabled", defaultValue = "false")
    Account toEntity(AccountRequest request);

    /**
     * Updates an existing Account entity from an AccountRequest DTO.
     *
     * <p>This method updates only the fields that are present in the request, preserving the
     * existing values of id, userId, timestamps, and isActive.
     *
     * <p>The balance field is updated from {@code initialBalance} in the request.
     *
     * @param request the account update request
     * @param account the existing account entity to update
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "balance", source = "initialBalance")
    @Mapping(target = "openingBalance", source = "initialBalance")
    @Mapping(target = "openingDate", source = "openingDate")
    @Mapping(target = "isActive", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "interestRateVariations", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromRequest(AccountRequest request, @MappingTarget Account account);

    /**
     * Converts an Account entity to an AccountResponse DTO.
     *
     * <p>Used when returning account information to clients. The name and description fields should
     * already be decrypted by the service layer before calling this method.
     *
     * @param account the account entity
     * @return the account response DTO
     */
    AccountResponse toResponse(Account account);
}
