# PRD — Split `edc17-dsp-integration` into reviewable PRs

Status: in flight, 4 of 7 planned slices merged + new infra-slice queue identified.

## Problem

`edc17-dsp-integration` originally carried 56 commits, ~216 files (+14k/-4k LOC) of
mixed work: EDC 0.17 DSP integration, catalog publication fixes, module refactor,
dev-environment QoL, system-test framework changes, ansible/k8s wiring,
native-package additions. A single PR of this size is not reviewable in any
reasonable time. Commits are interleaved across topics (same file touched by
refactor + catalog fix + baseline), so cherry-pick-by-commit produces cross-feature
contamination.

## Goal

Land all of `edc17-dsp-integration`'s intent on `develop` as a sequence of small
PRs, each **standalone-mergeable to develop with no regression**.

Original definition of done: `develop`'s tip after all PRs land == `edc17-dsp-integration`'s
tip (modulo merge-commit noise).

**Revised definition (after findings below):** develop's tip incorporates the
*intent* of every edc17 hunk that hasn't been superseded by a review cycle. Some
edc17 content (e.g. `DspFailureClassifier`, `ControlPlaneRegistrar`,
`DataPlaneReadinessState`, `DataPlaneServerLifecycle`, the
`dataplane-selector` module before its `dataplane-registrar` rename) was dropped
or restructured during PR review and will never reach develop in its edc17 shape.
Develop is the source of truth for those files; rest should not restore them.

## Approach

**Dormant-scaffolding model.** Land all DSP code as built-but-unloaded modules
first. The *final* PR is the only one that wires DSP into the proxy hot-path. No
feature flags, no conditional beans — non-breaking comes from the absence of a
call site, not from runtime gates.

Key seam: `src/service/proxy/proxy-application/build.gradle.kts` line
`implementation(project(":service:proxy:proxy-dsp-core"))`. Held for the final
PR. Result: every intermediate PR builds and ships `proxy-dsp-core` as an
artifact, but `proxy-application` never loads it → `ConsumerSideDspProcessor`,
`DataPlaneServer`, `ControlPlaneRegistrar` never instantiate → zero error spam
from unconfigured DSP wiring.

DSP federation services (`ds-control-plane`, `ds-identity-hub`,
`ds-issuer-service`) run as side-car containers in the dev stack from the moment
their modules land. They are not invoked by proxy traffic but their own boot path
must be silent (no crash-loop, no repeated WARN/ERROR).

**Path-based extraction, not commit cherry-pick.** For each slice:

1. Branch from `develop`.
2. Snapshot `edc17-dsp-integration` tip for the files belonging to the slice
   (`git checkout edc17-dsp-integration -- <paths>`).
3. For paths shared across slices (mixed files), use diff-application from a
   pure-slice commit (`git show <commit> -- <file> | git apply --3way --index`)
   so each slice carries only its own hunk, not the accumulated state.
4. Stage deletions for paths removed by edc17 but still present on develop.
5. Compile-check (`./gradlew compileJava compileTestJava compileIntTestJava`).
6. For dev-environment slices, also run `start-env.sh --recreate --skip-host-networking`
   to validate end-to-end against the real LXD stack.

**Safety net:** tag `dsp-source-snapshot` → `edc17-dsp-integration` kept until
all slices land. `dsp-split/rest` rebuilt after every carve as the remainder
catching anything we missed.

## Rules each slice must satisfy

- **Mergeable to `develop` standalone.** No references to modules/classes that
  don't yet exist on `develop`. No dependence on a sibling slice being merged
  first (unless declared as a stack).
- **No behavior regression.** Tests pass after the slice lands. Legacy proxy
  hot path stays untouched until the final flip PR.
- **No new error log lines at boot.** DSP side-car containers run idle but
  silent. Modules added but not wired produce no startup WARN/ERROR. Verify
  with `docker logs <service> | grep -iE 'error|warn'` before declaring green.
- **Each new module lands in its final shape.** No "land then rename" — fold
  rename commits into the slice that creates the module. Same for module
  splits.
- **Catalog/discovery fixes land with the catalog module**, not as follow-up
  slices. Built-in-services synthesis, management-subsystem synthesis,
  owner-only emission, mgmt-context routing — all in the `ds-xroad-catalog`
  slice from day 1.
