package ee.cyber.xroad.mediator.util;

import ch.qos.logback.core.PropertyDefinerBase;

import ee.cyber.xroad.mediator.MediatorSystemProperties;

public class LogLevelPropertyDefiner extends PropertyDefinerBase {

    @Override
    public String getPropertyValue() {
        return MediatorSystemProperties.getXroadLogLevel();
    }

}
