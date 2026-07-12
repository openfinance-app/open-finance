# Security Policy

> Open-Finance security architecture, master password model, data privacy, and vulnerability reporting.

## Security Overview

| Control                | Implementation                                             |
| ---------------------- | ---------------------------------------------------------- |
| **Authentication**     | Stateless JWT (HS256), 24 h expiry                         |
| **Password hashing**   | BCrypt (cost factor 10)                                    |
| **Data encryption**    | AES-256-GCM; PBKDF2-HMAC-SHA-256, 100 000 iterations       |
| **Password policy**    | ≥ 8 chars: uppercase, lowercase, digit, special character  |
| **Account lockout**    | 5 failed attempts → 15-minute lockout                      |
| **Rate limiting**      | 200 req/min general; 10 req/min on auth + upload endpoints |
| **Security headers**   | CSP, HSTS, X-Frame-Options (DENY), X-Content-Type-Options  |
| **Input sanitization** | XSS pattern stripping on all free-text fields              |
| **SQL injection**      | JPA/Hibernate parameterized queries throughout             |
| **Audit logging**      | All auth events logged to `security_audit_log`             |
| **CSRF**               | N/A — stateless REST + JWT; no cookies                     |

---

## Security Philosophy

Open-Finance operates on a **local-first, privacy-by-default** model:

- **No cloud storage** — data never leaves the device
- **Encryption at rest** — sensitive fields and attachments encrypted with AES-256-GCM
- **Zero-knowledge design** — the application cannot decrypt data without the master password
- **Minimal external calls** — optional market-data and exchange-rate lookups only; all opt-in
- **Open source** — the full source code is publicly auditable

---

## Encryption Architecture

### Field-Level Encryption

Sensitive database fields (account numbers, notes, attachment content) are encrypted at the application layer before being written to SQLite.

| Property       | Value                                          |
| -------------- | ---------------------------------------------- |
| Algorithm      | AES-256-GCM (authenticated encryption)         |
| Key size       | 256 bits                                       |
| IV             | 12 bytes (96-bit nonce, random per encryption) |
| Auth tag       | 128 bits                                       |
| Key derivation | PBKDF2-HMAC-SHA-256, 100 000 iterations        |
| Salt           | 16 bytes, random per key derivation            |

AES-GCM provides both **confidentiality** and ciphertext **integrity** — any tampering is detected on decryption.

### Encryption Mode Configuration

Field-level encryption is enabled by default:

```yaml
application:
    encryption:
        enabled: true
```

Set `application.encryption.enabled=false` before first startup only if the deployment intentionally stores supported fields in plaintext and does not use the master-password/session-key flow.

When encryption is disabled:

- Registration and login do not require a master password
- Login responses do not include an encryption session token
- The frontend omits `X-Encryption-Session` headers
- JPA converters pass field values through without encrypting or decrypting them

> **Unsupported mode switching**: changing `application.encryption.enabled` after users or financial data exist is unsupported. Open-Finance does not migrate existing data between encrypted and plaintext forms, does not validate mixed-mode databases, and does not repair data written under the previous mode.

### Attachment Encryption

File attachments are encrypted with the same AES-256-GCM scheme before being written to `./attachments/`. The database stores only a reference (path/ID) and encrypted metadata.

### Database-Level Security

The SQLite file (`openfinance.db`) uses application-layer encryption by default. For full filesystem encryption:

- **SQLCipher**: optional dependency for full database-file encryption
- **File permissions**: `chmod 600 openfinance.db` (restrict to the application user)

---

## Master Password

### Purpose

The master password is a **separate credential** from the login password. It derives the AES-256-GCM key for:

- Field-level encryption (account numbers, notes)
- Attachment encryption
- Backup archives

### How It Works

- The login password authenticates you (verified via BCrypt hash in the database)
- The master password **never leaves the device** and is **never stored** — only its derived key is held temporarily in memory during a session
- An attacker who obtains the database file cannot decrypt sensitive fields without the master password

### Changing the Master Password

Changing the master password re-encrypts all sensitive data atomically:

1. Decrypt all data with the old key
2. Re-encrypt all data with the new key
3. Commit as a single transaction (all-or-nothing)

Navigate to **Settings → Security → Change Master Password**.

### Lost Master Password

> ⚠️ Encrypted data **cannot be recovered** without the master password. This is by design.

Without the master password you can still log in, view and import unencrypted data (accounts, categories, budgets, reports), but you cannot view encrypted field values, open encrypted attachments, or restore encrypted backups.

**Recommendation**: store the master password in a password manager or physical safe.

---

## Authentication & Session Security

### Password Hashing

Login passwords are hashed with **BCrypt** (cost factor 10). Each hash includes a unique random salt, defeating rainbow-table and precomputation attacks.

### JSON Web Tokens

| Property     | Value                                        |
| ------------ | -------------------------------------------- |
| Algorithm    | HS256 (HMAC-SHA-256)                         |
| Expiration   | 24 hours (configurable via `jwt.expiration`) |
| Issuer claim | Included                                     |
| Storage      | Browser `localStorage`                       |

> **Production requirement**: set `jwt.secret` to a cryptographically random value of at least 256 bits. The repository default is for development only.

```bash
# Generate a 512-bit secret
openssl rand -base64 64
```

### Force-Logout

There is no server-side token blocklist. To invalidate all active sessions, rotate the `jwt.secret` — all existing tokens become immediately invalid.

---

## Account Lockout & Brute-Force Protection

| Setting             | Default | Config key                                      |
| ------------------- | ------- | ----------------------------------------------- |
| Max failed attempts | 5       | `application.security.max-failed-attempts`      |
| Lockout duration    | 15 min  | `application.security.lockout-duration-minutes` |

