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
package org.niis.xroad.configuration.migration;

import org.niis.xroad.common.core.node.NodeNameResolver;

import java.util.ArrayList;
import java.util.List;

/**
 * The shared {@code --node <name>} option of migration-cli. It names the cluster node that the migrated
 * node-scoped data belongs to, and defaults to the node name of a Security Server.
 *
 * @param nodeName the validated node name
 * @param remainingArgs the arguments without the option
 */
record NodeOption(String nodeName, String[] remainingArgs) {

    static final String OPTION = "--node";

    /**
     * Removes the option from the arguments and validates its value.
     *
     * @throws IllegalArgumentException if the option has no value or is given more than once
     * @throws org.niis.xroad.common.core.exception.XrdRuntimeException if the value breaks the node name rule
     */
    static NodeOption extract(String[] args) {
        String value = null;
        List<String> remaining = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            boolean separate = OPTION.equals(arg);
            if (!separate && !arg.startsWith(OPTION + "=")) {
                remaining.add(arg);
                continue;
            }
            if (value != null) {
                throw new IllegalArgumentException(OPTION + " may be given only once");
            }
            if (separate) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException(OPTION + " requires a node name");
                }
                value = args[++i];
            } else {
                value = arg.substring(OPTION.length() + 1);
            }
        }
        return new NodeOption(NodeNameResolver.resolve(value).name(), remaining.toArray(new String[0]));
    }
}
