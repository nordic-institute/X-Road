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
package org.niis.xroad.common.core.node;

import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.dbsource.InvalidNodeNameException;
import org.niis.xroad.common.properties.dbsource.NodeName;

/**
 * Identity of one cluster node. An instance always holds a valid node name, so code that receives one needs
 * no further validation. The rule is {@link NodeName}; this record reports its violations as
 * {@link XrdRuntimeException}.
 *
 * @param name       the validated node name
 * @param configured whether the operator set the name; {@code false} means {@code name} is the default
 */
public record NodeIdentity(String name, boolean configured) {

    public NodeIdentity {
        try {
            new NodeName(name, configured);
        } catch (InvalidNodeNameException e) {
            throw invalid(e);
        }
    }

    /**
     * The node identity for a configured value.
     *
     * @param configuredValue the raw configured value; {@code null} or blank when not configured
     * @return the node identity, {@link NodeName#DEFAULT_NODE_NAME} with {@code configured = false} when blank
     * @throws XrdRuntimeException if the value breaks the node name rule; the value is never rewritten
     */
    public static NodeIdentity resolve(String configuredValue) {
        var configured = configuredValue != null && !configuredValue.isBlank();
        return new NodeIdentity(configured ? configuredValue : NodeName.DEFAULT_NODE_NAME, configured);
    }

    private static XrdRuntimeException invalid(InvalidNodeNameException e) {
        return XrdRuntimeException.systemException(ErrorCode.INVALID_PROPERTY_VALUE, e, "%s", e.getMessage());
    }
}
