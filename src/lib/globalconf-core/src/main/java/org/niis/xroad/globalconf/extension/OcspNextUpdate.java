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
package org.niis.xroad.globalconf.extension;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import lombok.ToString;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.model.AbstractXmlConf;
import org.niis.xroad.globalconf.schema.ocspnextupdateparameters.ObjectFactory;
import org.niis.xroad.globalconf.schema.ocspnextupdateparameters.OcspNextUpdateType;

/**
 * Ocsp next update parameters
 */
@ToString(onlyExplicitlyIncluded = true)
public class OcspNextUpdate extends AbstractXmlConf<OcspNextUpdateType> {
    private static final JAXBContext JAXB_CONTEXT = createJAXBContext();
    /**
     * The default file name of the configuration part
     */
    public static final String FILE_NAME_OCSP_NEXT_UPDATE_PARAMETERS =
            "nextupdate-params.xml";

    /**
     * Default value for ocsp nextUpdate verification
     */
    public static final boolean OCSP_NEXT_UPDATE_DEFAULT = true;

    public OcspNextUpdate() {
        super(OcspNextUpdateSchemaValidator.class);
    }

    @ToString.Include
    public boolean shouldVerifyOcspNextUpdate() {
        return confType.isVerifyNextUpdate();
    }

    @Override
    protected JAXBContext getJAXBContext() {
        return JAXB_CONTEXT;
    }

    private static JAXBContext createJAXBContext() {
        try {
            return JAXBContext.newInstance(ObjectFactory.class);
        } catch (JAXBException e) {
            throw XrdRuntimeException.systemException(e);
        }
    }
}
