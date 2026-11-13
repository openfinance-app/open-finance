package org.openfinance.service;

import java.util.List;
import org.openfinance.dto.PropertyConversionCandidate;
import org.openfinance.dto.PropertyConversionRequest;
import org.openfinance.dto.PropertyPurchaseResponse;

public interface PropertyConversionService {
    List<PropertyConversionCandidate> candidates(Long userId);

    PropertyPurchaseResponse convert(Long userId, PropertyConversionRequest request);
}
