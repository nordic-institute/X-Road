/*
 * The MIT License
 *
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
package org.niis.xroad.globalconf.model;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.schema.sharedparameters.v3.ObjectFactory;
import org.niis.xroad.globalconf.schema.sharedparameters.v3.SharedParametersTypeV3;

import javax.xml.validation.Schema;

public class SharedParametersV3Marshaller extends AbstractSharedParametersMarshaller<SharedParametersTypeV3> {
    private static final JAXBContext JAXB_CONTEXT = createJaxbContext();

    private static JAXBContext createJaxbContext() {
        try {
            return JAXBContext.newInstance(ObjectFactory.class);
        } catch (JAXBException e) {
            throw XrdRuntimeException.systemException(e);
        }
    }

    @Override
    JAXBContext getJaxbContext() {
        return JAXB_CONTEXT;
    }

    @Override
    Schema getSchema() {
        return SharedParametersSchemaValidatorV3.getSchema();
    }

    @Override
    JAXBElement<SharedParametersTypeV3> convert(SharedParameters parameters) {
        return new ObjectFactory().createConf(SharedParametersV3ToXmlConverter.INSTANCE.convert(parameters));
    }
}
