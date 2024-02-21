/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
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
package ee.ria.xroad.confproxy.commandline;

import ee.ria.xroad.common.conf.globalconf.ConfigurationAnchor;
import ee.ria.xroad.common.conf.globalconf.privateparameters.v2.ConfigurationAnchorType;
import ee.ria.xroad.common.conf.globalconf.privateparameters.v2.ConfigurationSourceType;
import ee.ria.xroad.common.conf.globalconf.privateparameters.v2.ObjectFactory;
import ee.ria.xroad.common.util.AtomicSave;
import ee.ria.xroad.confproxy.ConfProxyProperties;
import ee.ria.xroad.confproxy.util.OutputBuilder;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.Marshaller;
import org.apache.commons.cli.CommandLine;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import java.io.OutputStream;
import java.nio.file.AccessDeniedException;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * Utility tool for creating an anchor file that is used for downloading
 * the generated global configuration.
 */
public class ConfProxyUtilGenerateAnchor extends ConfProxyUtil {

    /**
     * Constructs a confproxy-generate-anchor utility program instance.
     */
    ConfProxyUtilGenerateAnchor() {
        super("confproxy-generate-anchor");
        getOptions()
                .addOption(PROXY_INSTANCE)
                .addOption("f", "filename", true,
                        "Filename of the generated anchor");
    }

    @Override
    final void execute(final CommandLine commandLine) throws Exception {
        ensureProxyExists(commandLine);
        final ConfProxyProperties conf = loadConf(commandLine);

        ConfigurationAnchor sourceAnchor = null;
        try {
            sourceAnchor = new ConfigurationAnchor(conf.getProxyAnchorPath());
        } catch (Exception ex) {
            fail("Could not load source anchor: ", ex);
        }
        String instance = sourceAnchor.getInstanceIdentifier();

        if (conf.getConfigurationProxyURLs().isEmpty()) {
            fail("configuration-proxy.address has not been"
                    + " configured in 'local.ini'!", null);
        }

        if (conf.getKeyList().isEmpty()) {
            fail("No signing keys configured!", null);
        }

        if (commandLine.hasOption("filename")) {
            String filename = commandLine.getOptionValue("f");

            try {
                AtomicSave.execute(filename, "tmpanchor",
                        out -> generateAnchorXml(conf, instance, out));
            } catch (AccessDeniedException ex) {
                fail("Cannot write anchor to '" + filename
                        + "', permission denied. ", ex);
            }
            System.out.println("Generated anchor xml to '" + filename + "'");
        } else {
            printHelp();
        }
    }

    /**
     * Generates an achor xml file based on the provided proxy configuration
     * properties and writes it to the provided output stream.
     * @param conf configuration proxy properties instance
     * @param instanceIdentifier instance identifier of the resulting anchor
     * @param out the output stream for writing the generated xml
     * @throws Exception if xml generation fails
     */
    private void generateAnchorXml(final ConfProxyProperties conf,
                                   final String instanceIdentifier,
                                   final OutputStream out) throws Exception {
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

    private void addSources(ConfProxyProperties conf, ObjectFactory factory, ConfigurationAnchorType anchorType) throws Exception {
        for (String url : conf.getConfigurationProxyURLs()) {
            ConfigurationSourceType sourceType = factory.createConfigurationSourceType();
            sourceType.setDownloadURL(url + "/" + OutputBuilder.SIGNED_DIRECTORY_NAME);
            for (byte[] cert : conf.getVerificationCerts()) {
                sourceType.getVerificationCert().add(cert);
            }
            anchorType.getSource().add(sourceType);
        }
    }
}
