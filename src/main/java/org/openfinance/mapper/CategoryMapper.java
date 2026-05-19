package org.openfinance.mapper;

import org.mapstruct.*;
import org.openfinance.dto.CategoryRequest;
import org.openfinance.dto.CategoryResponse;
import org.openfinance.entity.Category;

/**
 * MapStruct mapper for converting between Category entity and DTOs.
 *
 * <p>This mapper handles bidirectional mapping between:
 *
 * <ul>
 *   <li>{@link CategoryRequest} DTO → {@link Category} entity (for create/update)
 *   <li>{@link Category} entity → {@link CategoryResponse} DTO (for read)
 * </ul>
 *
 * <p><strong>Note:</strong> Encryption and decryption of the name field are handled in the {@link
 * org.openfinance.service.CategoryService}, not in this mapper.
 *
 * <p>Requirement REQ-2.4: Category Management - DTO conversions for CRUD operations
 *
 * @see org.openfinance.entity.Category
 * @see org.openfinance.dto.CategoryRequest
 * @see org.openfinance.dto.CategoryResponse
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CategoryMapper {

    /**
     * Converts a CategoryRequest DTO to a Category entity.
     *
     * <p>Used when creating a new category. The userId must be set separately by the service layer
     * after authentication.
     *
     * @param request the category creation/update request
     * @return a new Category entity (without id, userId, timestamps)
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "parent", ignore = true)
    @Mapping(target = "subcategories", ignore = true)
    @Mapping(target = "isSystem", constant = "false")
    @Mapping(target = "nameKey", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Category toEntity(CategoryRequest request);

    /**
     * Updates an existing Category entity from a CategoryRequest DTO.
     *
     * <p>This method updates only the fields that are present in the request, preserving the
     * existing values of id, userId, timestamps, and isSystem flag.
     *
     * @param request the category update request
     * @param category the existing category entity to update
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "parent", ignore = true)
    @Mapping(target = "subcategories", ignore = true)
    @Mapping(target = "isSystem", ignore = true)
    @Mapping(target = "nameKey", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromRequest(CategoryRequest request, @MappingTarget Category category);

    /**
     * Converts a Category entity to a CategoryResponse DTO.
     *
     * <p>Used when returning category information to clients. The name field should already be
     * decrypted by the service layer before calling this method.
     *
     * <p>The subcategoryCount field is not automatically populated and must be set by the service
     * layer if needed.
     *
     * @param category the category entity
     * @return the category response DTO
     */
    @Mapping(target = "parentName", ignore = true)
    @Mapping(target = "subcategoryCount", ignore = true)
    CategoryResponse toResponse(Category category);
}
