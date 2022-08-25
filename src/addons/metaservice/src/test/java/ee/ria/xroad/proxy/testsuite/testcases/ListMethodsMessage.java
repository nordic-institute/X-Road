/**
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
package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.conf.serverconf.model.DescriptionType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.metadata.MethodListType;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;
import ee.ria.xroad.proxy.testsuite.TestSuiteServerConf;
import ee.ria.xroad.proxy.util.MetaserviceTestUtil;

import javax.xml.soap.SOAPBody;

import java.util.Arrays;
import java.util.List;

import static ee.ria.xroad.proxy.util.MetaserviceTestUtil.verifyAndGetSingleBodyElementOfType;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.isIn;
import static org.junit.Assert.assertThat;

/**
 * Send message to see what methods (services) are available
 * Result: client receives message containing existing services.
 */
public class ListMethodsMessage extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public ListMethodsMessage() {
        requestFileName = "listMethods.query";
    }

    // this is the service provider we want the methods listed from, it's from the query file
    private final ClientId.Conf expectedClientId =
            ClientId.Conf.create("EE", "BUSINESS", "producer", "SUB");

    private final List<ServiceId.Conf> expectedServices = Arrays.asList(
            MetaserviceTestUtil.createService("getRandom"),
            MetaserviceTestUtil.createService("helloService"),
            MetaserviceTestUtil.createService("ListMembers"));


    @Override
    protected void validateNormalResponse(Message receivedResponse)
            throws Exception {
        SoapMessageImpl soap = (SoapMessageImpl) receivedResponse.getSoap();
        SOAPBody body = soap.getSoap().getSOAPBody();

        // the content type might arrive without the space,
        // even though the MetadataServiceHandler uses the same MimeUtils value
        List<String> expectedContentTypes = MetaserviceTestUtil.xmlUtf8ContentTypes();

        List<ServiceId.Conf> resultServices = verifyAndGetSingleBodyElementOfType(body, MethodListType.class).getService();

        assertThat("Wrong amount of services",
                resultServices.size(), is(expectedServices.size()));

        assertThat("Wrong services", resultServices, containsInAnyOrder(expectedServices.toArray()));

        assertThat("Wrong content type", receivedResponse.getContentType(), isIn(expectedContentTypes));
    }

    @Override
    protected void startUp() throws Exception {
        super.startUp();

        ServerConf.reload(new TestSuiteServerConf() {
            @Override
            public List<ServiceId.Conf> getServicesByDescriptionType(ClientId serviceProvider,
                                                                DescriptionType descriptionType) {
                assertThat("Client id does not match expected", serviceProvider, is(expectedClientId));
                return expectedServices;
            }
        });
    }

}
