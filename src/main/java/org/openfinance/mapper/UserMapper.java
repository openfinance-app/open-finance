package org.openfinance.mapper;

import org.mapstruct.Mapper;
import org.openfinance.dto.UserResponse;
import org.openfinance.entity.User;

/**
 * MapStruct mapper for converting between User entity and DTOs.
 *
 * <p>This interface is processed at compile-time by MapStruct to generate implementation code for
 * entity-to-DTO conversions. The generated implementation is managed as a Spring bean.
 *
 * <p><strong>Mapping Strategy:</strong>
 *
 * <ul>
 *   <li><strong>toResponse(User)</strong>: Entity → DTO for API responses (excludes sensitive
 *       fields)
 *   <li><strong>Note:</strong> No toEntity() method - User creation handled manually in UserService
 *       to properly hash passwords and generate salts
 * </ul>
 *
 * <p><strong>Security:</strong> The toResponse() mapping automatically excludes sensitive fields
 * (passwordHash, masterPasswordSalt) because they are not present in the UserResponse DTO.
 *
 * <p>Requirement REQ-2.1.1: User entity-DTO mapping for registration
 *
 * @see org.openfinance.entity.User
 * @see org.openfinance.dto.UserResponse
 * @see org.openfinance.service.UserService
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-01-30
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    /**
     * Converts a User entity to a UserResponse DTO.
     *
     * <p>This method is used after user registration or retrieval to return non-sensitive user
     * information to the client. Sensitive fields like passwordHash and masterPasswordSalt are
     * automatically excluded.
     *
     * <p><strong>Field Mappings:</strong>
     *
     * <ul>
     *   <li>id → id
     *   <li>username → username
     *   <li>email → email
     *   <li>createdAt → createdAt
     *   <li>updatedAt → updatedAt
     * </ul>
     *
     * @param user the User entity to convert (must not be null)
     * @return UserResponse DTO with non-sensitive user information
     */
    UserResponse toResponse(User user);
}
