package org.openfinance.dto;

/** A server-fetched image data URI; null when the provider has no logo. */
public record AssetLogoResponse(String logo) {}
