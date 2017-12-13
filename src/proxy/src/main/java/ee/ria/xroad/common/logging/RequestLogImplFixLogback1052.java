package ee.ria.xroad.common.logging;

import ch.qos.logback.access.jetty.RequestLogImpl;
import org.eclipse.jetty.util.component.LifeCycle;

/**
 * This class is a temporary fix for Logback access logs problem
 * See https://github.com/eclipse/jetty.project/issues/509
 * See https://github.com/qos-ch/logback/pull/269
 */
public class RequestLogImplFixLogback1052 extends RequestLogImpl implements LifeCycle {
}
