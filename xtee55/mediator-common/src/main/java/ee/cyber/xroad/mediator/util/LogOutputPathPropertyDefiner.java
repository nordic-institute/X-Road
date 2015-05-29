package ee.cyber.xroad.mediator.util;

import ch.qos.logback.core.PropertyDefinerBase;

import ee.ria.xroad.common.SystemProperties;

/**
 * Log output path property definer for the logging system.
 */
public class LogOutputPathPropertyDefiner extends PropertyDefinerBase {

    @Override
    public String getPropertyValue() {
        return SystemProperties.getLogPath();
    }

}
