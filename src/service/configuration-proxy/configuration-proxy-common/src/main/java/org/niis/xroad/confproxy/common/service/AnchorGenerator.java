/*
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.confproxy.common.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.confproxy.common.config.ConfigurationProxyProperties;
import org.niis.xroad.confproxy.common.domain.ConfProxyInstance;
import org.niis.xroad.globalconf.model.ConfigurationAnchor;
import org.niis.xroad.globalconf.schema.privateparameters.v2.ConfigurationAnchorType;
import org.niis.xroad.globalconf.schema.privateparameters.v2.ConfigurationSourceType;
import org.niis.xroad.globalconf.schema.privateparameters.v2.ObjectFactory;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import static org.niis.xroad.confproxy.common.utils.ConfProxyUtils.getConfigurationProxyURLs;

@ApplicationScoped
@RequiredArgsConstructor
public class AnchorGenerator {

    private final ConfigurationProxyProperties cpProperties;

    public byte[] generateAnchor(ConfProxyInstance instance) {
        ConfigurationAnchor sourceAnchor = null;
        try {
            sourceAnchor = new ConfigurationAnchor(instance.getProxyAnchorPath());
        } catch (Exception ex) {
            throw XrdRuntimeException.systemInternalError("Could not load source anchor: " + ex.getMessage(), ex);
        }

        var instanceIdentifier = sourceAnchor.getInstanceIdentifier();

        if (getConfigurationProxyURLs(cpProperties.address(), instance.getInstance()).isEmpty()) {
            throw XrdRuntimeException.systemInternalError("xroad.configuration-proxy.address has not been configured in 'local.yaml'!");
        }

        if (instance.getKeyList().isEmpty()) {
            throw XrdRuntimeException.systemInternalError("No signing keys configured!");
        }

        try (var baos = new ByteArrayOutputStream()) {
            generateAnchorXml(instance, instanceIdentifier, baos);
            return baos.toByteArray();
        } catch (DatatypeConfigurationException | JAXBException | IOException ex) {
            throw XrdRuntimeException.systemInternalError("Cannot generate anchor: %s".formatted(ex.getMessage()), ex);
        }
    }


    /**
     * Generates an achor xml file based on the provided proxy configuration
     * properties and writes it to the provided output stream.
     * @param conf               configuration proxy properties instance
     * @param instanceIdentifier instance identifier of the resulting anchor
     * @param out                the output stream for writing the generated xml
     */
    private void generateAnchorXml(final ConfProxyInstance conf,
                                   final String instanceIdentifier,
                                   final OutputStream out) throws JAXBException, DatatypeConfigurationException {
        JAXBContext jaxbCtx = JAXBContext.newInstance(ObjectFactory.class);
        Marshaller marshaller = jaxbCtx.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

        ObjectFactory factory = new ObjectFactory();
        ConfigurationAnchorType anchorType = factory.createConfigurationAnchorType();
        anchorType.setInstanceIdentifier(instanceIdentifier);
        GregorianCalendar gcal = new GregorianCalendar();
        gcal.setTimeZone(TimeZone.getTimeZone("UTC"));
        XMLGregorianCalendar xgcal = DatatypeFactory.newInstance()
                .newXMLGregorianCalendar(gcal);
        anchorType.setGeneratedAt(xgcal);

        addSources(conf, factory, anchorType);

        JAXBElement<ConfigurationAnchorType> root =
                factory.createConfigurationAnchor(anchorType);

        marshaller.marshal(root, out);
    }

    private void addSources(ConfProxyInstance conf, ObjectFactory factory, ConfigurationAnchorType anchorType) {
        var urls = getConfigurationProxyURLs(cpProperties.address(), conf.getInstance());
        for (String url : urls) {
            ConfigurationSourceType sourceType = factory.createConfigurationSourceType();
            sourceType.setDownloadURL(url + "/" + OutputBuilder.SIGNED_DIRECTORY_NAME);
            for (byte[] cert : conf.getVerificationCerts()) {
                sourceType.getVerificationCert().add(cert);
            }
            anchorType.getSource().add(sourceType);
        }
    }
}
