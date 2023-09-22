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
        try (final JMXConnector connector = JMXConnectorFactory.connect(new JMXServiceURL(jmxServiceUrl.get()))) {
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
