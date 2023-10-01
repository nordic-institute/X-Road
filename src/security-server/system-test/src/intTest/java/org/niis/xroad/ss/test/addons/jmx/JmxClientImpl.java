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
package org.niis.xroad.ss.test.addons.jmx;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import java.io.IOException;
import java.util.function.Supplier;

public class JmxClientImpl implements JmxClient {

    private final Supplier<String> jmxServiceUrl;

    public JmxClientImpl(final Supplier<String> jmxServiceUrl) {
        this.jmxServiceUrl = jmxServiceUrl;
    }

    public Object getValue(final String objectNameFragment, final String attribute) {
        try (JMXConnector connector = JMXConnectorFactory.connect(new JMXServiceURL(jmxServiceUrl.get()))) {
            connector.connect();
            final var connection = connector.getMBeanServerConnection();
            return connection.queryNames(null, null).stream()
                    .filter(name -> name.getCanonicalName().contains(objectNameFragment))
                    .findFirst()
                    .map(name -> readAttribute(connection, name, attribute))
                    .orElse(null);
        } catch (IOException e) {
            throw new RuntimeException("Failed to get JMX Object", e);
        }
    }


    private Object readAttribute(MBeanServerConnection connection, ObjectName objectName, String attribute) {
        try {
            return connection.getAttribute(objectName, attribute);
        } catch (InstanceNotFoundException | ReflectionException | IOException | MBeanException | AttributeNotFoundException e) {
            throw new RuntimeException("Failed to read JMX Object attribute", e);
        }
    }
}
