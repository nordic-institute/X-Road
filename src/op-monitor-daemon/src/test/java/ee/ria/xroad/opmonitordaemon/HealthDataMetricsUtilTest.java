/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.opmonitordaemon;

import ee.ria.xroad.common.identifier.ServiceId;

import org.junit.Test;

import static ee.ria.xroad.opmonitordaemon.HealthDataMetricsUtil.getLastRequestTimestampGaugeName;
import static ee.ria.xroad.opmonitordaemon.HealthDataMetricsUtil.getRequestCounterName;
import static ee.ria.xroad.opmonitordaemon.HealthDataMetricsUtil.getRequestDurationName;
import static ee.ria.xroad.opmonitordaemon.HealthDataMetricsUtil.getRequestSizeName;
import static ee.ria.xroad.opmonitordaemon.HealthDataMetricsUtil.getServiceTypeName;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests against the utility methods in HealthDataMetricsUtil.
 */
public class HealthDataMetricsUtilTest {

    @Test
    public void testConvertSimpleServiceId() {
        OperationalDataRecord rec = new OperationalDataRecord();
        rec.setServiceXRoadInstance("EE");
        rec.setServiceMemberClass("GOV");
        rec.setServiceMemberCode("testmember");
        rec.setServiceSubsystemCode("testsub");
        rec.setServiceCode("testservice");
        rec.setServiceVersion("v1");
        rec.setServiceType("OPENAPI3");

        // This simple service ID is not escaped in any way in the parameter
        // keys.
        ServiceId serviceId = HealthDataMetricsUtil.getServiceId(rec);
        String expectedServiceId = "EE/GOV/testmember/testsub/testservice/v1";
        assertEquals(expectedServiceId, serviceId.toShortString());

        String jmxKey = getLastRequestTimestampGaugeName(serviceId, true);
        assertEquals(jmxKey, "lastSuccessfulRequestTimestamp("
                + serviceId.toShortString() + ")");

        String regex = HealthDataMetricsUtil.formatMetricMatchRegexp(jmxKey);
        assertTrue(jmxKey.matches(regex));

        jmxKey = getLastRequestTimestampGaugeName(serviceId, false);
        assertEquals(jmxKey, "lastUnsuccessfulRequestTimestamp("
                    + serviceId.toShortString() + ")");

        regex = HealthDataMetricsUtil.formatMetricMatchRegexp(jmxKey);
        assertTrue(jmxKey.matches(regex));

        jmxKey = getRequestCounterName(serviceId, true);
        assertEquals(jmxKey, "successfulRequestCount("
                        + serviceId.toShortString() + ")");

        regex = HealthDataMetricsUtil.formatMetricMatchRegexp(jmxKey);
        assertTrue(jmxKey.matches(regex));

        jmxKey = getRequestCounterName(serviceId, false);
        assertEquals(jmxKey, "unsuccessfulRequestCount("
                        + serviceId.toShortString() + ")");

        regex = HealthDataMetricsUtil.formatMetricMatchRegexp(jmxKey);
        assertTrue(jmxKey.matches(regex));

        jmxKey = getServiceTypeName(serviceId);
        assertEquals(jmxKey, "serviceType(" + serviceId.toShortString() + ")");
        regex = HealthDataMetricsUtil.formatMetricMatchRegexp(jmxKey);
        assertTrue(jmxKey.matches(regex));
    }

    @Test
    public void testConvertServiceIdWithSlashes() {
        OperationalDataRecord rec = new OperationalDataRecord();
        rec.setServiceXRoadInstance("EE/with/slashes");
        rec.setServiceMemberClass("GOV/with/slashes");
        rec.setServiceMemberCode("testmember/with/slashes");
        rec.setServiceSubsystemCode("testsub/with/slashes");
        rec.setServiceCode("testservice/with/slashes");
        rec.setServiceVersion("v1/with/slashes");

        ServiceId serviceId = HealthDataMetricsUtil.getServiceId(rec);

        String escapedId = HealthDataMetricsUtil.escapeServiceId(serviceId);
        String expectedServiceId =
                "EE&#47;with&#47;slashes/GOV&#47;with&#47;slashes/"
                + "testmember&#47;with&#47;slashes"
                + "/testsub&#47;with&#47;slashes"
                + "/testservice&#47;with&#47;slashes"
                + "/v1&#47;with&#47;slashes";
        assertEquals(expectedServiceId, escapedId);
        System.out.println(escapedId);

        String requestSizeKey = getRequestSizeName(serviceId);
        assertEquals("requestSize(" + escapedId + ")", requestSizeKey);
    }

