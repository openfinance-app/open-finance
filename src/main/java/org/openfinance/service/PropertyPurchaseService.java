package org.openfinance.service;

import org.openfinance.dto.PropertyPurchaseRequest;
import org.openfinance.dto.PropertyPurchaseResponse;

public interface PropertyPurchaseService {
    PropertyPurchaseResponse purchase(Long userId, PropertyPurchaseRequest request);
}
