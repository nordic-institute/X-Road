package org.niis.xroad.cs.admin.globalconf.generator;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.cs.admin.api.domain.AnchorUrl;
import org.niis.xroad.cs.admin.api.domain.AnchorUrlCert;
import org.niis.xroad.cs.admin.api.domain.TrustedAnchor;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.api.service.TrustedAnchorService;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static org.niis.xroad.cs.admin.api.service.SystemParameterService.INSTANCE_IDENTIFIER;

@Component
@RequiredArgsConstructor
class PrivateParametersLoader {
    private final SystemParameterService systemParameterService;
    private final TrustedAnchorService trustedAnchorService;


    PrivateParameters load() {
        var privateParameters = new PrivateParameters();
        privateParameters.setInstanceIdentifier(systemParameterService.getParameterValue(INSTANCE_IDENTIFIER, null));

        var configurationAnchors = trustedAnchorService.findAll().stream()
                .map(this::toConfigurationAnchor)
                .collect(toList());
        privateParameters.setConfigurationAnchors(configurationAnchors);
        
        privateParameters.setManagementService(getManagementService());
        return privateParameters;
    }

    private PrivateParameters.ManagementService getManagementService() {
        var managementService = new PrivateParameters.ManagementService();
        // managementService.setAuthCertRegServiceAddress(...);
        return managementService;
    }

    private PrivateParameters.ConfigurationAnchor toConfigurationAnchor(TrustedAnchor trustedAnchor) {
        var configurationAnchor = new PrivateParameters.ConfigurationAnchor();
        configurationAnchor.setInstanceIdentifier(trustedAnchor.getInstanceIdentifier());
        configurationAnchor.setGeneratedAt(trustedAnchor.getGeneratedAt());
        configurationAnchor.setSources(trustedAnchor.getAnchorUrls().stream()
                .map(this::toCounfigurationSource)
                .collect(toList()));
        return configurationAnchor;
    }

    private PrivateParameters.ConfigurationSource toCounfigurationSource(AnchorUrl anchorUrl) {
        var configurationSource = new PrivateParameters.ConfigurationSource();
        configurationSource.setDownloadURL(anchorUrl.getUrl());
        configurationSource.setVerificationCerts(
                anchorUrl.getAnchorUrlCerts().stream()
                        .map(AnchorUrlCert::getCert)
                        .collect(toList()));
        return configurationSource;
    }

    private Optional<String> getAuthCertRegUrl() {
        return null; // TODO
    }
}
