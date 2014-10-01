package ee.cyber.sdsb.proxyui;

import java.util.Collection;

import org.junit.Test;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.proxyui.WSDLParser.ServiceInfo;

import static org.junit.Assert.assertEquals;

public class WSDLParserTest {

    @Test
    public void readValidWsdl() throws Exception {
        Collection<ServiceInfo> si =
                WSDLParser.parseWSDL("file:src/test/resources/valid.wsdl");
        assertEquals(3, si.size());
    }

    @Test
    public void readInvalidWsdl() throws Exception {
        try {
            WSDLParser.parseWSDL("file:src/test/resources/invalid.wsdl");
        } catch (CodedException expected) {
        }
    }

    @Test
    public void readWsdlFromInvalidUrl() throws Exception {
        try {
            WSDLParser.parseWSDL("http://localhost:1234/foo.wsdl");
        } catch (CodedException expected) {
        }
    }

    @Test
    public void readFaultInsteadOfWsdl() throws Exception {
       try {
           WSDLParser.parseWSDL("file:src/test/resources/fault.xml");
       } catch (CodedException expected) {
       }
    }
}
