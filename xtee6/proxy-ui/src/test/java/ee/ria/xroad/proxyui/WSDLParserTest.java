package ee.ria.xroad.proxyui;

import java.util.Collection;

import org.junit.Test;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.proxyui.WSDLParser.ServiceInfo;

import static org.junit.Assert.assertEquals;

/**
 * Tests correctness of the WSDL parser.
 */
public class WSDLParserTest {

    /**
     * Test if a valid WSDL is parsed correctly.
     * @throws Exception in case of any errors
     */
    @Test
    public void readValidWsdl() throws Exception {
        Collection<ServiceInfo> si =
                WSDLParser.parseWSDL("file:src/test/resources/valid.wsdl");
        assertEquals(3, si.size());
    }

    /**
     * Test if an invalid WSDL is recognized.
     * @throws Exception in case of any errors
     */
    @Test(expected = CodedException.class)
    public void readInvalidWsdl() throws Exception {
        WSDLParser.parseWSDL("file:src/test/resources/invalid.wsdl");
    }

    /**
     * Test if an invalid URL is recognized.
     * @throws Exception in case of any errors
     */
    @Test(expected = CodedException.class)
    public void readWsdlFromInvalidUrl() throws Exception {
        WSDLParser.parseWSDL("http://localhost:1234/foo.wsdl");
    }

    /**
     * Test if a fault XML is recognized.
     * @throws Exception in case of any errors
     */
    @Test(expected = CodedException.class)
    public void readFaultInsteadOfWsdl() throws Exception {
       WSDLParser.parseWSDL("file:src/test/resources/fault.xml");
    }
}
