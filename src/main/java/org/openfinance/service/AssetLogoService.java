package org.openfinance.service;

import org.openfinance.entity.AssetType;

public interface AssetLogoService {
    /** Resolves a logo without changing the asset; null means no logo is available. */
    String resolveLogo(AssetType type, String symbol, String name);
}
