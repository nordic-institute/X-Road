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

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.util.List;
import java.util.stream.Stream;

@Slf4j

/**
 * Enumerates the management WSDL service codes hosted by the federation's management subsystem.
 * The list mirrors {@code org.niis.xroad.common.managementrequest.model.ManagementRequestType}
 * but is duplicated here to avoid pulling the management-request module (and its Spring Web
 * transitive dependencies) into the control-plane catalog.
 *
 * <p>Owner-only catalog entries are emitted only when the management subsystem (as resolved by
 * {@link GlobalConfProvider#getManagementRequestService()}) is locally registered as a client on
 * this Security Server. Outside that case the helper yields an empty stream.
 */
final class ManagementServiceCatalog {

    static final List<String> SERVICE_CODES = List.of(
            "authCertReg",
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

    private ManagementServiceCatalog() {
    }

    static Stream<ServiceId.Conf> resolveSyntheticServices(GlobalConfProvider globalConfProvider,
                                                           ServerConfProvider serverConfProvider) {
        ClientId managementSubsystem = globalConfProvider.getManagementRequestService();
        if (managementSubsystem == null || managementSubsystem.getSubsystemCode() == null) {
            return Stream.empty();
        }
        var thisServer = serverConfProvider.getIdentifier();
        if (thisServer == null) {
            return Stream.empty();
        }
        if (!globalConfProvider.isSecurityServerClient(managementSubsystem, thisServer)) {
            return Stream.empty();
        }
        if (!serverConfProvider.getAllServices(managementSubsystem).isEmpty()) {
            return Stream.empty();
        }
        return SERVICE_CODES.stream()
                .map(code -> ServiceId.Conf.create(managementSubsystem, code));
    }
}
