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
package org.niis.xroad.edc.extension.catalog;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import jakarta.annotation.Nullable;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Synthetic catalog entries for management WSDL services hosted by the federation's
 * management subsystem. The service code list mirrors
 * {@code org.niis.xroad.common.managementrequest.model.ManagementRequestType} — duplicated
 * here to avoid pulling the management-request module's Spring Web transitive deps into
 * the control-plane catalog.
 */
@Slf4j
@UtilityClass
class ManagementServiceCatalog {

    private static final String AUTH_CERT_REG = "authCertReg";

    static final List<String> SERVICE_CODES = List.of(
            AUTH_CERT_REG,
            "clientReg",
            "ownerChange",
            "clientDeletion",
            "authCertDeletion",
            "addressChange",
            "clientDisable",
            "clientEnable",
            "clientRename",
            "maintenanceModeEnable",
            "maintenanceModeDisable"
    );

    /**
     * {@link #SERVICE_CODES} minus {@code authCertReg}: it is never negotiated over the
     * dataspace protocol (it goes as direct HTTPS to the Central Server), so the SYSTEM
     * publication excludes it while the {@code -mgmt} mirror keeps its full list.
     */
    static final Set<String> SYSTEM_SERVICE_CODES = SERVICE_CODES.stream()
            .filter(code -> !AUTH_CERT_REG.equals(code))
            .collect(Collectors.toUnmodifiableSet());

    static Stream<ServiceId.Conf> resolveSyntheticServices(GlobalConfProvider globalConfProvider,
                                                           ServerConfProvider serverConfProvider) {
        var managementSubsystem = resolveManagementSubsystem(globalConfProvider, serverConfProvider);
        if (managementSubsystem == null) {
            return Stream.empty();
        }
        return SERVICE_CODES.stream()
                .map(code -> ServiceId.Conf.create(managementSubsystem, code));
    }

    /**
     * Same gate as {@link #resolveSyntheticServices}, filtered to the SYSTEM-eligible codes.
     */
    static Stream<ServiceId.Conf> resolveSystemSyntheticServices(GlobalConfProvider globalConfProvider,
                                                                  ServerConfProvider serverConfProvider) {
        var managementSubsystem = resolveManagementSubsystem(globalConfProvider, serverConfProvider);
        if (managementSubsystem == null) {
            return Stream.empty();
        }
        return SYSTEM_SERVICE_CODES.stream()
                .map(code -> ServiceId.Conf.create(managementSubsystem, code));
    }

    /**
     * Whether {@code serviceId} is one of the SYSTEM-eligible management-request synthetic
     * services on this server: owned by the locally hosted management subsystem, and not
     * {@code authCertReg}.
     */
    static boolean isSystemEligible(ServiceId serviceId, GlobalConfProvider globalConfProvider,
                                     ServerConfProvider serverConfProvider) {
        var managementSubsystem = resolveManagementSubsystem(globalConfProvider, serverConfProvider);
        return managementSubsystem != null
                && managementSubsystem.equals(serviceId.getClientId())
                && SYSTEM_SERVICE_CODES.contains(serviceId.getServiceCode());
    }

    @Nullable
    private static ClientId resolveManagementSubsystem(GlobalConfProvider globalConfProvider,
                                                         ServerConfProvider serverConfProvider) {
        ClientId managementSubsystem = globalConfProvider.getManagementRequestService();
        if (managementSubsystem == null || managementSubsystem.getSubsystemCode() == null) {
            return null;
        }
        var thisServer = serverConfProvider.getIdentifier();
        if (thisServer == null) {
            return null;
        }
        if (!globalConfProvider.isSecurityServerClient(managementSubsystem, thisServer)) {
            return null;
        }
        if (!serverConfProvider.getAllServices(managementSubsystem).isEmpty()) {
            return null;
        }
        return managementSubsystem;
    }
}
