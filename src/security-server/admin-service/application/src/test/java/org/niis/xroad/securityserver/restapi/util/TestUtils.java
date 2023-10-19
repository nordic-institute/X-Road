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
package org.niis.xroad.securityserver.restapi.util;

import ee.ria.xroad.common.conf.globalconf.GlobalGroupInfo;
import ee.ria.xroad.common.conf.globalconf.MemberInfo;
import ee.ria.xroad.common.conf.globalconf.SharedParameters;
import ee.ria.xroad.common.conf.serverconf.model.TspType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import com.google.common.collect.Ordering;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.restapi.exceptions.WarningDeviation;
import org.niis.xroad.securityserver.restapi.openapi.model.TimestampingService;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test utils for constants and generic object creation
 */
public final class TestUtils {

    // Constants
    public static final String LOCALGROUP = "LOCALGROUP";
    public static final String GLOBALGROUP = "GLOBALGROUP";
    public static final String GLOBALGROUP1 = "GLOBALGROUP1";
    public static final String GLOBALGROUP2 = "GLOBALGROUP2";
    public static final String GLOBALGROUP_CODE1 = "GLOBALGROUP_CODE1";
    public static final String NAME_FOR = "Name for: ";
    public static final String SS0_GET_RANDOM_V1 = "FI:GOV:M1:SS0:getRandom.v1";
    public static final String SS1_GET_RANDOM_V1 = "FI:GOV:M1:SS1:getRandom.v1";
    public static final String SS1_GET_RANDOM_V2 = "FI:GOV:M1:SS1:getRandom.v2";
    public static final String SS1_REST_SERVICECODE = "FI:GOV:M1:SS1:rest-servicecode";
    public static final String SS1_CALCULATE_PRIME = "FI:GOV:M1:SS1:calculatePrime.v1";
    public static final String SS6_OPENAPI_TEST = "FI:GOV:M2:SS6:openapi3-test.v1";
    public static final String URL_HTTPS = "https://foo.bar";
    public static final String URL_HTTP = "http://foo.bar";
    public static final String INSTANCE_FI = "FI";
    public static final String INSTANCE_EE = "EE";
    public static final String MEMBER_CLASS_GOV = "GOV";
    public static final String MEMBER_CLASS_PRO = "PRO";
    public static final String MEMBER_CODE_M1 = "M1";
    public static final String MEMBER_CODE_M2 = "M2";
    public static final String MEMBER_CODE_M3 = "M3";
    public static final String SUBSYSTEM = "SUBSYSTEM";
    public static final String SUBSYSTEM1 = "SS1";
    public static final String SUBSYSTEM2 = "SS2";
    public static final String SUBSYSTEM3 = "SS3";
    public static final String SUBSYSTEM5 = "SS5";
    public static final String SUBSYSTEM6 = "SS6";
    public static final String OWNER_ID = "FI:GOV:M1";
    public static final String NEW_OWNER_ID = "FI:GOV:M2";
    public static final String CLIENT_ID_SS1 = "FI:GOV:M1:SS1";
    public static final String CLIENT_ID_SS2 = "FI:GOV:M1:SS2";
    public static final String CLIENT_ID_SS3 = "FI:GOV:M1:SS3";
    public static final String CLIENT_ID_SS4 = "FI:GOV:M1:SS4";
    public static final String CLIENT_ID_SS5 = "FI:GOV:M2:SS5";
    public static final String CLIENT_ID_SS6 = "FI:GOV:M2:SS6";
    public static final String CLIENT_ID_M2_SS6 = "FI:GOV:M2:SS6";
    public static final String CLIENT_ID_INVALID_INSTANCE_IDENTIFIER = "DUMMY:PRO:M2:SS6";
    public static final String CLIENT_ID_INVALID_MEMBER_CLASS = "FI:DUMMY:M2:SS6";
    public static final String NEW_GROUPCODE = "NEW_GROUPCODE";
    public static final String GROUP_DESC = "GROUP_DESC";
    public static final String NEW_GROUP_DESC = "NEW_GROUP_DESC";
    public static final String INVALID_GROUP_ID = "NOT_VALID";
    public static final int CLIENT_ID_SS1_INITIAL_SERVICEDESCRIPTION_COUNT = 4;
    // values from initial test data: src/test/resources/data.sql
    public static final String DB_GLOBALGROUP_ID = "FI:security-server-owners";
    public static final String DB_GLOBALGROUP_CODE = "security-server-owners";
    public static final String DB_LOCAL_GROUP_ID_1 = "1";
    public static final String DB_LOCAL_GROUP_ID_2 = "2";
    public static final String DB_LOCAL_GROUP_CODE = "group1";
    public static final int GROUP1_ACCESS_RIGHTS_COUNT = 1;
    // services from initial test data: src/test/resources/data.sql
    public static final String FULL_SERVICE_XROAD_GET_RANDOM_OLD = "xroadGetRandomOld.v1";
    public static final String SERVICE_XROAD_GET_RANDOM_OLD = "xroadGetRandomOld";
    public static final String FULL_SERVICE_CODE_BMI_OLD = "bodyMassIndexOld.v1";
    public static final String SERVICE_CODE_BMI_OLD = "bodyMassIndexOld";
    public static final String FULL_SERVICE_CODE_GET_RANDOM = "getRandom.v1";
    public static final String SERVICE_CODE_GET_RANDOM = "getRandom";
    public static final String FULL_SERVICE_CALCULATE_PRIME = "calculatePrime.v1";
    public static final String SERVICE_CALCULATE_PRIME = "calculatePrime";
    // services from wsdl test file: src/test/resources/testservice.wsdl
    public static final String FULL_SERVICE_XROAD_GET_RANDOM = "xroadGetRandom.v1";
    public static final String SERVICE_XROAD_GET_RANDOM = "xroadGetRandom";
    public static final String FULL_SERVICE_CODE_BMI = "bodyMassIndex.v1";
    public static final String SERVICE_CODE_BMI = "bodyMassIndex";

