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
package ee.ria.xroad.common.conf.globalconf;

import ee.ria.xroad.common.conf.AbstractXmlConf;
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v4.ObjectFactory;
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v4.SharedParametersTypeV4;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import lombok.AccessLevel;
import lombok.Getter;

import java.io.IOException;
import java.nio.file.Path;
import java.security.cert.CertificateEncodingException;
import java.time.OffsetDateTime;

@Getter(AccessLevel.PACKAGE)
public class SharedParametersV4 extends AbstractXmlConf<SharedParametersTypeV4> implements SharedParametersProvider {
    private static final JAXBContext JAXB_CONTEXT = createJAXBContext();

    private final SharedParametersV4Converter converter = new SharedParametersV4Converter();

    @Getter
    private final SharedParameters sharedParameters;

    @Getter
    private final OffsetDateTime expiresOn;

    // variable to prevent using load methods after construction
    private final boolean initCompleted;

    // This constructor is used for simple verifications after configuration download.
    // It does not initialise class fully!
    public SharedParametersV4(byte[] content) throws CertificateEncodingException, IOException {
        super(content, SharedParametersSchemaValidatorV4.class);
        expiresOn = OffsetDateTime.MAX;
        sharedParameters = converter.convert(confType);
        initCompleted = true;
    }

    public SharedParametersV4(Path sharedParametersPath, OffsetDateTime expiresOn) throws CertificateEncodingException, IOException {
        super(sharedParametersPath.toString(), SharedParametersSchemaValidatorV4.class);
        this.expiresOn = expiresOn;
        sharedParameters = converter.convert(confType);
        initCompleted = true;
    }

    private SharedParametersV4(SharedParametersV4 original, OffsetDateTime newExpiresOn) throws CertificateEncodingException, IOException {
        super(original);
        expiresOn = newExpiresOn;
        sharedParameters = converter.convert(confType);
        initCompleted = true;
    }

    @Override
    public SharedParametersProvider refresh(OffsetDateTime fileExpiresOn) throws CertificateEncodingException, IOException {
        return new SharedParametersV4(this, fileExpiresOn);
    }

    @Override
    public SharedParametersMarshaller getMarshaller() {
        return new SharedParametersV4Marshaller();
    }

    @Override
    public void load(String fileName) throws Exception {
        throwIfInitCompleted();
        super.load(fileName);
    }

    @Override
    public void load(byte[] data) throws Exception {
        throwIfInitCompleted();
        super.load(data);
    }


    private void throwIfInitCompleted() {
        if (initCompleted) {
            throw new IllegalStateException("This object can not be reloaded");
        }
    }

    @Override
    protected JAXBContext getJAXBContext() {
        return JAXB_CONTEXT;
    }

    private static JAXBContext createJAXBContext() {
        try {
            return JAXBContext.newInstance(ObjectFactory.class);
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }

}
