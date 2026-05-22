# DSP User-Service Catalog Publication Gap

Status: needs-triage

## Problem

After DSP/EDC v17 refactor, `security-server/system-test` scenario
`2400-ss-opmonitoring.feature > Retrieving Operational Data of Security Server`
fails on assertion `getProducerRecord(monitoringData, "REST", "s3c2")` →
`NoSuchElementException`. No Producer-side op-monitor record exists for the
REST calls that scenario `2350-ss-rest-calls.feature` issued against
`DEV/COM/1234/TestService/s3c2`, `testOas31`, `s4c2`.

Root cause is upstream of op-monitor: the ds-control-plane EDC catalog
returns `datasets=0` for all three user-subsystem services. Without a
catalog entry the consumer-side DSP negotiation throws `unknown_member: No
dataset found for asset ID: DEV:COM:1234:TestService:<code>`, the
ClientRestMessageProcessor never sends the request to the server side, no
Producer record is ever created.

## Why this happens

`service/ds-control-plane/ds-xroad-catalog/src/main/java/org/niis/xroad/edc/extension/catalog/ContractDefinitionServerConfStore.java:218-220`:

```java
private void collectContractDefinitionsForService(ServiceId serviceId,
                                                  List<ContractDefinition> definitions) {
    var accessRights = serverConfProvider.getServiceAccessRights(serviceId);
    if (accessRights.isEmpty()) {
        return;
    }
    ...
}
```

A service with no `ServiceAccessRight` rows produces zero
`ContractDefinition` entries. EDC then cannot build a `Dataset` from the
Asset (Asset exists but is uncontracted), so the catalog response contains
zero datasets for that service.

`AssetIndexServerConfStore.queryAssets`:102 also filters out disabled
services (`getDisabledNotice != null`), so a disabled service is dropped
even before reaching ContractDefinition.

The 0540 and 0550 UI feature files exercise the cleanup-removal flow as
part of their own test coverage:

- 0540 line 70-83 — "Client service has all access rights removed" removes
  every ACL row from `s4c2`.
- 0540 line 116-121 — "Newly added services are enabled and one of them
  disabled" disables `s4c2`.
- 0540 line 137-149 — adds `testOas31` and never gives it any ACL.
- 0550 line 81-94 — "Client service has all access rights removed" removes
  every ACL row from `s3c1` (and `s3c2` never had ACL added in 0550 at
  all).

By the time scenario 2350 runs, all three services hit by the Feign calls
are in the state the 0540/0550 cleanup tests left them: `s3c2` and
`testOas31` have empty ACL, `s4c2` has empty ACL and is disabled.

## Why it worked pre-DSP

Pre-DSP, the proxy did not depend on a catalog. The ACL check ran on the
server side at request time, and the request reached the server regardless
of catalog state. The Producer-side op-monitor record was created as part
of message processing on the server.

Post-DSP, catalog publication is a prerequisite for the consumer's
request to ever leave the local control plane. ACL state at catalog
publication time now determines reachability, not just authorization.

## Decision required

Four options, ranked roughly by scope:

### A. Restore ACL + enable in a prep feature before 2350

Add `behavior/02-addons/2349-restore-rest-acl.feature` that re-enables
`s4c2` and re-adds member/subsystem ACL to `s3c2`, `testOas31`, `s4c2`.
The UI step definitions for this exist
(`ClientServicesStepDefs.java:179-220, :332-339`). Estimated cost: ~18-25
lines of Gherkin, ~30 s additional UI time per service due to Selenide
dialog flow.

Trade-off: minimal scope; test intent of 0540/0550 (cleanup flow) stays
intact; the runtime call in 2350 starts to work again. However the
prep-feature pattern needs to be repeated for every future test that
calls a user service after a strip-ACL scenario.

### B. Use different services for the 2350 calls

Add new ACL'd services dedicated to runtime-call testing (e.g.
`s3c-runtime`, `oas-runtime`) and route 2350 + 2400 against those. Keep
0540/0550 cleanup tests on the existing `s3c2` etc.

Trade-off: cleanly separates "ACL-strip coverage" from "runtime-call
coverage". Larger change — new FeignXRoadRestRequestsApi endpoints,
new mock-server stubs, two new ACL'd services in UI features. Probably
the cleanest long-term separation.

### C. Relax the catalog filter for owner-internal calls

Modify `ContractDefinitionServerConfStore.collectContractDefinitionsForService`
so that a service owned by the local member is always exposed to the
local member, even with empty external ACL. Mirrors the X-Road convention
that a member can always invoke services on its own security server.

Trade-off: changes runtime semantics, not just tests. Requires alignment
with the broader DSP/EDC contract design — does "publication" require
external ACL, or is owner-call always implicit? Needs an architectural
decision before code change.

### D. Skip 2400 Producer assertions for affected services

Mark the affected assertions in `OpMonitorStepDefs.validOperationalDataIsReturned`
as `@SkippedPostDsp` or guard them behind a feature flag.

Trade-off: hides the gap rather than fixing it; op-monitor producer-side
coverage shrinks. Defer until a real decision on A/B/C lands.

## Recommendation

Option **B** for the next milestone (cleanest separation of concerns),
with **A** as a tactical bandaid if B can't land before the next
stabilization cut. Option C requires a separate architectural review of
DSP catalog semantics for owner-internal calls.

## Verification

After applying any fix, the ds-control-plane log at the time of the
`TestService:s3c2` request should show `datasets >= 1` instead of
`datasets=0`, and `security-server/system-test/build/container-logs/op-monitor--test-automation.log`
should have a `Received store request` entry for a record with
`securityServerType=Producer`, `serviceCode=s3c2`. The 2400 assertion
`verifyRestRecord(getProducerRecord(monitoringData, "REST", "s3c2"), "/*/pets/*")`
will then pass.
