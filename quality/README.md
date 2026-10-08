# Required quality checks

CI requires backend `spotless:check verify`, frontend tests/build/lint/Prettier,
SpotBugs, Dependency-Check, npm audit, and packaged Chromium/mobile tests. The tag
publishing workflow depends on the reusable SQLite, PostgreSQL, frontend and
browser workflows. A failed required job prevents publishing.

## SpotBugs baseline

`spotbugs-baseline.xml` records exact BugInstance identities, not package or bug-type
exclusions. It started from the unmodified audit commit
`30b66a580cd0efef19218b4595123ad4f6e39b38`: 852 findings, of which 680 were mutable
reference warnings, predominantly Spring injection and Lombok/JPA accessors. The
remaining legacy findings include locale-sensitive case conversion, broad catches,
unused private methods, formatting, and conservative nullability/lifecycle warnings.
They remain technical debt; a passing baseline check is not a claim of zero warnings.

The remediation also fixes two JDBC Statement resource leaks. Ten exact constructor
findings were reviewed after adding constructor dependencies and the recurrence
anchor. They retain Spring-managed services/repositories/configuration, or a JPA
currency association, by reference as required by those lifecycles. No category-wide
exception is used. Existing identities are retained so the original audit baseline
remains traceable; some are now obsolete after fixes/signature changes.

The second finance correction round reviewed 58 further exact constructor identities:
53 are the same fields retained by constructors whose signatures changed; five are
new Spring-managed collaborators for recurring-account closure, property cost basis,
and shared dashboard calculations. The null-exception catch and redundant boxing
reported in new code were fixed. No package-wide or bug-type-wide filter was added.

Run `mvn compile spotbugs:check`. A new identity fails the check. Review it against
source and fix real defects; do not regenerate this file from current output just
to make CI pass.

The fourth finance correction round reviewed 24 exact reference identities: 16
existing Spring fields whose constructor signatures changed, four intentionally
shared collaborators in the atomic purchase service, and four accessors for the
request-scoped, validated nested purchase DTOs. The new broad catch and two dead
stores were fixed in source. An independent unmodified-HEAD run still reports 64
unmatched existing findings; these were not added to the baseline by this change.

The shared-category change reviewed nine exact constructor identities: six are existing
mutable DTO/JPA associations or Spring collaborators whose constructor signatures
changed when category types were removed; three are the new category activity
service's injected repositories and exchange-rate service. These collaborators must
retain their Spring-managed identity. Four obsolete local assignments were removed
from validation code. An independent build of unmodified HEAD still reports 92
unmatched findings; those remain outside this change and were not added to the baseline.

The 0.5.1-beta release review independently reproduced those 92 findings on main.
It fixes locale-dependent property-name sorting, nullable JDBC aggregate handling,
and the insight-generation result's mutable lists. The remaining 85 exact identities
were reviewed: 64 constructor fields retain Spring-managed collaborators, two retain
managed JPA user associations, and three preserve the mutable import-review DTO's
existing constructor contract. Sixteen constructor/accessor identities belong to
operation-scoped history assembly records: they intentionally share captured rows,
references, and change sets within one operation; import undo normalizes the captured
status before serialization. These records do not represent immutable public values.
The baseline retains each reviewed identity individually. No bug type, package, or
quality job is excluded, and any unreviewed identity still fails the release gate.

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
