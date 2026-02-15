# X-Road Codebase Review

**Date:** 2026-02-15
**Scope:** Full codebase review covering architecture, security, code quality, frontend, build system, and testing
**Codebase Version:** Based on latest `develop` branch (commit c0594f21)

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Architecture Summary](#2-architecture-summary)
3. [Security Findings](#3-security-findings)
4. [Code Quality Findings](#4-code-quality-findings)
5. [Frontend Review](#5-frontend-review)
6. [Build System & Dependencies](#6-build-system--dependencies)
7. [Test Infrastructure](#7-test-infrastructure)
8. [Docker & Deployment](#8-docker--deployment)
9. [Recommendations Summary](#9-recommendations-summary)

---

## 1. Project Overview

X-Road is an open-source data exchange layer providing unified and secure data exchange between organizations. Maintained by the Nordic Institute for Interoperability Solutions (NIIS), currently at version 7.8.0-SNAPSHOT.

**Technology Stack:**
| Component | Technology |
|-----------|-----------|
| Backend | Java 21 + Spring Boot 3.5.4 |
| Messaging | X-Road protocol + gRPC 1.74.0 |
| Persistence | PostgreSQL + Hibernate 7.0.9 + Liquibase 4.33.0 |
| Frontend | Vue.js 3 + TypeScript + Vite 7 + Vuetify 3 |
| Crypto | BouncyCastle 1.81 |
| Build | Gradle 8.12 (131+ subprojects) |
| CI/CD | GitHub Actions |
| Code Quality | SonarQube + JaCoCo + Checkstyle + ArchUnit |

---

## 2. Architecture Summary

The project follows a well-organized modular architecture with clear separation of concerns:

- **`src/common/`** - 15 shared library modules (core, DB, messaging, admin API, RPC, etc.)
- **`src/service/`** - Backend services (proxy, signer, configuration-client, monitor, op-monitor, message-log-archiver)
- **`src/central-server/`** - Central Server admin service, management service, registration service
- **`src/security-server/`** - Security Server admin service with Vue.js UI
- **`src/lib/`** - Library projects (ASiC, globalconf, serverconf, keyconf)
- **`src/addons/`** - Plugin modules (hwtoken, messagelog, metaservice, wsdlvalidator, op-monitoring)
- **`src/shared-ui/`** - Reusable Vue.js component library
- **`Docker/`** - Container images for Central Server, Security Server, test CA
- **`ansible/`** - 17 Ansible roles for automated deployment

**Strengths:** Clear module boundaries, layered architecture (core API -> infrastructure -> application), plugin-based extensibility, comprehensive documentation.

---

## 3. Security Findings

### 3.1 CRITICAL: XPath Injection

**File:** `src/common/common-message/src/main/java/ee/ria/xroad/common/util/XmlUtils.java:252`

```java
return (Element) xpath.evaluate("//*[@Id = '" + id + "']", doc, XPathConstants.NODE);
```

The `getElementById()` method constructs an XPath expression via string concatenation with the `id` parameter. An attacker supplying `id = "' or '1'='1"` could manipulate XML element selection, leading to information disclosure or unauthorized data access.

**Recommendation:** Use XPath variable binding or sanitize the `id` parameter to disallow `'` characters.

---

### 3.2 CRITICAL: Insecure Random Number Generation for Cryptographic Key IDs

**File:** `src/service/signer/signer-core/src/main/java/org/niis/xroad/signer/core/util/SignerUtil.java:131`

```java
public static byte[] generateId() {
    byte[] id = new byte[RANDOM_ID_LENGTH];
    new Random().nextBytes(id);  // INSECURE - predictable
    return id;
}
```

Uses `java.util.Random` instead of `java.security.SecureRandom` for generating cryptographic key IDs. `Random` uses a linear congruential generator that is predictable and unsuitable for security-sensitive operations.

**Recommendation:** Replace `new Random()` with `new SecureRandom()`.

---

### 3.3 HIGH: Disabled TLS Certificate Verification

**Files:**
- `src/addons/wsdlvalidator/src/main/java/ee/ria/xroad/wsdlvalidator/WSDLValidator.java:132` - Sets default hostname verifier to always return `true`
- `src/service/configuration-client/configuration-client-core/src/main/java/org/niis/xroad/confclient/core/HttpUrlConnectionConfigurer.java:57-69` - Uses `NoopTrustManager` and `NoopHostnameVerifier`

Both locations disable TLS certificate verification, enabling man-in-the-middle attacks. The configuration-client code does have feature flags (`isHostNameVerificationEnabled()`, `isTlsCertificationVerificationEnabled()`) controlling this behavior, but the defaults should be verified to ensure verification is enabled.

**Recommendation:** Ensure TLS verification is enabled by default. Consider removing the WSDLValidator global hostname verifier override entirely or scoping it to a specific connection.

---

### 3.4 HIGH: Empty `checkServerTrusted()` in AuthTrustManager

**File:** `src/service/proxy/proxy-core/src/main/java/org/niis/xroad/proxy/core/auth/AuthTrustManager.java:74-78`

```java
public void checkServerTrusted(X509Certificate[] certs, String authType)
        throws CertificateException {
    // Check for the certificates later in AuthTrustVerifier
}
```

The empty implementation means no server certificate validation occurs at the TLS handshake level. While the comment indicates deferred validation, this is a dangerous pattern - if `AuthTrustVerifier` is bypassed or fails to run, connections would be accepted without any certificate checks.

**Recommendation:** Add defensive validation or at minimum log a warning when this method is called. Document the exact code path where `AuthTrustVerifier` is invoked.

---

### 3.5 HIGH: Password Store Default Path in World-Writable Directory

**File:** `src/service/signer/signer-api/src/main/java/ee/ria/xroad/common/util/FilePasswordStoreProvider.java:93`

```java
return System.getProperty(CFG_FILE_PASSWORD_STORE_PATH, "/tmp/xroad/passwordstore/");
```

The default password store path is under `/tmp/`, which is world-writable on most Linux systems. This could allow unauthorized users to read or tamper with stored passwords.

**Recommendation:** Change the default path to a restricted directory like `/var/lib/xroad/passwordstore/` with appropriate file permissions.

---

### 3.6 MEDIUM: CSRF Cookie HttpOnly Disabled

**File:** `src/common/common-admin-api/src/main/java/org/niis/xroad/restapi/auth/securityconfigurer/CookieAndSessionCsrfTokenRepository.java:59`

```java
cookieCsrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
```

CSRF tokens are accessible via JavaScript, making them vulnerable to theft via XSS. This is intentional (needed for JavaScript-based CSRF submission), but increases exposure if an XSS vulnerability is introduced.

**Mitigating factor:** The codebase also uses `HttpSessionCsrfTokenRepository` for dual validation, which reduces the risk. The `SameSite` cookie attribute is also configured.

---

### 3.7 POSITIVE: Good Security Practices Observed

- **XXE Protection:** XML parsing properly hardened with DOCTYPE disallowed, external entities disabled, secure processing enabled
- **SQL Injection Prevention:** JPA/Hibernate with parameterized queries used consistently
- **CSRF Protection:** Dual cookie + session token validation
- **Spring Security:** Proper authentication provider integration with permission-based access control
- **Process Execution:** `ProcessBuilder` with argument arrays (not shell string concatenation) in most locations

---

## 4. Code Quality Findings

### 4.1 HIGH: Widespread Exception Handling Suppression (Architectural Debt)

Multiple files in the hardware token module contain `@ArchUnitSuppressed("NoVanillaExceptions") //TODO XRDDEV-2962`, indicating known exception handling debt that hasn't been addressed. This appears in 13+ locations in the `hwtoken` addon alone.

**Recommendation:** Prioritize XRDDEV-2962 to introduce proper domain-specific exceptions.

---

### 4.2 MEDIUM: System.out/System.err Instead of Logger

**Files:**
- `src/tool/asic-verifier-cli/src/main/java/org/niis/xroad/asic/verifier/cli/AsicVerifierMain.java` - 11 occurrences
- `src/service/signer/signer-cli/src/main/java/org/niis/xroad/signer/cli/Utils.java` - 8 occurrences
- `src/buildSrc/src/main/java/org/niis/xroad/oasvalidatorplugin/Oas3Validator.java` - 3 occurrences

While CLI tools may intentionally use stdout, mixing `System.out.println()` with a logging framework (present in the same classes) creates inconsistent output handling.

**Recommendation:** Use the logger for all diagnostic output. Use stdout only for intended CLI output.

---

### 4.3 MEDIUM: Swallowed Exceptions

**File:** `src/tool/asic-verifier-cli/src/main/java/org/niis/xroad/asic/verifier/cli/AsicVerifierMain.java:137-139`

```java
} catch (IOException e) {
    System.out.println("Unable to extract files");
}
```

IOException is caught with only a generic message printed. The exception details and stack trace are discarded, making debugging impossible.

**Similar patterns found in:** `signer-cli/Utils.java` (lines 154-156, 164-166)

---

### 4.4 MEDIUM: Incorrect Log Levels

**File:** `src/service/signer/signer-api/src/main/java/ee/ria/xroad/common/util/FilePasswordStoreProvider.java:55,71`

```java
log.warn("Reading password from {}. File exists? {}", file, file.exists());
log.warn("Writing password to {}", file);
```

Normal operational activities logged at WARN level. These should be INFO or DEBUG.

---

### 4.5 LOW: Deprecated PostgreSQL Dialect Still in Use

**File:** `src/common/common-db/src/main/java/ee/ria/xroad/common/db/CustomPostgreSQLDialect.java`

Class is marked `@Deprecated` but likely still referenced. Should be migrated to the modern Hibernate PostgreSQL dialect.

---

## 5. Frontend Review

### 5.1 Architecture (Positive)

The frontend is well-structured with 3 Vue.js 3 applications sharing a common UI library:
- **Shared UI** (`src/shared-ui/`) - Reusable Vuetify components, i18n, utilities
- **Security Server UI** (`src/security-server/admin-service/ui/`) - Admin interface
- **Central Server UI** (`src/central-server/admin-service/ui/`) - Admin interface

Modern practices: `<script setup>` syntax, Pinia state management, TypeScript strict mode, Vite bundling.

### 5.2 POSITIVE: No XSS Vulnerabilities Detected

No instances of `v-html`, `innerHTML`, or `dangerouslySetInnerHTML` found. All text rendering uses Vue's safe `{{ }}` interpolation.

### 5.3 MEDIUM: Excessive `any` Type Usage

200+ instances of the `any` type across TypeScript files, with 65 ESLint/TypeScript ignore directives. While ESLint is configured to flag `no-explicit-any` as an error in production, many suppressions remain.

**Key file:** `src/security-server/admin-service/ui/src/util/api.ts:27` disables `no-explicit-any` for the entire file.

**Recommendation:** Progressively replace `any` types with proper interfaces, especially in the API utility layer.

### 5.4 MEDIUM: Minimal Frontend Test Coverage

Only **8 test files** exist for the entire frontend codebase (182+ components):
- 5 test files for Security Server UI
- 3 test files for Central Server UI

Critical areas like routing guards, API interceptors, and complex form components lack unit tests.

**Recommendation:** Add tests for security-critical paths (auth flow, permission checks, API error handling) at minimum.

### 5.5 Good Practices Observed

- Form validation with vee-validate and custom domain-specific rules (X-Road identifiers, endpoints)
- Proper session management (sessionStorage cleared on login/logout)
- Route guards with permission-based access control
- OpenAPI-generated types for API contracts
- Comprehensive error handling in API calls with deduplication

---

## 6. Build System & Dependencies

### 6.1 POSITIVE: Modern, Well-Configured Build

- Gradle 8.12 with Kotlin DSL and centralized version catalog (`libs.versions.toml`)
- 131+ subprojects with custom convention plugins for consistency
- Dependabot configured with smart grouping (Spring, gRPC, JS, GitHub Actions)
- Multi-architecture CI/CD (amd64 + arm64)
- pnpm 10.13.1 with pinned version for frontend

### 6.2 HIGH: Outdated Legacy Dependencies

| Dependency | Current | Issue |
|-----------|---------|-------|
| `commons-collections` 3.2.2 | 4.4+ available | 12+ years old, known vulnerabilities |
| `wsdl4j` 1.6.3 | unmaintained | 20+ years old |
| `javax.annotation-api` 1.3.2 | Jakarta available | Legacy namespace, Jakarta 3.0.0 already in use alongside |
| ANTLR `ST4` 4.3.4 | 4.13+ available | 10+ years old |

**Recommendation:** Upgrade `commons-collections` to 4.x (urgent). Evaluate `wsdl4j` replacement with Apache CXF (already in use). Complete javax-to-Jakarta migration.

### 6.3 MEDIUM: No Docker Image Signing or SBOM

Published Docker images lack:
- Image signatures or attestation
- Software Bill of Materials (SBOM) generation
- Fixed version tags (only floating `-latest` tags used)

---

## 7. Test Infrastructure

### 7.1 Overview

- **368 test files** across unit, integration, BDD, and system tests
- **101 Gherkin feature files** for behavioral testing
- **13 Python system test files** for operational monitoring
- JaCoCo coverage reporting (but no minimum thresholds enforced)

### 7.2 HIGH: Flaky Test Patterns (Thread.sleep)

**19 files** contain hard-coded `Thread.sleep()` calls that create race conditions:

- `MessageLogPerformanceTest.java:85` - `Thread.sleep(15000)` (15 seconds)
- `HealthChecksTest.java:119,142` - `Thread.sleep(okCache * 1000L + 500L)`
- `ServerProxyConnectionAborted.java:67` - `Thread.sleep(STARTUP_DELAY)`
- `OpMonitoringBufferTest.java:208` - Random sleep durations

**Recommendation:** Replace with `Awaitility` (already a dependency) for deterministic waiting.

### 7.3 MEDIUM: Performance Tests Not Integrated into CI

`MessageLogPerformanceTest.java` and `MessageLogIntegrationTest.java` have no `@Test` annotations and can only run as standalone programs. They are invisible to JUnit runners and CI pipelines.

### 7.4 MEDIUM: Limited Test Data Cleanup

Only 7 test classes use `@Transactional` for automatic rollback. Integration tests risk data leaking between test runs.

### 7.5 Well-Tested Areas

- Security Server Admin Service (37+ integration test methods)
- Message Logging (multiple test classes)
- Operational Monitoring (proper mocking)
- Health Checks (9 test methods, good assertions)

---

## 8. Docker & Deployment

### 8.1 POSITIVE: Good Base Practices

- Multi-stage builds in all production images
- Ubuntu 24.04 LTS base
- Non-root users created (postgres, xroad)
- APT cleanup to reduce image size
- Multi-architecture support (amd64, arm64)

### 8.2 HIGH: No Final USER Directive

All production Dockerfiles (Central Server, Security Server, Sidecar) lack a final `USER` directive, meaning containers default to running as root.

**Recommendation:** Add `USER xroad` as the final directive in all production Dockerfiles.

### 8.3 MEDIUM: Missing HEALTHCHECK Directives

No production Dockerfiles define `HEALTHCHECK` instructions, leaving container orchestrators unable to detect unhealthy instances.

### 8.4 MEDIUM: Hardcoded Test Credentials

Docker images contain hardcoded test user passwords (password hashes visible in Dockerfiles). These should be injected via environment variables or secrets.

### 8.5 LOW: Unpinned Base Images

Base images use floating tags (`ubuntu:24.04`, `ubuntu:noble`) rather than digest-pinned versions (`ubuntu:24.04@sha256:...`), which could lead to non-reproducible builds.

---

## 9. Recommendations Summary

### Critical Priority
| # | Finding | Location | Action |
|---|---------|----------|--------|
| 1 | XPath Injection | `XmlUtils.java:252` | Use XPath variable binding or input sanitization |
| 2 | Insecure Random for key IDs | `SignerUtil.java:131` | Replace `Random` with `SecureRandom` |
| 3 | Outdated commons-collections 3.2.2 | `libs.versions.toml` | Upgrade to 4.x |

### High Priority
| # | Finding | Location | Action |
|---|---------|----------|--------|
| 4 | Disabled TLS verification defaults | `HttpUrlConnectionConfigurer.java`, `WSDLValidator.java` | Ensure verification enabled by default |
| 5 | Password store in /tmp | `FilePasswordStoreProvider.java:93` | Change default path to restricted directory |
| 6 | Docker containers run as root | All production Dockerfiles | Add final `USER` directive |
| 7 | Flaky tests with Thread.sleep() | 19 test files | Replace with Awaitility |
| 8 | Exception handling arch debt | hwtoken addon (13+ suppressions) | Address XRDDEV-2962 |

### Medium Priority
| # | Finding | Location | Action |
|---|---------|----------|--------|
| 9 | CSRF cookie HttpOnly=false | `CookieAndSessionCsrfTokenRepository.java:59` | Document risk; mitigated by dual validation |
| 10 | Empty checkServerTrusted() | `AuthTrustManager.java:74-78` | Add defensive validation or logging |
| 11 | 200+ `any` types in frontend | Various TypeScript files | Progressive type refinement |
| 12 | 8 frontend tests for 182+ components | UI test directories | Add tests for auth flow and permissions |
| 13 | No Docker HEALTHCHECK | All production Dockerfiles | Add health check instructions |
| 14 | No image signing/SBOM | CI/CD publish workflow | Add attestation and SBOM generation |
| 15 | Missing JaCoCo coverage thresholds | `build.gradle.kts` | Set minimum coverage targets |

### Low Priority
| # | Finding | Location | Action |
|---|---------|----------|--------|
| 16 | System.out instead of logger | CLI tools (22+ occurrences) | Standardize on logging framework |
| 17 | Swallowed exceptions | `AsicVerifierMain.java`, `Utils.java` | Log exception details |
| 18 | Deprecated PostgreSQL dialect | `CustomPostgreSQLDialect.java` | Migrate to modern Hibernate dialect |
| 19 | Unpinned Docker base images | Dockerfiles | Pin with digest hashes |
| 20 | Legacy wsdl4j/javax dependencies | `libs.versions.toml` | Migrate to modern alternatives |

---

## Overall Assessment

X-Road is a mature, well-architected enterprise system with strong fundamentals: clean modular design, modern technology stack (Java 21, Spring Boot 3.5, Vue 3), comprehensive CI/CD, and thorough documentation. The codebase demonstrates good security awareness with proper XXE protection, parameterized queries, CSRF dual validation, and Spring Security integration.

The most urgent items to address are the **XPath injection vulnerability** and **insecure random number generation** in the signer module, as these directly impact the security guarantees the platform provides. The Docker security posture (running as root, no health checks) and legacy dependency upgrades are also important for production deployments.

The frontend code is clean and follows modern Vue.js best practices, though test coverage is notably thin. The backend test infrastructure is more robust but would benefit from eliminating flaky `Thread.sleep()` patterns and enforcing coverage thresholds.
