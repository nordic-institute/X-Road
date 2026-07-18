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

package org.niis.xroad.common.properties.config;

/**
 * UI grouping a declared property belongs to — the panel/section the system-parameters view lists it under.
 * Purely registry metadata for display; it is not persisted ({@code configuration_properties} is keyed by
 * {@code property_key} alone). {@link #COMMON} groups shared keys not owned by a single service.
 */
public enum Category {

    COMMON("Common"),
    PROXY("Proxy"),
    SIGNER("Signer"),
    SOFTTOKEN_SIGNER("Soft token signer"),
    PROXY_UI_API("Admin UI"),
    OP_MONITOR_DAEMON("Operational monitoring"),
    MONITOR("Environmental monitoring"),
    CONFIGURATION_CLIENT("Configuration client"),
    AUXILIARY_SERVICE("Auxiliary service"),
    MESSAGE_LOG_ARCHIVER("Message log archiver"),
    ADMIN_SERVICE("Admin service"),
    MANAGEMENT_SERVICE("Management service"),
    REGISTRATION_SERVICE("Registration service"),
    CONFIGURATION_PROXY("Configuration proxy");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    /** @return human-readable label for the system-parameters UI grouping */
    public String label() {
        return label;
    }
}
