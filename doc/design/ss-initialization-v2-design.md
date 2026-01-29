# Security Server Initialization v2 - Design Document

## Overview

This document describes the design for improving SS initialization with granular step tracking and error handling, addressing the limitations of the current monolithic initialization approach.

## Current State Analysis

### Current Implementation (`InitializationService.java:159-198`)

The current initialization uses a single API call that performs multiple operations:

```
POST /api/v1/initialization
    ├── 1. createInitialServerConf()     [DB Transaction - rollbackable]
    ├── 2. initializeSoftwareToken()     [Signer RPC - NOT transactional]
    └── 3. prepareEncryption()           [NOT transactional]
        ├── generateGpgKey()             [Backup Manager RPC]
        ├── initializeMessageLogArchivalEncryption()  [Vault/Bao]
        └── initializeMessageLogDatabaseEncryption()  [Vault/Bao]
```

### Current Problems

As noted in the source code comment (line 185-186):
> "Both software token initialisation and GPG key generation are non transactional - when second one fails server moves to unusable state"

| Failure Point | Current Behavior | Recovery Path |
|---------------|------------------|---------------|
| ServerConf (DB) | Rolls back | Retry works |
| SoftToken init | ServerConf committed, token not initialized | **UNCLEAR - manual intervention needed** |
| GPG key generation | ServerConf + Token done, no GPG key | **UNCLEAR - manual intervention needed** |
| Bao encryption keys | ServerConf + Token + GPG done, no encryption | **UNCLEAR - manual intervention needed** |

---

## Solution

### Design Principles

1. **Separation of Concerns**: Each initialization step is independently callable
2. **Idempotency**: Each step can be safely retried without side effects
3. **Derived Status**: Step completion is determined by querying the underlying systems, not by persisted state
4. **Resumability**: Failed initialization can resume from the point of failure
5. **Backward Compatibility**: v1 API continues to work as before
6. **Exception-driven Error Handling**: Service methods throw typed exceptions, handled by `ApplicationExceptionHandler`

### Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Initialization Steps                              │
├─────────────────────────────────────────────────────────────────────┤
│  Prerequisite: ANCHOR  │ Must be imported before any step           │
│  Step 1: SERVERCONF    │ Server code + Owner (DB transaction)       │
│  Step 2: SOFTTOKEN     │ Software token initialization              │
│  Step 3: GPG_KEY       │ GPG key generation for backups             │
│  Step 4: MLOG_ENCRYPTION │ Message log encryption setup             │
└─────────────────────────────────────────────────────────────────────┘
```

ANCHOR is treated as a prerequisite checked before any step can execute, not as a step itself.

---

## API Design

New v2 API with individual endpoints per step:

```yaml
# Get detailed initialization status
GET /api/v2/initialization/status
Response: InitializationStatusV2Dto

# Initialize serverconf (code + owner)
POST /api/v2/initialization/serverconf
Request: ServerConfInitRequestDto { securityServerCode, ownerMemberClass, ownerMemberCode, ignoreWarnings }
Response: InitStepResultDto

# Initialize software token
POST /api/v2/initialization/softtoken
Request: SoftTokenInitRequestDto { softwareTokenPin }
Response: InitStepResultDto

# Generate GPG key
POST /api/v2/initialization/gpg-key
Request: (none - uses serverconf identity)
Response: InitStepResultDto

# Initialize message log encryption
POST /api/v2/initialization/messagelog-encryption
Request: (none - uses serverconf identity)
Response: InitStepResultDto

# Convenience: Run all pending steps
POST /api/v2/initialization/run-all
Request: FullInitRequestV2Dto { securityServerCode, ownerMemberClass, ownerMemberCode, softwareTokenPin, ignoreWarnings }
Response: InitializationStatusV2Dto
```

---

## Data Model

### InitializationStep Enum

```java
public enum InitializationStep {
    SERVERCONF,       // Server code + owner configuration
    SOFTTOKEN,        // Software token initialization
    GPG_KEY,          // GPG key generation for backups
    MLOG_ENCRYPTION   // Message log encryption setup
}
```

Each step defines its order and prerequisites:
- `SERVERCONF` — order 1, no prerequisites
- `SOFTTOKEN` — order 2, requires SERVERCONF
- `GPG_KEY` — order 3, requires SERVERCONF
- `MLOG_ENCRYPTION` — order 4, requires SERVERCONF

### InitializationStepStatus Enum

```java
public enum InitializationStepStatus {
    NOT_STARTED,      // Step has not been attempted
    IN_PROGRESS,      // Step is currently running (for long operations)
    COMPLETED,        // Step completed successfully
    FAILED,           // Step failed - retry possible
    SKIPPED,          // Step was skipped (e.g., optional)
    UNKNOWN           // Status could not be determined (e.g., RPC error)
}
```

### InitializationStepInfo DTO

```java
@Data @Builder
public class InitializationStepInfo {
    private InitializationStep step;
    private InitializationStepStatus status;
    private Instant startedAt;
    private Instant completedAt;
    private String errorMessage;      // If FAILED
    private String errorCode;         // Machine-readable error code
    private boolean retryable;        // Can this step be retried?
}
```

### InitializationStatusV2

```java
@Data @Builder
public class InitializationStatusV2 {
    private OverallStatus overallStatus;
    private boolean anchorImported;
    private List<InitializationStepInfo> steps;
    private List<InitializationStep> pendingSteps;
    private List<InitializationStep> failedSteps;
    private List<InitializationStep> completedSteps;
    private boolean fullyInitialized;
    private String securityServerId;               // If serverconf done
    private Boolean tokenPinPolicyEnforced;         // From signer

