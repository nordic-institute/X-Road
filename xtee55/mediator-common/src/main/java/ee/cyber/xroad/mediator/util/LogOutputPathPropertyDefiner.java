package ee.cyber.xroad.mediator.util;

import ch.qos.logback.core.PropertyDefinerBase;

import ee.cyber.sdsb.common.SystemProperties;

public class LogOutputPathPropertyDefiner extends PropertyDefinerBase {

    @Override
    public String getPropertyValue() {
        return SystemProperties.getLogPath();
    }

}
