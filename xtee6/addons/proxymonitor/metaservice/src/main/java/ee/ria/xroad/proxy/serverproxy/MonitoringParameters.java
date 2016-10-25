/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.proxy.serverproxy;

import ee.ria.xroad.common.conf.AbstractXmlConf;
import ee.ria.xroad.common.conf.globalconf.monitoringparameters.MonitoringClientType;
import ee.ria.xroad.common.conf.globalconf.monitoringparameters.MonitoringParametersType;
import ee.ria.xroad.common.conf.globalconf.monitoringparameters.ObjectFactory;

/**
 * Monitoring parameters
 */
public class MonitoringParameters extends AbstractXmlConf<MonitoringParametersType> {

    /**
     * The default file name of monitoring parameters.
     */
    public static final String FILE_NAME_MONITORING_PARAMETERS =
            "monitoring-params.xml";

    MonitoringParameters() {
        super(ObjectFactory.class, MonitoringParametersSchemaValidator.class);
    }

    MonitoringClientType getMonitoringClient() {
        return confType.getMonitoringClient();
    }

}