    public enum OverallStatus {
        NOT_STARTED,
        IN_PROGRESS,
        PARTIALLY_COMPLETED,  // Some steps done, some pending
        FAILED,               // Critical failure, cannot proceed
        COMPLETED             // All required steps done
    }
}
```

---

## Persistence Strategy

There is no dedicated persistence layer. Each step's completion status is **derived by querying the underlying system** that the step configures:

| Step | How completion is determined |
|------|----------------------------|
| SERVERCONF | `serverConfService.isServerCodeInitialized() && isServerOwnerInitialized()` (database) |
| SOFTTOKEN | `tokenService.isSoftwareTokenInitialized()` (signer RPC) |
| GPG_KEY | `backupManagerRpcClient.hasGpgKey()` (backup manager RPC) |
| MLOG_ENCRYPTION | `vaultClient.getMLogArchivalSigningSecretKey()` + `getMLogDBEncryptionSecretKeys()` (vault) |

This approach has several advantages over database or file persistence:
- Status always reflects actual system state, not stale recorded state
- Survives service restarts without any migration or state recovery
- Idempotency is natural — re-running a completed step detects completion and returns success
- No risk of state divergence between the tracking table and actual system

### HasGpgKey RPC

The backup manager exposes a `HasGpgKey` RPC to check GPG key existence:

```protobuf
rpc HasGpgKey(Empty) returns (HasGpgKeyResp) {}

message HasGpgKeyResp {
  bool has_gpg_key = 1;
}
```

The implementation runs `gpg --list-keys --with-colons` against the GPG home directory and checks for `pub:` or `sec:` key entries. A fallback to `GetBackupEncryptionStatus` is used if the RPC is unavailable.

---

## Service Layer Design

### InitializationStepService

```java
@Service
@Transactional
public class InitializationStepService {

    // Get status of all steps (derived from underlying systems)
    public InitializationStatusV2 getInitializationStatusV2();

    // Get status of specific step
    public InitializationStepInfo getStepStatus(InitializationStep step);

    // Execute individual steps — throw on failure
    public InitializationStepInfo executeServerConfStep(String code, String memberClass, String memberCode, boolean ignoreWarnings);
    public InitializationStepInfo executeSoftTokenStep(String pin);
    public InitializationStepInfo executeGpgKeyStep();
    public InitializationStepInfo executeMessageLogEncryptionStep();

