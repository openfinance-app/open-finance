package org.openfinance.service;

import java.util.List;
import org.openfinance.dto.LiabilityConversionCandidate;
import org.openfinance.dto.LiabilityConversionRequest;
import org.openfinance.dto.LiabilityResponse;

public interface LiabilityConversionService {
    List<LiabilityConversionCandidate> candidates(Long userId);

    LiabilityConversionPayments.Candidates payments(Long userId, Long accountId, String search);

    LiabilityResponse convert(Long userId, LiabilityConversionRequest request);
}
