package ee.cyber.sdsb.common.identifier;

import java.io.FileInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import ee.cyber.sdsb.common.identifier.IdentifierTypeConverter.GenericSdsbIdAdapter;

import static ee.cyber.sdsb.common.identifier.IdentifierTypeConverter.*;
import static ee.cyber.sdsb.common.identifier.SdsbObjectType.*;
import static org.junit.Assert.*;

public class IdentifierTypeConverterTest {

    @Test
    public void readClientIdentifier() throws Exception {
        ClientId id = parseClientId(
                fileToType("clientid.xml", MEMBER,
                        SdsbClientIdentifierType.class));

        assertEquals("EE", id.getSdsbInstance());
        assertEquals("COMPANY", id.getMemberClass());
        assertEquals("FOOBAR", id.getMemberCode());
        assertEquals("MEMBER:EE/COMPANY/FOOBAR", id.toString());
    }

    @Test
    public void readServiceIdentifier() throws Exception {
        ServiceId id = parseServiceId(
                fileToType("serviceid.xml", SERVICE,
                        SdsbServiceIdentifierType.class));

        assertEquals("EE", id.getSdsbInstance());
        assertEquals("BUSINESS", id.getMemberClass());
        assertEquals("JODA", id.getMemberCode());
        assertEquals("getState", id.getServiceCode());
        assertEquals("SERVICE:EE/BUSINESS/JODA/getState", id.toString());
        assertNull(id.getServiceVersion());
    }

    @Test
    public void readServiceIdentifierWithVersion() throws Exception {
        ServiceId id = parseServiceId(
                fileToType("serviceid-version.xml", SERVICE,
                        SdsbServiceIdentifierType.class));

        assertEquals("EE", id.getSdsbInstance());
        assertEquals("BUSINESS", id.getMemberClass());
        assertEquals("JODA", id.getMemberCode());
        assertEquals("getState", id.getServiceCode());
        assertEquals("SERVICE:EE/BUSINESS/JODA/getState/v1", id.toString());
        assertEquals("v1", id.getServiceVersion());
    }

    @Test
    public void readSecurityCategoryIdentifier() throws Exception {
        SecurityCategoryId id = parseSecurityCategoryId(
                fileToType("securitycategoryid.xml", SECURITYCATEGORY,
                        SdsbSecurityCategoryIdentifierType.class));

        assertEquals("EE", id.getSdsbInstance());
        assertEquals("ISKE_H", id.getCategoryCode());
        assertEquals("SECURITYCATEGORY:EE/ISKE_H", id.toString());
    }

    @Test
    public void readCentralServiceIdentifier() throws Exception {
        CentralServiceId id = parseCentralServiceId(
                fileToType("centralserviceid.xml", CENTRALSERVICE,
                        SdsbCentralServiceIdentifierType.class));

        assertEquals("EE", id.getSdsbInstance());
        assertEquals("rahvastikuregister_isikuandmed", id.getServiceCode());
        assertEquals("CENTRALSERVICE:EE/rahvastikuregister_isikuandmed",
                id.toString());
    }

    @Test
    public void readSecurityServerIdentifier() throws Exception {
        SecurityServerId id = parseSecurityServerId(
                fileToType("securityserverid.xml", SERVER,
                        SdsbSecurityServerIdentifierType.class));

        assertEquals("EE", id.getSdsbInstance());
        assertEquals("BUSINESS", id.getMemberClass());
        assertEquals("123456789", id.getMemberCode());
        assertEquals("bambi", id.getServerCode());
        assertEquals("SERVER:EE/BUSINESS/123456789/bambi", id.toString());
    }

    @Test
    public void readGlobalGroupIdentifier() throws Exception {
        GlobalGroupId id = parseGlobalGroupId(
                fileToType("globalgroupid.xml", GLOBALGROUP,
                        SdsbGlobalGroupIdentifierType.class));

        assertEquals("EE", id.getSdsbInstance());
        assertEquals("perearstid", id.getGroupCode());
        assertEquals("GLOBALGROUP:EE/perearstid", id.toString());
    }

    @Test
    public void readLocalGroupIdentifier() throws Exception {
        LocalGroupId id = parseLocalGroupId(
                fileToType("localgroupid.xml", LOCALGROUP,
                        SdsbLocalGroupIdentifierType.class));

        assertNull(id.getSdsbInstance());
        assertEquals("lokaalgrupp", id.getGroupCode());
        assertEquals("LOCALGROUP:lokaalgrupp", id.toString());
    }

    @Test
    public void genericIdentifierAdapter() throws Exception {
        ClientId id = parseClientId(
                fileToType("clientid.xml", MEMBER,
                        SdsbClientIdentifierType.class));

        GenericSdsbIdAdapter adapter = new GenericSdsbIdAdapter();
        SdsbIdentifierType type = adapter.marshal(id);
        assertTrue(type instanceof SdsbClientIdentifierType);

        SdsbId backToId = adapter.unmarshal(type);
        assertEquals(id, backToId);
    }

    private static <T> T fileToType(String file, SdsbObjectType expectedType,
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
