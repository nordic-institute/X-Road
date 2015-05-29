package ee.cyber.xroad.mediator.util;

import ch.qos.logback.core.PropertyDefinerBase;

import ee.cyber.xroad.mediator.MediatorSystemProperties;

/**
 * Log level property definer for the logging system.
 */
public class LogLevelPropertyDefiner extends PropertyDefinerBase {

    @Override
    public String getPropertyValue() {
        return MediatorSystemProperties.getXroadLogLevel();
    }

}
