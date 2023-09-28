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

import ee.ria.xroad.common.SystemProperties;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static ee.ria.xroad.common.SystemProperties.AllowedFederationMode.ALL;
import static ee.ria.xroad.common.SystemProperties.AllowedFederationMode.CUSTOM;
import static ee.ria.xroad.common.SystemProperties.AllowedFederationMode.NONE;

/** Implementation of the {@link FederationConfigurationSourceFilter}.
 * Looks at {@link SystemProperties#getConfigurationClientAllowedFederations()} (a comma-separated list of allowed
 * instances).
 * <ol>
 * <li>If it contains {@link ee.ria.xroad.common.SystemProperties.AllowedFederationMode#NONE}, it will return
 * {@code false} for all {@link #shouldDownloadConfigurationFor(String)} queries.</li>
 * <li>If previous is untrue but the string contains
 * {@link ee.ria.xroad.common.SystemProperties.AllowedFederationMode#ALL}, it will return {@code true} for all
 * queries.</li>
 * <li>If previous are untrue, it will return {@code true} if the list contains the (case-insensitive) X-Road instance
 * name</li>
 * </ol>
 *
 */
@Slf4j
public class FederationConfigurationSourceFilterImpl implements FederationConfigurationSourceFilter {

    private static final String COMMA_SEPARATOR = "\\s*,\\s*";

    private final String ownInstance;

    private SystemProperties.AllowedFederationMode allowedFederationMode = null;
    private Set<String> allowedFederationPartners = null;

    FederationConfigurationSourceFilterImpl(String ownInstance) {
        this.ownInstance = ownInstance;
        String filterString = SystemProperties.getConfigurationClientAllowedFederations();
        log.info("The federation filter system property value is: '{}'", filterString);
        parseAndSetAllowedInstances(Arrays.asList(filterString.split(COMMA_SEPARATOR)));
        log.info("Allowed federation mode {} allows specific X-Road instances: {} ",
                allowedFederationMode, allowedFederationPartners);
    }

    @Override
    public boolean shouldDownloadConfigurationFor(String instanceIdentifier) {
        if (ownInstance.equalsIgnoreCase(instanceIdentifier)) {
            return true;
        }
        switch (allowedFederationMode) {
            case CUSTOM:
                return allowedFederationPartners.contains(instanceIdentifier);
            case ALL:
                return true;
            default:
                return false;
        }
    }

    private void parseAndSetAllowedInstances(List<String> initial) {
        if (initial.size() == 0) {
            log.warn("Allowed federations list was empty, is the configuration malformed?");
            allowedFederationMode = NONE;
            allowedFederationPartners = Collections.emptySet();
            return;
        }
        Set<String> allowedInstances = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String allowedInstance : initial) {
            if (NONE.name().equalsIgnoreCase(allowedInstance)) {
                allowedFederationMode = NONE;
                allowedFederationPartners = Collections.emptySet();
                return;
            } else if (ALL.name().equalsIgnoreCase(allowedInstance)) {
                allowedFederationMode = ALL;
            } else if (allowedFederationMode != ALL) {
                allowedFederationMode = CUSTOM;
                allowedInstances.add(allowedInstance);
            }
        }
        allowedFederationPartners = allowedInstances;
    }
}
