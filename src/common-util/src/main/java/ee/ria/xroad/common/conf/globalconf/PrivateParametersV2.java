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
package ee.ria.xroad.common.conf.globalconf;

import ee.ria.xroad.common.conf.AbstractXmlConf;
import ee.ria.xroad.common.conf.globalconf.privateparameters.v2.ManagementServiceType;
import ee.ria.xroad.common.conf.globalconf.privateparameters.v2.ObjectFactory;
import ee.ria.xroad.common.conf.globalconf.privateparameters.v2.PrivateParametersType;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import java.math.BigInteger;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Contains private parameters of a configuration instance.
 */
public class PrivateParametersV2 extends AbstractXmlConf<PrivateParametersType> {
    private static final JAXBContext JAXB_CONTEXT = createJAXBContext();

    private final OffsetDateTime expiresOn;

    // variable to prevent using load methods after constrution
    private boolean initCompleted;

    PrivateParametersV2(byte[] content) {
        super(content, PrivateParametersSchemaValidatorV2.class);
        expiresOn = OffsetDateTime.MAX;
        initCompleted = true;
    }

    PrivateParametersV2(Path privateParametersPath, OffsetDateTime expiresOn) {
        super(privateParametersPath.toString(), PrivateParametersSchemaValidatorV2.class);
        this.expiresOn = expiresOn;
        initCompleted = true;
    }

    PrivateParametersV2(PrivateParametersV2 original, OffsetDateTime newExpiresOn) {
        super(original);
        expiresOn = newExpiresOn;
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

    String getInstanceIdentifier() {
        return confType.getInstanceIdentifier();
    }

    List<ConfigurationSource> getConfigurationSource() {
        return confType.getConfigurationAnchor().stream()
                .map(ConfigurationAnchorV2::new)
                .collect(Collectors.toList());
    }

    BigInteger getTimeStampingIntervalSeconds() {
        return confType.getTimeStampingIntervalSeconds();
    }

    ManagementServiceType getManagementService() {
        return confType.getManagementService();
    }

    public OffsetDateTime getExpiresOn() {
        return expiresOn;
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
