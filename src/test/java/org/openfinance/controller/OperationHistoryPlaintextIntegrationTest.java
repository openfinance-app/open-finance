package org.openfinance.controller;

import org.springframework.boot.test.context.SpringBootTest;

/** Exercises the same financial reversals when the deployment deliberately disables encryption. */
@SpringBootTest(properties = "application.encryption.enabled=false")
class OperationHistoryPlaintextIntegrationTest extends OperationHistoryControllerIntegrationTest {}