    @Test
    public void testConvertNonAsciiServiceId() {
        OperationalDataRecord rec = new OperationalDataRecord();
        rec.setServiceXRoadInstance("EE");
        rec.setServiceMemberClass("BÖÖ");
        rec.setServiceMemberCode("testmember");
        rec.setServiceSubsystemCode("testservice_provider");
        rec.setServiceCode("[\"Með_suð_í_eyrum\"]");
        rec.setServiceVersion("v012");

        ServiceId serviceId = HealthDataMetricsUtil.getServiceId(rec);
        String expectedServiceId = "EE/BÖÖ/testmember"
                + "/testservice_provider/[\"Með_suð_í_eyrum\"]/v012";
        assertEquals(expectedServiceId, serviceId.toShortString());

        String lastSuccessfulRequestTsKey = getLastRequestTimestampGaugeName(
                serviceId, true);
        assertEquals(lastSuccessfulRequestTsKey,
                "lastSuccessfulRequestTimestamp(EE/BÖÖ/testmember"
                        + "/testservice_provider/&#91;&quot;Með_suð_í_"
                        + "eyrum&quot;&#93;/v012)");

        String regex = HealthDataMetricsUtil.formatMetricMatchRegexp(
                lastSuccessfulRequestTsKey);
        assertTrue(lastSuccessfulRequestTsKey.matches(regex));
    }

    @Test
    public void testConvertServiceIdWithDots() {
        OperationalDataRecord rec = new OperationalDataRecord();
        rec.setServiceXRoadInstance("EE");
        rec.setServiceMemberClass("foo.bar");
        rec.setServiceMemberCode("testmember");
        rec.setServiceSubsystemCode("testservice_provider");
        rec.setServiceCode("Закрой.за.мной.дверь.я.ухожу");

        ServiceId serviceId = HealthDataMetricsUtil.getServiceId(rec);
        String expectedServiceId = "EE/foo.bar/testmember"
                + "/testservice_provider/Закрой.за.мной.дверь.я.ухожу";
        assertEquals(expectedServiceId, serviceId.toShortString());

        String requestDurationKey = getRequestDurationName(serviceId);
        assertEquals(requestDurationKey,
                "requestDuration(EE/foo&#46;bar/testmember"
                        + "/testservice_provider"
                        + "/Закрой&#46;за&#46;мной&#46;дверь&#46;я&#46;ухожу)");

        String regex = HealthDataMetricsUtil.formatMetricMatchRegexp(
                requestDurationKey);
        assertTrue(requestDurationKey.matches(regex));
    }

    @Test
    public void testConvertServiceIdWithSpacesCommasEtc() {
        OperationalDataRecord rec = new OperationalDataRecord();
        rec.setServiceXRoadInstance("EE TEST");
        rec.setServiceMemberClass("foo\\bar");
        rec.setServiceMemberCode("testmember, simple");
        rec.setServiceSubsystemCode("testservice_provider");
        rec.setServiceCode("a service with spaces");

        ServiceId serviceId = HealthDataMetricsUtil.getServiceId(rec);
        String expectedServiceId = "EE TEST/foo\\bar/testmember, simple"
                + "/testservice_provider/a service with spaces";
        assertEquals(expectedServiceId, serviceId.toShortString());

        String requestDurationKey = getRequestDurationName(serviceId);
        assertEquals(requestDurationKey,
                "requestDuration(EE&#32;TEST/foo&#92;bar"
                        + "/testmember&#44;&#32;simple/testservice_provider"
                        + "/a&#32;service&#32;with&#32;spaces)");

        String regex = HealthDataMetricsUtil.formatMetricMatchRegexp(
                requestDurationKey);
        assertTrue(requestDurationKey.matches(regex));
    }
}
