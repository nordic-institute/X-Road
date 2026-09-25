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
package org.niis.xroad.common.core;

import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service codes of the synthetic management-request services hosted by the federation's management
 * subsystem. The list mirrors {@code org.niis.xroad.common.managementrequest.model.ManagementRequestType}
 * — duplicated here to avoid pulling the management-request module's Spring Web transitive deps into
 * the data space catalog or the proxy's consumer-side routing check, neither of which needs anything
 * beyond the codes. The single definition for both, so the lists cannot drift apart.
 */
@UtilityClass
public class ManagementServiceCodes {

    public static final String AUTH_CERT_REG = "authCertReg";
    public static final String CLIENT_REG = "clientReg";
    public static final String OWNER_CHANGE = "ownerChange";
    public static final String CLIENT_DELETION = "clientDeletion";
    public static final String AUTH_CERT_DELETION = "authCertDeletion";
    public static final String ADDRESS_CHANGE = "addressChange";
    public static final String CLIENT_DISABLE = "clientDisable";
    public static final String CLIENT_ENABLE = "clientEnable";
    public static final String CLIENT_RENAME = "clientRename";
    public static final String MAINTENANCE_MODE_ENABLE = "maintenanceModeEnable";
    public static final String MAINTENANCE_MODE_DISABLE = "maintenanceModeDisable";

    /** All management-request service codes, {@link #AUTH_CERT_REG} included. */
    public static final List<String> ALL = List.of(
            AUTH_CERT_REG,
            CLIENT_REG,
            OWNER_CHANGE,
            CLIENT_DELETION,
            AUTH_CERT_DELETION,
            ADDRESS_CHANGE,
            CLIENT_DISABLE,
            CLIENT_ENABLE,
            CLIENT_RENAME,
            MAINTENANCE_MODE_ENABLE,
            MAINTENANCE_MODE_DISABLE);

    /**
     * {@link #ALL} minus {@link #AUTH_CERT_REG}: it is never negotiated over the data space
     * protocol — it goes as direct HTTPS to the Central Server — so it is excluded from both the
     * SYSTEM catalog publication and the consumer-side SYSTEM-context routing check.
     */
    public static final Set<String> DSP_NEGOTIATED = ALL.stream()
            .filter(code -> !AUTH_CERT_REG.equals(code))
            .collect(Collectors.toUnmodifiableSet());
}