    public static final File ANCHOR_FILE = TestUtils.getTestResourceFile("internal-configuration-anchor.xml");
    public static final String ANCHOR_HASH = "B37E02C0B310497C05D938A8C4446DFA80722F97123852BA8BF20D57";
    public static final String INTERNAL_CERT_CN = "5e18422580b8";

    // key has all roles in data.sql
    public static final String API_KEY_HEADER_VALUE = "X-Road-apikey token=d56e1ca7-4134-4ed4-8030-5f330bdb602a";

    // obsolete items
    public static final ClientId.Conf OBSOLETE_SUBSYSTEM_ID = ClientId.Conf.create("FI", "GOV",
            "M2", "OBSOLETE-SUBSYSTEM");
    public static final GlobalGroupId.Conf OBSOLETE_GGROUP_ID = GlobalGroupId.Conf.create("FI",
            "obsolete-globalgroup");
    public static final long OBSOLETE_SCS_BASE_ENDPOINT_ID = 13L;
    public static final String OBSOLETE_SCS_SERVICE_CODE = "serviceWithObsoleteScs";
    public static final String OBSOLETE_SCS_FULL_SERVICE_CODE = OBSOLETE_SCS_SERVICE_CODE + ".v1";
    public static final SecurityServerId.Conf OWNER_SERVER_ID = SecurityServerId.Conf.create(
            "XRD2", "GOV", "M4", "owner");

    private TestUtils() {
        // noop
    }

    /**
     * Returns a new ClientId with given params
     *
     * @param instance
     * @param memberClass
     * @param memberCode
     * @param subsystem
     * @return ClientId
     */
    public static ClientId.Conf getClientId(String instance, String memberClass, String memberCode, String subsystem) {
        return ClientId.Conf.create(instance, memberClass, memberCode, subsystem);
    }

    /**
     * Returns a new ClientId "FI:GOV:M1:SS1"
     *
     * @return ClientId
     */
    public static ClientId.Conf getM1Ss1ClientId() {
        return getClientId(INSTANCE_FI, MEMBER_CLASS_GOV, MEMBER_CODE_M1, SUBSYSTEM1);
    }

    /**
     * Returns a new ClientId "FI:GOV:M1:SS2"
     *
     * @return ClientId
     */
    public static ClientId.Conf getM1Ss2ClientId() {
        return getClientId(INSTANCE_FI, MEMBER_CLASS_GOV, MEMBER_CODE_M1, SUBSYSTEM2);
    }

    /**
     * Returns a new ClientId which has been built from encoded client id string,
     * such as "FI:GOV:M1:SS1"
     *
     * @param encodedId
     * @return
     */
    public static ClientId.Conf getClientId(String encodedId) {
        return new ClientIdConverter().convertId(encodedId);
    }

    /**
     * Returns a new MemberInfo with given parameters
     *
     * @param instance
     * @param memberClass
     * @param memberCode
     * @param subsystem
     * @return MemberInfo
     */
    public static MemberInfo getMemberInfo(String instance, String memberClass, String memberCode, String subsystem) {
        return new MemberInfo(getClientId(instance, memberClass, memberCode, subsystem),
                subsystem != null ? NAME_FOR + subsystem : NAME_FOR + memberCode);
    }