- **No DSP planning refs in comments.** Strip any `Phase N` / `PRD <slug>` /
  `# see issue …` comments while carving.

## Status

### Merged to `develop`

| # | Slice | PR | Notes |
|---|---|---|---|
| 1 | `dsp-split/lxd-improvements` | #3555 | jaeger reliability + lxd snapshot/restore + pkg caching + password sync; `--skip-host-networking` flag |
| 2 | `dsp-split/drop-ds-data-plane` | #3556 | drop module + housekeeping + `@Skip` on the two DS feature files |
| 3 | `dsp-split/edc-bump-and-skeleton` | #3557 | EDC 0.16.0 → 0.17.0 bump + migrations + `ds-control-plane-tasks-store-poll-executor` module + `ds-xroad-edr-api` deleted + transitional `ds-control-plane-application` build.gradle.kts; folds `test-infra-improvements` |
| 4 | `dsp-split/dsp-catalog-and-dataplane-selector` | #3559 | `ds-xroad-catalog` (final shape, all publication fixes folded in) + `ds-xroad-dataplane-selector` (later renamed in-PR to `dataplane-registrar`) + new `ServerConfProvider.getServiceAccessRights` interface method |
| 5 | `dsp-split/dsp-asset-access` | #3565 | `ds-xroad-asset-access-api` + `-protocol` modules. `DspFailureClassifier` was added then dropped in review (replaced with DSP_TO_LEGACY map in `AssetAccessGrpcService`). Added `SpanAttributes` / `XrdSpanAttrs` to common-core + `opentelemetry-sdk-testing 1.61.0` lib |
| 6 | `dsp-split/dsp-identity-hub-and-issuer-service` | #3568 | Aligns federation participants with EDC 0.17. Trims `CustomIdentityHubParticipantContextService` (-84 lines) — drops the bespoke ParticipantContext path now covered by the upstream provisioner |
| 7 | `dsp-split/dsp-proxy-dsp-core-dormant` | #3569 | `proxy-dsp-core` module shipped as built-but-unloaded artifact. Review-cycle dropped `ControlPlaneRegistrar`, `DataPlaneReadinessState`, `DataPlaneServerLifecycle`, `DspLegacyErrorMapper`. Added jersey + jetty-ee11-servlet entries to `libs.versions.toml`. **`proxy-application/build.gradle.kts` NOT touched.** |

### Local-only, ready / partially ready

| Slice | Δ over develop | Status |
|---|---|---|
| `dsp-split/dsp-systemtest-scaffolding` | +1 commit / ~19 files / +1427/-355 | Local-only. DSP-mode compose profile, `DspBootstrap` + `DspAuthTokens`, EDR → AssetAccess vocab rename. Compile checked. Not pushed yet. |
| `dsp-split/dsp-enable-traffic` | +2 commits / ~17 files / +445/-66 over systemtest | The final flip. Wires `proxy-application` → `proxy-dsp-core` dep edge, injects `ConsumerSideDspProcessor` into `ClientSoap`/`ClientRestMessageProcessor`, adds `recordServiceSecurityServerAddress`, flips system-test 0920 error-code assertions. **CRITICAL: not safe to merge alone — see findings below.** |

### Branches no longer needed

- `dsp-split/test-infra-improvements` — folded into #3557
- `dsp-split/test-lxd-plus-drop` — stale combine attempt
- `dsp-split/dsp-asset-access`, `dsp-split/dsp-identity-hub-and-issuer-service`,
  `dsp-split/dsp-proxy-dsp-core-dormant`, `dsp-split/dsp-catalog-and-dataplane-selector`
  — content on develop; safe to delete locally if you want to clean up

## Findings (this session, 2026-05-29)

### Finding 1: `dsp-split/dsp-enable-traffic` is misleadingly named

The "enable traffic" slice does NOT add DSP as an *option* with fallback to
legacy transport. It makes DSP **mandatory** on every consumer-side request:

- In `ClientSoapMessageProcessor.java:221` and the REST equivalent,
  `consumerSideDspProcessor.execute(...)` is called unconditionally with **no
  try/catch**. The return value is discarded.
