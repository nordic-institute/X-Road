package org.niis.xroad.ss.test.addons.jmx;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MalformedObjectNameException;
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

    public Object getValue(final String objectName, final String attribute) {
        try (final JMXConnector connector = JMXConnectorFactory.connect(new JMXServiceURL(jmxServiceUrl.get()))) {
            connector.connect();
            final var connection = connector.getMBeanServerConnection();
            return connection.getAttribute(ObjectName.getInstance(objectName), attribute);
        } catch (MBeanException | AttributeNotFoundException | InstanceNotFoundException | ReflectionException | IOException |
                 MalformedObjectNameException e) {
            throw new RuntimeException("Failed to read JMX Object attribute", e);
        }
    }
}