After 5 consecutive failures the account is locked; requests during the lockout period return `423 Locked` with the unlock time. The counter resets on successful login and persists across restarts.

### Security Audit Log

Every authentication event is written to `security_audit_log`:

| Column       | Description                                                              |
| ------------ | ------------------------------------------------------------------------ |
| `event_type` | `LOGIN_SUCCESS` · `LOGIN_FAILED` · `ACCOUNT_LOCKED` · `PASSWORD_CHANGED` |
| `user_id`    | Related user                                                             |
| `ip_address` | Client IP                                                                |
| `user_agent` | Browser / client string                                                  |
| `timestamp`  | Event time (UTC)                                                         |
| `details`    | Event-specific metadata                                                  |

---

## API Hardening

### Security Headers

| Header                      | Value                                      | Purpose                         |
| --------------------------- | ------------------------------------------ | ------------------------------- |
| `Content-Security-Policy`   | `default-src 'self'; script-src 'self'; …` | XSS / data-injection prevention |
| `X-Frame-Options`           | `DENY`                                     | Clickjacking prevention         |
| `X-Content-Type-Options`    | `nosniff`                                  | MIME-type sniffing prevention   |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains`      | Enforces HTTPS                  |
| `Referrer-Policy`           | `strict-origin-when-cross-origin`          | Controls referrer leakage       |
| `Permissions-Policy`        | `camera=(), microphone=(), geolocation=()` | Restricts browser APIs          |

### CSRF

CSRF protection is intentionally omitted. The API is stateless, uses JWT in `Authorization` headers (not cookies), and browser cross-origin requests do not automatically include JWT tokens — the CSRF attack surface does not apply.

### Input Sanitization

All free-text inputs pass through `XssSanitizer` before processing:

- Strips HTML/script tags (`<script>`, `<iframe>`, …)
- Removes dangerous event attributes (`onload`, `onclick`, …)
- Removes `javascript:` URI schemes

SQL injection is prevented by JPA/Hibernate parameterized queries — raw SQL with user input is never constructed.

### Rate Limiting

Implemented with **Bucket4j** (token bucket algorithm):

| Scope               | Limit        | Window         |
| ------------------- | ------------ | -------------- |
| `/api/v1/auth/**`   | 10 requests  | 1 min per IP   |
| `/api/v1/files/**`  | 10 requests  | 1 min per IP   |
| All other endpoints | 200 requests | 1 min per user |

Responses when exceeded: `429 Too Many Requests` with a `Retry-After` header.

---

## Backup Security

Backups are encrypted ZIP archives; the key is derived from the master password via PBKDF2.

- **Never share backup files** without first rotating the master password
- **Store off-device**: external drive, USB, or separate encrypted cloud storage
- **Test restores** periodically before you need them
- **Rotate**: keep multiple dated backups; never overwrite the only copy

Each backup includes a content checksum. Open-Finance verifies it on restore and rejects tampered or corrupted archives.

---

## Data Privacy & External Calls

### What Leaves the Device

| Service                          | Data sent                          | Trigger                                  |
| -------------------------------- | ---------------------------------- | ---------------------------------------- |
| Yahoo Finance (yfinance wrapper) | Ticker symbols only (e.g., `AAPL`) | Live price fetch                         |
| Ollama (local)                   | Financial context + question       | AI chat — local, no internet             |
| OpenAI (optional)                | Financial context + question       | Only if `provider: openai` is configured |

### Disabling External Calls (air-gapped mode)

```yaml
application:
    market-data:
        yahoo-finance-url: "" # disable
    ai:
        provider: ollama # keep local; do not set provider: openai
```

### GDPR / Data Subject Rights

All data is stored locally on the user's device:

- **Right to access**: `openfinance.db` + `./attachments/`
- **Right to erasure**: delete the database file and attachments directory
- **Right to portability**: CSV / PDF export

No personal data is transmitted to or processed by the Open-Finance project maintainers.

---

## Best Practices for Users

1. **Strong, unique login password** — ≥ 8 characters with uppercase, lowercase, digit, and special character
2. **Separate master password** — use a different, strong passphrase; store in a password manager or physical safe
3. **Enable device screen lock** — prevents unauthorized physical access
4. **Regular encrypted backups** — store off-device
5. **Keep the application updated** — apply new releases to receive security patches
6. **Log out on shared machines** — clears the JWT immediately from the browser
7. **Avoid public API exposure** — designed for `localhost`; use a VPN or SSH tunnel for remote access

---

## Best Practices for Self-Hosters

1. **Rotate the JWT secret** — never run the repository default in production
2. **Use HTTPS** — place nginx or Caddy with TLS in front of the application
3. **Enable SQLCipher** — for full database-file encryption
4. **Least-privilege user** — run as a dedicated non-root OS user; `chmod 700` the data directory
5. **Monitor the audit log** — watch `security_audit_log` for suspicious patterns
6. **Firewall** — restrict port 8080 to trusted IPs only
7. **Automated backups** — schedule regular encrypted backups to a secure location

---

## Vulnerability Reporting

**Do not open a public GitHub issue for security vulnerabilities.**

Report privately via the [GitHub Security Advisories](https://github.com/open-finance/open-finance/security/advisories) tab.

**Include in your report:**

- Description of the vulnerability
- Steps to reproduce
- Potential impact assessment
- Suggested fix (optional)

**Disclosure timeline:**

- Initial response: ≤ 14 days
- Patch target: ≤ 90 days from report

We credit researchers in the release notes following a responsible disclosure period.

---

_Last updated: April 2026 | Open-Finance v0.1.0_
