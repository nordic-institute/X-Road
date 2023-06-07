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
import ee.ria.xroad.common.conf.globalconf.privateparameters.v2.ConfigurationAnchorType;
import ee.ria.xroad.common.conf.globalconf.privateparameters.v2.ObjectFactory;

import org.apache.commons.lang3.builder.HashCodeBuilder;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.datatype.XMLGregorianCalendar;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Configuration anchor specifies the configuration source where a security
 * server can download global configuration.
 */
public class ConfigurationAnchorV2
        extends AbstractXmlConf<ConfigurationAnchorType>
        implements ConfigurationSource {
    private static final JAXBContext JAXB_CONTEXT = createJAXBContext();

    ConfigurationAnchorV2(ConfigurationAnchorType t) {
        confType = t;
    }

    /**
     * @param fileName the configuration anchor file name
     */
    public ConfigurationAnchorV2(String fileName) {
        super(fileName, PrivateParametersSchemaValidatorV2.class); // also applies to stand-alone configuration source
    }

    /**
     * A special constructor for creating a ConfigurationAnchorV2 from bytes instead of a file on the filesystem.
     * <b>Does not set <code>confFileChecker</code>. This constructor is used e.g. for creating a preview of an
     * anchor.</b> {@link ConfigurationAnchorV2#ConfigurationAnchorV2(String)} should usually be preferred!
     * @param fileBytes the configuration anchor file bytes
     */
    public ConfigurationAnchorV2(byte[] fileBytes) {
        super(fileBytes, PrivateParametersSchemaValidatorV2.class);
    }

    /**
     * @return the generated at date
     */
    public Date getGeneratedAt() {
        XMLGregorianCalendar generatedAt = confType.getGeneratedAt();
        if (generatedAt == null) {
            return null;
        }

        return generatedAt.toGregorianCalendar().getTime();
    }

    @Override
    public String getInstanceIdentifier() {
        return confType.getInstanceIdentifier();
    }

    @Override
    public List<ConfigurationLocation> getLocations() {
        return confType.getSource().stream().map(l -> new ConfigurationLocation(this,
            ConfigurationUtils.generateConfigurationLocation(l.getDownloadURL(), 2), l.getVerificationCert()))
            .collect(Collectors.toList());
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ConfigurationSource)) {
            return false;
        }

        ConfigurationSource that = (ConfigurationSource) obj;
        if (!getInstanceIdentifier().equals(that.getInstanceIdentifier())) {
            return false;
        }

        List<ConfigurationLocation> thisLocations = this.getLocations();
        List<ConfigurationLocation> thatLocations = that.getLocations();
        if (thisLocations.size() != thatLocations.size()) {
            return false;
        }

        for (int i = 0; i < thisLocations.size(); i++) {
            if (!thisLocations.get(i).equals(thatLocations.get(i))) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
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