- `ConsumerSideDspProcessor`'s Javadoc explicitly states *"Lookup miss is a
  hard error (fail fast; no silent fallback). Exhaustion of all candidates
  ends in NETWORK_ERROR with the last thrown exception chained."*

**Operational consequence:** merging enable-traffic to develop in its current
form makes every SOAP/REST consumer-side request fail unless a functioning
federation (control-plane + identity-hub + issuer-service) is reachable from
the proxy.

**Deployment coverage today (post-PR #3569):**

| Mode | ds-control-plane | ds-identity-hub | ds-issuer-service |
|---|---|---|---|
| Native (DEB/RPM) | ✅ on develop | ✅ on develop | ❌ packaging NOT on develop |
| Dev docker compose | ✅ | ✅ | ✅ |
| K8s Helm chart | ❌ side-cars not in chart | ❌ | ❌ |
| Ansible / LXD | ❌ DSP roles never landed | ❌ | ❌ |

So enable-traffic merged today = green CI (system-test runs on the DSP-mode
compose profile), green local docker dev, but **regression in native-installed
prod/staging, k8s, LXD/ansible**. The PR is therefore not the *final flip* —
it's blocked on the infra slices below.

### Finding 2: original PRD inventory skipped infra

The PRD's slice inventory covered code modules + system-tests. **It never
scoped deployment / packaging / ansible / k8s / sidecar / CI work as slices.**
`dsp-split/rest` was intended as the remainder catch-all but had decayed.

### Finding 3: rest-rebuild methodology iterated through three versions

`rest` is supposed to answer "what's left in edc17 that hasn't reached
develop yet". Three attempts were needed:

**Attempt A — naive `git checkout edc17 -- .` (285 files, +7826/-4171).** Took
the edc17 tree wholesale and reverted develop's review-cycle improvements
(rename of `selector → registrar`, removal of `DspLegacyErrorMapper`, asset-access
polish, etc.). Wrong: develop is truth.

**Attempt B — adds-only minus develop-rejected (27 files, +1330).** Excluded
all 201 "modify" files. Wrong in the other direction: not every "modify" is
develop-truth. Some are files develop **never touched since the merge-base**;
edc17 changed them and the changes are unintegrated.

**Attempt C (current) — 3-way classification per file (78 files, +2316/-425).**
For each `M` file: if develop's blob == merge-base blob, develop hasn't touched
the file and edc17's hunks are unintegrated → include in rest. Otherwise →
develop is truth, exclude.

Classification result of the 201 modifies:

| Class | Count | Rule |
|---|---|---|
| develop untouched, edc17 changed → in rest | 51 | edc17's hunks are unintegrated |
| develop changed, edc17 untouched → develop truth | 69 | develop reworked since edc17 was branched off, edc17 unchanged on that file |
| BOTH changed → ambiguous (see Finding 4) | 81 | currently excluded from rest; some may have unintegrated edc17 hunks |

Plus the 27 ADDS (files only in edc17) = 78 total in rest today.

### Finding 4: ~10–20 load-bearing infra hunks are still potentially missing

Of the 81 BOTH-changed files, three categories exist:

| Category | Files | Status |
|---|---|---|
| Files in **merged slice modules** (asset-access, proxy-dsp-core, catalog tests, common-core SpanAttributes, serverconf-impl catalog hooks, TaskPollExecutorTest) | ~50 | Develop is truth. Diff represents review-rejected content. |
| **Auto-regenerated** (`verification-metadata.xml`) | 1 | Noise. |
| Files in **areas no slice landed** (ansible playbooks, LXD start-env scripts, deb `control` file, CI workflows) | ~10–20 | **Likely unintegrated.** Often the file is the include-point/wiring-up for roles that DO live in rest. Without these hunks rest's own additions don't take effect. |
| Files in **unmerged slice branches** (e2e-test, system-test, compose.systemtest.ds.yaml, EnvSetup.java, step defs) | ~10 | Tracked by the slice branches but the slice may carry less than edc17 — needs per-file diff. |

Top suspicious BOTH-changed files (by remaining diff size) that warrant a per-file 3-way check:

- `development/ansible/xroad_cache_images.yml` (~220 lines) — likely DSP image cache list
- `development/ansible/roles/init-lxd/tasks/main.yml` (~94 lines) — LXD bringup
- `development/ansible/roles/xroad-ss/tasks/main.yml` (~84 lines) — include-points for the new DSP tasks already in rest
- `development/ansible/xroad_dev.yml` (~65 lines) — top-level dev playbook
- `development/ansible/roles/xroad-cs/tasks/main.yml` (~58 lines) — same, CS side
- `deployment/native-packages/src/xroad/ubuntu/generic/control` — DEB control file, `xroad-ds-issuer-service` package entry
- `development/native-lxd-stack/start-env.sh`, `restore-containers.sh`, `README.md`, `config/ansible_hosts.txt`
- `.github/workflows/cleanup-snapshots.yaml`
- `src/security-server/system-test/src/intTest/resources/compose.systemtest.ds.yaml` (~80 lines) — may have hunks beyond what `dsp-systemtest-scaffolding` slice carries

## Current `dsp-split/rest` tip

`dsp-split/rest` = `10f4c5e029` — 78 files, +2316 / −425 vs `origin/develop`.

Breakdown by area:

| Area | Files | What |
|---|---|---|
| `development/ansible/` | 17 | DSP roles for cs/ss + `xroad-mock-jwks-server` role, `xroad-hurl` updates, `xroad-ca/sign_req.sh`, `xroad-ss/hwtokens.yml`, init-dev-config local.yaml |
| `development/k8s/` | 13 | dev k8s overlay updates — site.yml, security_server role, inventory group_vars, `dev.sh` script, netdata role |
| `deployment/native-packages/` | 12 | `xroad-ds-issuer-service` package (7 new) + `_openbao.sh` + `secret-store-init.sh` + `xroad-proxy.service` + `ds-control-plane.conf` + `ds-identity-hub.conf` |
| `deployment/security-server/` (Helm) | 11 | Chart.yaml, `_helpers.tpl`, services/all.yaml, values.yaml + new DSP templates + jwks files + chart README + openbao-init/init.sh |
| `src/service/proxy/` | 11 | enable-traffic content: `proxy-application/build.gradle.kts`, application.yaml, ClientSoap/RestMessageProcessor, ClientRequestPreparationService, AuthKeyOcspReadinessCheck, DefaultServiceAddressResolver, proxy-core test stubs + new tests |
| `src/security-server/` (system-test + e2e) | 7 | systemtest content: IdentityHubStepDefs, IssuerServiceStepDefs, DsStepDefs, Port.java, compose.systemtest.yaml, 0920 feature, compose.aux.yaml |
| `development/native-lxd-stack/` | 3 | test-proxy-dsp.http + test-proxy-rest.http + http-client.env.json |
| `development/docker/` | 2 | compose.yaml, openbao/Dockerfile |
| `development/hurl/` | 1 | vars.env |
| `src/config/` | 1 | checkstyle suppressions |

**Caveat:** rest is a **triage artifact, not a mergeable branch.** It contains
overlap with the unmerged `dsp-systemtest-scaffolding` and `dsp-enable-traffic`
branches (those branches carry the same content but as logically scoped commits).
And per Finding 4, it likely under-covers by ~10–20 load-bearing infra hunks.

## Remaining work — proposed slice queue

Order matters: enable-traffic MUST land **after** the infra slices below.

1. **`dsp-native-packaging`** (~7 files in rest + ~2 from BOTH-changed)
   - `xroad-ds-issuer-service` DEB/RPM (.install/.postinst/.service + bin + scripts)
   - `ubuntu/jammy/debian/compat`
   - `deployment/native-packages/src/xroad/ubuntu/generic/control` — DEB control file with `xroad-ds-issuer-service` line (from BOTH-changed)
   - `_openbao.sh`, `secret-store-init.sh`, `xroad-proxy.service`, `ds-control-plane.conf`, `ds-identity-hub.conf`

2. **`dsp-k8s-helm`** (~11 files)
   - Helm chart additions: DSP templates (`_helpers.tpl`, control-plane seed job, mock-jwks-server configmap), jwks files, chart README, openbao-init/init.sh
   - `Chart.yaml`, `values.yaml`, `services/all.yaml`, `templates/_helpers.tpl` modifications

3. **`dsp-ansible-federation`** (~17 files in rest + ~5 from BOTH-changed)
   - New roles: `xroad-cs` DSP handlers + tasks, `xroad-mock-jwks-server`, `xroad-ss` DSP tasks
   - Modifications (need per-file 3-way check): `xroad_cache_images.yml`, `xroad_dev.yml`, `init-lxd/tasks/main.yml`, `xroad-cs/tasks/main.yml`, `xroad-ss/tasks/main.yml`
   - `xroad-hurl` updates, init-dev-configuration local.yaml
   - `xroad-ss/hwtokens.yml`, `xroad-ca/sign_req.sh`

4. **`dsp-dev-lxd-and-docker`** (~5 files)
   - LXD scenarios (`test-proxy-dsp.http`, `test-proxy-rest.http`, `http-client.env.json`)
   - `start-env.sh`, `restore-containers.sh`, `README.md`, `config/ansible_hosts.txt` (need per-file check)
   - `development/docker/security-server/compose.yaml`, `openbao/Dockerfile`
   - `development/hurl/scenarios/vars.env`

5. **`dsp-k8s-dev-roles`** (~13 files)
   - `development/k8s/` dev overlay: `site.yml`, `security_server` role (defaults, tasks, templates, argument_specs), inventory `group_vars/{dev,eks,test}`, `dev.sh`, `ansible.cfg`
   - `netdata` role (3 files — separate concern, possibly its own slice)

6. **`dsp-ci-workflows`** (~5 files)
   - `.github/workflows/_build-and-package.yml`, `build.yaml`, `build-package-builder-images.yaml`, `cleanup-snapshots.yaml`, `publish_testca.yaml`

7. **`dsp-sidecar-and-docs`** (~9 files)
   - `sidecar/*` (6 files including `docker-build.sh`)
   - `doc/Manuals/` (2 files), `doc/Sidecar/` (1 file)

8. **`dsp-systemtest-scaffolding`** (existing local branch) — but verify per-file
   that the slice carries everything from rest's `src/security-server/` deltas.

9. **`dsp-enable-traffic`** (existing local branch) — lands last, once 1-7
   are on develop and the federation is reachable in every deployment mode.

## Methodology lessons (for any future similar split)

1. **Don't trust "diff name-status" alone.** Files marked `M` between two
   long-lived branches can mean any of: develop-truth (rework superseded edc17),
   edc17-unintegrated (develop never touched), or both-changed (genuine
   3-way merge needed). Use blob-hash-vs-merge-base per file to distinguish.

2. **Auto-generated files are noise.** `verification-metadata.xml` regenerates
   from `libs.versions.toml`; treating its diff as "missing work" inflates the
   gap by hundreds of lines.

3. **Track review-cycle removals separately.** Files like `DspFailureClassifier`,
   `ControlPlaneRegistrar`, `DataPlaneReadinessState`,
   `DataPlaneServerLifecycle`, `DspLegacyErrorMapper`, plus the
   `dataplane-selector` module pre-rename are all examples where the original
   branch had content that develop intentionally rejected. Rest must NOT
   restore them.

4. **The "remaining" branch isn't merge-ready.** It's a triage artifact for
   enumerating what's left; carve from it into proper develop-based slices.
   Never PR `rest` itself.

5. **Final-flip slices are the most dangerous.** A slice whose only commit is
   "turn on the new code path" is also the slice that depends on the *entire
   surrounding infrastructure* being present in every deployment target. Plan
   infra slices *before* enabling the path, not after.

## Non-goals

- Preserving original commit authorship/history. Each slice gets a fresh,
  semantically-scoped commit message; the original 56 commits are not preserved.
- Backporting any of this to release branches.
- Refactoring `edc17-dsp-integration` itself — it stays as the read-only
  source of truth until the split is complete.
- Feature flags or conditional beans to gate DSP. Non-breaking is achieved
  by the absence of the `proxy-application` → `proxy-dsp-core` dependency
  edge through all of Stack 2; the final PR adds that edge AND the deployment
  infra in one coordinated landing window.
