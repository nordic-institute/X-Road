/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import java.io.FileInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import ee.ria.xroad.common.identifier.IdentifierTypeConverter.GenericXroadIdAdapter;

import static ee.ria.xroad.common.identifier.IdentifierTypeConverter.*;
import static ee.ria.xroad.common.identifier.XroadObjectType.*;
import static org.junit.Assert.*;

/**
 * Tests to verify XROAD identifier converter behavior.
 */
public class IdentifierTypeConverterTest {

    /**
     * Test to ensure client ID can be read from XML.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readClientIdentifier() throws Exception {
        ClientId id = parseClientId(
                fileToType("clientid.xml", MEMBER,
                        XroadClientIdentifierType.class));

        assertEquals("EE", id.getXRoadInstance());
        assertEquals("COMPANY", id.getMemberClass());
        assertEquals("FOOBAR", id.getMemberCode());
        assertEquals("MEMBER:EE/COMPANY/FOOBAR", id.toString());
    }

    /**
     * Test to ensure service ID can be read from XML.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readServiceIdentifier() throws Exception {
        ServiceId id = parseServiceId(
                fileToType("serviceid.xml", SERVICE,
                        XroadServiceIdentifierType.class));

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
                        XroadServiceIdentifierType.class));

        assertEquals("EE", id.getXRoadInstance());
        assertEquals("BUSINESS", id.getMemberClass());
        assertEquals("JODA", id.getMemberCode());
        assertEquals("getState", id.getServiceCode());
        assertEquals("SERVICE:EE/BUSINESS/JODA/getState/v1", id.toString());
        assertEquals("v1", id.getServiceVersion());
    }

    /**
     * Test to ensure security category ID can be read from XML.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readSecurityCategoryIdentifier() throws Exception {
        SecurityCategoryId id = parseSecurityCategoryId(
                fileToType("securitycategoryid.xml", SECURITYCATEGORY,
                        XroadSecurityCategoryIdentifierType.class));

        assertEquals("EE", id.getXRoadInstance());
        assertEquals("ISKE_H", id.getCategoryCode());
        assertEquals("SECURITYCATEGORY:EE/ISKE_H", id.toString());
    }

    /**
     * Test to ensure central service ID can be read from XML.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readCentralServiceIdentifier() throws Exception {
        CentralServiceId id = parseCentralServiceId(
                fileToType("centralserviceid.xml", CENTRALSERVICE,
                        XroadCentralServiceIdentifierType.class));

        assertEquals("EE", id.getXRoadInstance());
        assertEquals("rahvastikuregister_isikuandmed", id.getServiceCode());
        assertEquals("CENTRALSERVICE:EE/rahvastikuregister_isikuandmed",
                id.toString());
    }

    /**
     * Test to ensure security server ID can be read from XML.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readSecurityServerIdentifier() throws Exception {
        SecurityServerId id = parseSecurityServerId(
                fileToType("securityserverid.xml", SERVER,
                        XroadSecurityServerIdentifierType.class));

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
                        XroadGlobalGroupIdentifierType.class));

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
                        XroadLocalGroupIdentifierType.class));

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
                        XroadClientIdentifierType.class));

        GenericXroadIdAdapter adapter = new GenericXroadIdAdapter();
        XroadIdentifierType type = adapter.marshal(id);
        assertTrue(type instanceof XroadClientIdentifierType);

        XroadId backToId = adapter.unmarshal(type);
        assertEquals(id, backToId);
    }

    private static <T> T fileToType(String file, XroadObjectType expectedType,
            Class<T> type) throws Exception {
        return IdentifierXmlNodeParser.parseType(expectedType,
                fileToNode(file), type);
    }

    private static Node fileToNode(String fileName) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);

        Document doc = f.newDocumentBuilder().parse(
                new FileInputStream(
                        "src/test/resources/identifiers/" + fileName));

        return doc.getDocumentElement();
    }
}