    // Execute all pending steps — stops on first exception
    public InitializationStatusV2 executeAllPendingSteps(String code, String memberClass, String memberCode, String pin, boolean ignoreWarnings);
}
```

### Step Execution Pattern

Each step follows the same pattern:

```java
public InitializationStepInfo executeXxxStep(...) {
    // 1. Check prerequisites — throws ConflictException if not met
    verifyPrerequisite(step);

    // 2. Check if already completed — return success (idempotent)
    if (isStepCompleted(step)) {
        return InitializationStepInfo.completed(step, Instant.now());
    }

    // 3. Execute step-specific logic — exceptions propagate to ApplicationExceptionHandler
    doStepWork(...);

    // 4. Return success
    return InitializationStepInfo.completed(step, Instant.now());
}
```

### Error Handling

Service methods throw typed exceptions instead of returning error DTOs:

| Exception | HTTP Status | When |
|-----------|-------------|------|
| `AnchorNotFoundException` (extends `ConflictException`) | 409 | Anchor not imported |
| `PrerequisiteNotMetException` (extends `ConflictException`) | 409 | SERVERCONF not completed |
| `WeakPinException` (extends `BadRequestException`) | 400 | PIN validation fails |
| `InvalidCharactersException` (extends `BadRequestException`) | 400 | PIN has invalid characters |
| Other exceptions | 500 | Infrastructure failures |

All exceptions are handled by the existing `ApplicationExceptionHandler`, which returns consistent `ErrorInfo` responses.

### Audit Events

Each endpoint uses a step-specific audit event:

| Endpoint | Audit Event |
|----------|-------------|
| `POST /serverconf` | `INIT_SERVER_CONF` |
| `POST /softtoken` | `INIT_SOFTTOKEN` |
| `POST /gpg-key` | `INIT_GPG_KEY` |
| `POST /messagelog-encryption` | `INIT_MLOG_ENCRYPTION` |
| `POST /run-all` | `INIT_ALL_STEPS` |

---

## Controller Design

The controller implements the generated `InitializationV2Api` interface from the OpenAPI specification:

```java
@Controller
@RequestMapping("/api")
@PreAuthorize("denyAll")
public class InitializationApiControllerV2 implements InitializationV2Api {
    // Method-level @PreAuthorize("hasAuthority('INIT_CONFIG')") on write operations
    // Method-level @PreAuthorize("isAuthenticated()") on status endpoint
    // synchronized on write operations to prevent concurrent step execution
}
```

The controller converts between domain DTOs (`InitializationStepInfo`, `InitializationStatusV2`) and generated OpenAPI DTOs (`InitStepResultDto`, `InitializationStatusV2Dto`).

---

## Error Handling and Recovery

### Recovery Strategies by Step

| Step | Failure Mode | Recovery Strategy |
|------|--------------|-------------------|
| SERVERCONF | DB error | Automatic rollback, retry safe |
| SERVERCONF | Anchor not imported | Import anchor, then retry |
| SOFTTOKEN | Signer unavailable | Retry when signer is available |
| SOFTTOKEN | Invalid/weak PIN | User corrects PIN and retries |
| GPG_KEY | Backup manager unavailable | Retry when service available |
| GPG_KEY | Key already exists | Detected by `hasGpgKey()`, returns completed |
| MLOG_ENCRYPTION | Vault/Bao unavailable | Retry when service available |
| MLOG_ENCRYPTION | Keys already exist | Detected by vault query, returns completed |

### Idempotency

Each step checks completion before executing. Re-running a completed step returns `InitializationStepInfo.completed()` without side effects. This is guaranteed by the derived status approach — the check queries the actual system state.

---

## Backward Compatibility

- The existing `POST /api/v1/initialization` endpoint continues to work unchanged
- v1 uses `InitializationService`, v2 uses `InitializationStepService` — separate service classes
- Both can coexist without interference

### Detecting Existing State

For servers initialized via v1 before v2 was available, the derived status approach handles this naturally — `getInitializationStatusV2()` queries the actual systems and correctly reports all steps as COMPLETED if they were done via v1.

---

## OpenAPI Specification

The v2 endpoints are defined in `openapi-definition.yaml` under the `initialization-v2` tag. The OpenAPI generator produces:

- **Interface**: `InitializationV2Api` — implemented by the controller
- **Request DTOs**: `ServerConfInitRequestDto`, `SoftTokenInitRequestDto`, `FullInitRequestV2Dto`
- **Response DTOs**: `InitStepResultDto`, `InitializationStatusV2Dto`, `InitializationStepInfoDto`
- **Enums**: `InitializationStepDto`, `InitializationStepStatusDto`, `InitializationOverallStatusDto`

---

## Security Considerations

1. PIN handling: Never persist PINs, only pass through to signer
2. Audit logging: Each step endpoint has a dedicated audit event
3. Authorization: `INIT_CONFIG` authority required for all write endpoints, `isAuthenticated()` for status
4. Synchronized methods: Prevents concurrent execution of initialization steps

---

## File References

| File | Purpose |
|------|---------|
| `InitializationStepService.java` | Step execution and status derivation |
| `InitializationApiControllerV2.java` | v2 API controller implementing `InitializationV2Api` |
| `InitializationStep.java` | Step enum with ordering and prerequisites |
| `InitializationStepInfo.java` | Per-step status DTO |
| `InitializationStatusV2.java` | Complete status DTO |
| `RestApiAuditEvent.java` | Step-specific audit events |
| `backup_service.proto` | HasGpgKey RPC definition |
| `FileSystemBackupHandler.java` | `hasGpgKey()` implementation |
| `BackupManagerRpcClient.java` | HasGpgKey RPC client |
| `openapi-definition.yaml` | v2 API specification |
| `InitializationApiControllerV2Test.java` | Controller unit tests |
