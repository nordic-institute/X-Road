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
package ee.ria.xroad.common.conf.globalconf;

import ee.ria.xroad.common.conf.AbstractXmlConf;
import ee.ria.xroad.common.conf.globalconf.privateparameters.v3.ObjectFactory;
import ee.ria.xroad.common.conf.globalconf.privateparameters.v3.PrivateParametersTypeV3;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import lombok.Getter;

import java.nio.file.Path;
import java.time.OffsetDateTime;

/**
 * Contains private parameters of a configuration instance.
 */
public class PrivateParametersV3 extends AbstractXmlConf<PrivateParametersTypeV3> implements PrivateParametersProvider {
    private static final JAXBContext JAXB_CONTEXT = createJAXBContext();

    private final PrivateParametersV3Converter converter = new PrivateParametersV3Converter();

    @Getter
    private final PrivateParameters privateParameters;

    @Getter
    private final OffsetDateTime expiresOn;


    // variable to prevent using load methods after construction
    private boolean initCompleted;


    PrivateParametersV3(byte[] content) {
        super(content, PrivateParametersSchemaValidatorV3.class);
        expiresOn = OffsetDateTime.MAX;
        privateParameters = converter.convert(confType);
        initCompleted = true;
    }

    PrivateParametersV3(Path privateParametersPath, OffsetDateTime expiresOn) {
        super(privateParametersPath.toString(), PrivateParametersSchemaValidatorV3.class);
        this.expiresOn = expiresOn;
        privateParameters = converter.convert(confType);
        initCompleted = true;
    }

    PrivateParametersV3(PrivateParametersV3 original, OffsetDateTime newExpiresOn) {
        super(original);
        expiresOn = newExpiresOn;
        privateParameters = original.getPrivateParameters();
        initCompleted = true;
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
