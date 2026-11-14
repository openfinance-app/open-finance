# CI checks

CI runs backend `spotless:check verify`, frontend tests/build/lint/Prettier,
Dependency-Check, npm audit, and packaged Chromium/mobile tests. The tag
publishing workflow depends on the reusable SQLite, PostgreSQL, frontend and
browser workflows. A failed required job prevents publishing.

## Dependency applicability reviews

`dependency-check-suppressions.xml` identifies individual CVEs and exact artifact
versions. Every entry has its applicability reason, an advisory link and a
2026-12-20 expiry. The reviewed Spring issues require facilities absent from this
application: reactive servers, RSocket, WebAuthn, embedded LDAP, vulnerable view
rendering, user-controlled SpEL, self-populating data-binding lists, native sorted
queries, or specific legacy authentication/encryption components. Reactor Netty
is an outbound client here; Tomcat serves HTTP. One LangChain finding is a Python
package matched to an unrelated Java artifact.

The 2026-10-08 release review also checks CVE-2026-41707 against the packaged JAR
and authentication configuration. The application uses its local JWT/password
authentication, with no OAuth2 resource server or DPoP configuration. No
`spring-security-oauth2` module or vulnerable `DPoPProofJwtDecoderFactory` is
packaged. The finding for the four packaged Spring Security 6.5.11 artifacts is
therefore recorded as not applicable, with the same review expiry. Recheck before
enabling DPoP.

These are applicability decisions, not upstream fixes. Adding an affected facility,
changing the server architecture, or changing a dependency version requires review.
Spring Framework 6.2 and Spring Security 6.5 have advisories whose fixes are in the
newer OSS major or commercial maintenance lines. Plan the next platform upgrade;
do not extend the review expiry without checking current advisories and code.

`mvn dependency-check:check` downloads official NVD bulk feeds and uses the Java and
JavaScript analyzers, including RetireJS. The .NET analyzer is disabled because this
build contains no .NET assemblies. OSS Index is disabled because it requires a
separate authenticated service; NVD/RetireJS remain required. The gate fails for an
unreviewed CVSS score of 7 or higher, or scanner/update errors. Offline execution
against stale data is not equivalent to a fresh release scan.

## Packaged browser checks

After `npm ci` and `npx playwright install --with-deps chromium` in `openfinance-ui`,
run `python3 scripts/run-browser-tests.py` from the repository root. It builds the
UI and JAR, serves them with encryption enabled, creates only synthetic accounts in
an owned temporary database, runs Playwright without retries, and stops the process.
Reports and failed-test traces are under `target/browser-tests/`.
