package org.niis.xroad.ss.test.addons.jmx;

public interface JmxClient {
    Object getValue(String objectName, String attribute);
}
