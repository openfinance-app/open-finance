package org.openfinance.service;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "application.encryption.enabled=false")
class AuditPlaintextSessionRevocationIntegrationTest
        extends AuditSessionRevocationIntegrationTest {}
