package org.niis.xroad.restapi.openapi;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * Trying to reproduce XRDDEV-1392 with minimal set of tests. Don't commit to git
 *
 * Run with: ../gradlew test --tests MinimalSampleSuite
 *
 * Results for me:
 * 22 tests completed, 1 failed
 *
 * > Task :proxy-ui-api:test FAILED
 *
 * FAILURE: Build failed with an exception.
 *
 * * What went wrong:
 * Execution failed for task ':proxy-ui-api:test'.
 * > There were failing tests. See the report at: file:///home/janne/projects/niis/xroad-ui/git/X-Road-REST-UI/src/proxy-ui-api/build/reports/tests/test/index.html
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
        DiagnosticsApiControllerTest.class,
        ServiceDescriptionsApiControllerIntegrationTest.class
        })
public class MinimalSampleSuite {
}
