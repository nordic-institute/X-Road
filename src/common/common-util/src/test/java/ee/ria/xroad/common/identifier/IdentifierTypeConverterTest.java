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
package ee.ria.xroad.common.identifier;

import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.ExpectedCodedException;
import ee.ria.xroad.common.identifier.IdentifierTypeConverter.GenericXRoadIdAdapter;
import ee.ria.xroad.common.util.XmlUtils;

import org.junit.Rule;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;

import java.io.FileInputStream;

import static ee.ria.xroad.common.identifier.IdentifierTypeConverter.parseClientId;
import static ee.ria.xroad.common.identifier.IdentifierTypeConverter.parseGlobalGroupId;
import static ee.ria.xroad.common.identifier.IdentifierTypeConverter.parseLocalGroupId;
import static ee.ria.xroad.common.identifier.IdentifierTypeConverter.parseSecurityServerId;
import static ee.ria.xroad.common.identifier.IdentifierTypeConverter.parseServiceId;
import static ee.ria.xroad.common.identifier.XRoadObjectType.GLOBALGROUP;
import static ee.ria.xroad.common.identifier.XRoadObjectType.LOCALGROUP;
import static ee.ria.xroad.common.identifier.XRoadObjectType.MEMBER;
import static ee.ria.xroad.common.identifier.XRoadObjectType.SERVER;
import static ee.ria.xroad.common.identifier.XRoadObjectType.SERVICE;
import static ee.ria.xroad.common.identifier.XRoadObjectType.SUBSYSTEM;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests to verify X-Road identifier converter behavior.
 */
