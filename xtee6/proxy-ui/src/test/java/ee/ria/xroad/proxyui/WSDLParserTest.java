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
