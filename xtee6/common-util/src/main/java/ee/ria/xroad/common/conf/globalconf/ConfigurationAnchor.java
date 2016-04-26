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
package ee.ria.xroad.common.conf.globalconf;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.commons.lang.builder.HashCodeBuilder;

import ee.ria.xroad.common.conf.AbstractXmlConf;
import ee.ria.xroad.common.conf.globalconf.privateparameters.ConfigurationAnchorType;
import ee.ria.xroad.common.conf.globalconf.privateparameters.ObjectFactory;

/**
 * Configuration anchor specifies the configuration source where a security
 * server can download global configuration.
 */
public class ConfigurationAnchor
        extends AbstractXmlConf<ConfigurationAnchorType>
        implements ConfigurationSource {

    ConfigurationAnchor(ConfigurationAnchorType t) {
        confType = t;
    }

    /**
     * @param fileName the configuration anchor file name
     */
    public ConfigurationAnchor(String fileName) {
        super(ObjectFactory.class, fileName,
                PrivateParametersSchemaValidator.class); // also applies to stand-alone configuration source
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
        return confType.getSource().stream()
                .map(l -> new ConfigurationLocation(this, l.getDownloadURL(),
                        l.getVerificationCert()))
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
}