public class IdentifierTypeConverterTest {

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    /**
     * Test to ensure client ID (MEMBER) can be read from XML.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readClientIdentifierAsMember() throws Exception {
        ClientId id = parseClientId(
                fileToType("clientid.xml", MEMBER,
                        XRoadClientIdentifierType.class));

        assertEquals(MEMBER, id.getObjectType());
        assertEquals("EE", id.getXRoadInstance());
        assertEquals("COMPANY", id.getMemberClass());
        assertEquals("FOOBAR", id.getMemberCode());
        assertEquals("MEMBER:EE/COMPANY/FOOBAR", id.toString());
    }

    /**
     * Test to ensure client ID (MEMBER) reading from XML fails, if excessive
     * subsystem code element is present.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readInvalidClientIdentifierAsMember() throws Exception {
        thrown.expectError(ErrorCodes.X_INVALID_CLIENT_IDENTIFIER);

        ClientId id = parseClientId(
                fileToType("invalid-clientid-member.xml", MEMBER,
                        XRoadClientIdentifierType.class));
    }

    /**
     * Test to ensure client ID (SUBSYSTEM) can be read from XML.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readClientIdentifierAsSubsystem() throws Exception {
        ClientId id = parseClientId(fileToType("clientid-subsystem.xml",
                SUBSYSTEM, XRoadClientIdentifierType.class));

        assertEquals(SUBSYSTEM, id.getObjectType());
        assertEquals("EE", id.getXRoadInstance());
        assertEquals("COMPANY", id.getMemberClass());
        assertEquals("FOOBAR", id.getMemberCode());
        assertEquals("SUBSYSTEM", id.getSubsystemCode());
        assertEquals("SUBSYSTEM:EE/COMPANY/FOOBAR/SUBSYSTEM", id.toString());
    }

    /**
     * Test to ensure client ID (SUBSYSTEM) reading from XML fails, if
     * subsystem code is missing.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readInvalidClientIdentifierAsSubsystem() throws Exception {
        thrown.expectError(ErrorCodes.X_INVALID_CLIENT_IDENTIFIER);

        ClientId id = parseClientId(fileToType("invalid-clientid-subsystem.xml",
                SUBSYSTEM, XRoadClientIdentifierType.class));
    }

    /**
     * Test to ensure service ID can be read from XML.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readServiceIdentifier() throws Exception {
        ServiceId id = parseServiceId(
                fileToType("serviceid.xml", SERVICE,
                        XRoadServiceIdentifierType.class));

        assertEquals("EE", id.getXRoadInstance());
        assertEquals("BUSINESS", id.getMemberClass());
        assertEquals("JODA", id.getMemberCode());
        assertEquals("getState", id.getServiceCode());
        assertEquals("SERVICE:EE/BUSINESS/JODA/getState", id.toString());
        assertNull(id.getServiceVersion());
    }

    /**
     * Test to ensure service ID with version can be read from XML.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readServiceIdentifierWithVersion() throws Exception {
        ServiceId id = parseServiceId(
                fileToType("serviceid-version.xml", SERVICE,
                        XRoadServiceIdentifierType.class));

        assertEquals("EE", id.getXRoadInstance());
        assertEquals("BUSINESS", id.getMemberClass());
        assertEquals("JODA", id.getMemberCode());
        assertEquals("getState", id.getServiceCode());
        assertEquals("SERVICE:EE/BUSINESS/JODA/getState/v1", id.toString());
        assertEquals("v1", id.getServiceVersion());
    }

    /**
     * Test to ensure security server ID can be read from XML.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readSecurityServerIdentifier() throws Exception {
        SecurityServerId id = parseSecurityServerId(
                fileToType("securityserverid.xml", SERVER,
                        XRoadSecurityServerIdentifierType.class));

        assertEquals("EE", id.getXRoadInstance());
        assertEquals("BUSINESS", id.getMemberClass());
        assertEquals("123456789", id.getMemberCode());
        assertEquals("bambi", id.getServerCode());
        assertEquals("SERVER:EE/BUSINESS/123456789/bambi", id.toString());
    }

    /**
     * Test to ensure global group ID can be read from XML.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readGlobalGroupIdentifier() throws Exception {
        GlobalGroupId id = parseGlobalGroupId(
                fileToType("globalgroupid.xml", GLOBALGROUP,
                        XRoadGlobalGroupIdentifierType.class));

        assertEquals("EE", id.getXRoadInstance());
        assertEquals("perearstid", id.getGroupCode());
        assertEquals("GLOBALGROUP:EE/perearstid", id.toString());
    }

    /**
     * Test to ensure local group ID can be read from XML.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readLocalGroupIdentifier() throws Exception {
        LocalGroupId id = parseLocalGroupId(
                fileToType("localgroupid.xml", LOCALGROUP,
                        XRoadLocalGroupIdentifierType.class));

        assertNull(id.getXRoadInstance());
        assertEquals("lokaalgrupp", id.getGroupCode());
        assertEquals("LOCALGROUP:lokaalgrupp", id.toString());
    }

    /**
     * Test to ensure a generic ID can be read from XML.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void genericIdentifierAdapter() throws Exception {
        ClientId id = parseClientId(
                fileToType("clientid.xml", MEMBER,
                        XRoadClientIdentifierType.class));

        GenericXRoadIdAdapter adapter = new GenericXRoadIdAdapter();
        XRoadIdentifierType type = adapter.marshal(id);
        assertTrue(type instanceof XRoadClientIdentifierType);

        XRoadId backToId = adapter.unmarshal(type);
        assertEquals(id, backToId);
    }

    private static <T> T fileToType(String file, XRoadObjectType expectedType,
            Class<T> type) throws Exception {
        return IdentifierXmlNodeParser.parseType(expectedType,
                fileToNode(file), type);
    }

    private static Node fileToNode(String fileName) throws Exception {
        DocumentBuilderFactory f = XmlUtils.createDocumentBuilderFactory();
        f.setNamespaceAware(true);

        Document doc = f.newDocumentBuilder().parse(
                new FileInputStream(
                        "src/test/resources/identifiers/" + fileName));

        return doc.getDocumentElement();
    }
}
