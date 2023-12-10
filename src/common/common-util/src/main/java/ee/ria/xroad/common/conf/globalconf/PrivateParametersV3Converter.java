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

import ee.ria.xroad.common.conf.globalconf.privateparameters.v3.ConfigurationAnchorType;
import ee.ria.xroad.common.conf.globalconf.privateparameters.v3.ConfigurationSourceType;
import ee.ria.xroad.common.conf.globalconf.privateparameters.v3.PrivateParametersTypeV3;

import static java.util.stream.Collectors.toList;

public class PrivateParametersV3Converter {

    PrivateParameters convert(PrivateParametersTypeV3 source) {
        var target = new PrivateParameters();
        target.setInstanceIdentifier(source.getInstanceIdentifier());
        target.setTimeStampingIntervalSeconds(source.getTimeStampingIntervalSeconds());

        if (source.getManagementService() != null) {
            var managementService = new PrivateParameters.ManagementService();
            managementService.setAuthCertRegServiceAddress(source.getManagementService().getAuthCertRegServiceAddress());
            managementService.setAuthCertRegServiceCert(source.getManagementService().getAuthCertRegServiceCert());
            managementService.setManagementRequestServiceProviderId(source.getManagementService().getManagementRequestServiceProviderId());
            target.setManagementService(managementService);
        }

        if (source.getConfigurationAnchor() != null) {
            target.setConfigurationAnchors(source.getConfigurationAnchor().stream()
                    .map(this::toConfigurationAnchor)
                    .collect(toList())
            );
        }

        return target;
    }

    private PrivateParameters.ConfigurationAnchor toConfigurationAnchor(ConfigurationAnchorType source) {
        var configurationAnchor = new PrivateParameters.ConfigurationAnchor();
        configurationAnchor.setInstanceIdentifier(source.getInstanceIdentifier());
        if (source.getGeneratedAt() != null) {
            configurationAnchor.setGeneratedAt(source.getGeneratedAt().toGregorianCalendar().toInstant());
        }
        configurationAnchor.setSources(source.getSource().stream()
                .map(this::toConfigurationSource)
                .collect(toList())
        );
        return configurationAnchor;
    }

    private PrivateParameters.Source toConfigurationSource(ConfigurationSourceType source) {
        var configurationSource = new PrivateParameters.Source();
        configurationSource.setDownloadURL(source.getDownloadURL());
        configurationSource.setVerificationCerts(source.getVerificationCert());
        return configurationSource;
    }

}