    /**
     * Returns a new GlobalGroupInfo object
     *
     * @param instance
     * @param groupCode
     * @return
     */
    public static GlobalGroupInfo getGlobalGroupInfo(String instance, String groupCode) {
        return new GlobalGroupInfo(GlobalGroupId.Conf.create(instance, groupCode), groupCode + "-description");
    }

    /**
     * Finds warning with matching code, or returns null
     *
     * @param code
     * @param warningDeviations
     * @return
     */
    public static WarningDeviation findWarning(String code, Collection<WarningDeviation> warningDeviations) {
        if (warningDeviations != null) {
            return warningDeviations.stream()
                    .filter(warning -> code.equals(warning.getCode()))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    /**
     * assert that path <code>http://http://localhost</code> + endpointPathEnd
     * exists in header <code>Location</code> (true for our integration tests)
     *
     * @param endpointPath for example "/api/service-descriptions/12"
     * @param response
     */
    public static void assertLocationHeader(String endpointPath, ResponseEntity response) {
        assertEquals(Collections.singletonList(TEST_API_URL + endpointPath),
                response.getHeaders().get("Location"));
    }

    private static final String TEST_API_URL = "http://localhost";

    /**
     * assert that request does not have <code>Location</code> headers
     *
     * @param response
     */
    public static void assertMissingLocationHeader(ResponseEntity response) {
        List<String> locationHeaders = response.getHeaders().get("Location");
        assertNull(locationHeaders);
    }

    // this key has all roles in data.sql
    public static final String API_KEY_HEADER_PREFIX = "X-Road-apikey token=";
    public static final String API_KEY_TOKEN_WITH_ALL_ROLES = "d56e1ca7-4134-4ed4-8030-5f330bdb602a";

    /**
     * Add Authentication header for API key with all roles
     *
     * @param testRestTemplate
     */
    public static void addApiKeyAuthorizationHeader(TestRestTemplate testRestTemplate) {
        addApiKeyAuthorizationHeader(testRestTemplate, API_KEY_TOKEN_WITH_ALL_ROLES);
    }

    /**
     * Add Authentication header for specific API key
     *
     * @param testRestTemplate
     * @param apiKeyToken API key token
     */
    public static void addApiKeyAuthorizationHeader(TestRestTemplate testRestTemplate,
            String apiKeyToken) {
        testRestTemplate.getRestTemplate().setInterceptors(
                Collections.singletonList((request, body, execution) -> {
                    request.getHeaders()
                            .add("Authorization",
                                    API_KEY_HEADER_PREFIX + apiKeyToken);
                    return execution.execute(request, body);
                }));
    }

    /**
     * Creates a new TspType using the given url and name
     *
     * @param url
     * @param name
     * @return
     */
    public static TspType createTspType(String url, String name) {
        TspType tsp = new TspType();
        tsp.setUrl(url);
        tsp.setName(name);
        return tsp;
    }

    /**
     * Creates a new ApprovedTSAType with the given url and name
     *
     * @param url
     * @param name
     * @return
     */
    public static SharedParameters.ApprovedTSA createApprovedTsaType(String url, String name) {
        SharedParameters.ApprovedTSA approvedTSA = new SharedParameters.ApprovedTSA();
        approvedTSA.setUrl(url);
        approvedTSA.setName(name);
        return approvedTSA;
    }

    /**
     * Creates a new TimestampingService using the given url and name
     *
     * @param url
     * @param name
     * @return
     */
    public static TimestampingService createTimestampingService(String url, String name) {
        TimestampingService timestampingService = new TimestampingService();
        timestampingService.setUrl(url);
        timestampingService.setName(name);
        return timestampingService;
    }

    /**
     * Returns a file from classpath
     *
     * @param pathToFile
     * @return
     */
    public static File getTestResourceFile(String pathToFile) {
        File resource = null;
        try {
            resource = new ClassPathResource(pathToFile).getFile();
        } catch (IOException e) {
            fail("could not get test resource file");
        }
        assertNotNull(resource);
        return resource;
    }

    public static byte[] getTestResourceFileAsBytes(String pathToFile) {
        File file = getTestResourceFile(pathToFile);
        byte[] fileBytes = null;
        try {
            fileBytes = Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            fail("could not read test resource file");
        }
        assertNotNull(fileBytes);
        assertTrue(fileBytes.length > 0);
        return fileBytes;
    }

    public static void mockServletRequestAttributes() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes);
    }

    /**
     * Checks if the sort order of the given Set is correct.
     * @param set
     * @param comparator
     * @return
     */
    public static <T> boolean isSortOrderCorrect(Set<T> set, Comparator<? super T> comparator) {
        return Ordering.from(comparator).isOrdered(set);
    }
}
