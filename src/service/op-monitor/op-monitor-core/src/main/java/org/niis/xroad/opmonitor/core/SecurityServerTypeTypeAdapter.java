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
package org.niis.xroad.opmonitor.core;

import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * Type adapter for the securityServerType field used with the JsonDeserialize annotation.
 *
 * We use this type adapter to ensure an exception is thrown if the given value cannot
 * be converted to any values defined in SecurityServerType, instead of silently setting
 * the securityServerField to null.
 */
class SecurityServerTypeTypeAdapter extends StdDeserializer<String> {

    protected SecurityServerTypeTypeAdapter() {
        super(String.class);
    }

    @Override
    public String deserialize(JsonParser p,
                              DeserializationContext ctxt) {
        String value = p.getValueAsString();
        if (OpMonitoringData.SecurityServerType.fromString(value) == null) {
            throw XrdRuntimeException.systemInternalError("Invalid value of securityServerType");
        }
        return value;
    }
}
