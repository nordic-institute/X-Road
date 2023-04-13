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
package org.niis.xroad.cs.admin.globalconf.generator;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.cs.admin.api.domain.AnchorUrl;
import org.niis.xroad.cs.admin.api.domain.AnchorUrlCert;
import org.niis.xroad.cs.admin.api.domain.TrustedAnchor;
import org.niis.xroad.cs.admin.api.service.InternalTlsCertificateService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.api.service.TrustedAnchorService;
import org.springframework.stereotype.Component;

import static java.util.stream.Collectors.toList;

@Component
@RequiredArgsConstructor
class PrivateParametersLoader {
    private final SystemParameterService systemParameterService;
    private final TrustedAnchorService trustedAnchorService;
    private final InternalTlsCertificateService internalTlsCertificateService;

    PrivateParameters load() {
        var privateParameters = new PrivateParameters();
        privateParameters.setInstanceIdentifier(systemParameterService.getInstanceIdentifier());

        var configurationAnchors = trustedAnchorService.findAll().stream()
                .map(this::toConfigurationAnchor)
                .collect(toList());
        privateParameters.setConfigurationAnchors(configurationAnchors);
        privateParameters.setManagementService(getManagementService());
        privateParameters.setTimeStampingIntervalSeconds(systemParameterService.getTimeStampingIntervalSeconds());

        return privateParameters;
    }

    @SneakyThrows
    private PrivateParameters.ManagementService getManagementService() {
        var managementService = new PrivateParameters.ManagementService();

        String authCertRegUrl = systemParameterService.getAuthCertRegUrl();
        if (StringUtils.isBlank(authCertRegUrl)) {
            throw new ConfGeneratorException("Auth Cert Registration Service URL not configured");
        }
        managementService.setAuthCertRegServiceAddress(authCertRegUrl);

        managementService.setAuthCertRegServiceCert(internalTlsCertificateService.getInternalTlsCertificate().getEncoded());
        managementService.setManagementRequestServiceProviderId(systemParameterService.getManagementServiceProviderId());
        return managementService;
    }

    private PrivateParameters.ConfigurationAnchor toConfigurationAnchor(TrustedAnchor trustedAnchor) {
        var configurationAnchor = new PrivateParameters.ConfigurationAnchor();
        configurationAnchor.setInstanceIdentifier(trustedAnchor.getInstanceIdentifier());
        configurationAnchor.setGeneratedAt(trustedAnchor.getGeneratedAt());
        configurationAnchor.setSources(trustedAnchor.getAnchorUrls().stream()
                .map(this::toConfigurationSource)
                .collect(toList()));
        return configurationAnchor;
    }

    private PrivateParameters.ConfigurationSource toConfigurationSource(AnchorUrl anchorUrl) {
        var configurationSource = new PrivateParameters.ConfigurationSource();
        configurationSource.setDownloadURL(anchorUrl.getUrl());
        configurationSource.setVerificationCerts(
                anchorUrl.getAnchorUrlCerts().stream()
                        .map(AnchorUrlCert::getCert)
                        .collect(toList()));
        return configurationSource;
    }
}
