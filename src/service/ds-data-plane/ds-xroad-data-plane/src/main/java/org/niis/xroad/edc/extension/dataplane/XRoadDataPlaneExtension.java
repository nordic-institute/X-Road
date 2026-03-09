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

package org.niis.xroad.edc.extension.dataplane;

import org.eclipse.dataplane.Dataplane;
import org.eclipse.dataplane.domain.Result;
import org.eclipse.dataplane.domain.dataflow.DataFlow;
import org.eclipse.dataplane.logic.OnPrepare;
import org.eclipse.dataplane.logic.OnStart;
import org.eclipse.dataplane.logic.OnStarted;
import org.eclipse.dataplane.logic.OnTerminate;
import org.eclipse.edc.runtime.metamodel.annotation.Configuration;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.runtime.metamodel.annotation.Settings;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebService;

import static org.niis.xroad.edc.extension.dataplane.XRoadDataPlaneExtension.NAME;

@Extension(NAME)
public class XRoadDataPlaneExtension implements ServiceExtension {
    static final String NAME = "X-Road Sample data plane";

    @Setting(key = "edc.controlplane.control.url")
    private String controlplaneEndpoint;

    @Configuration
    private ApiConfiguration apiConfiguration;

    @Inject
    private WebService webService;

    @Inject
    private Monitor monitor;

    private Dataplane dataplane;

    @Override
    public void initialize(ServiceExtensionContext context) {
        monitor.info("Initializing X-Road Sample data plane.");
        dataplane = Dataplane.newInstance()
                .endpoint(apiConfiguration.dataFlowEndpoint()) // endpoint for signaling
                .transferType("Xrd-PULL")
                .onPrepare(new DataplaneOnPrepare())
                .onStart(new DataplaneOnStart())
                .onStarted(new DataplaneOnStarted())
                .onCompleted(Result::success)
                .onTerminate(new DataplaneOnTerminate())
                .build();

//        webService.registerResource(ApiContext.CONTROL, dataplane.controller());
        webService.registerResource(dataplane.controller());
    }

    @Override
    public void start() {
        monitor.info("Registering X-Road Sample data plane.");
        dataplane.registerOn(controlplaneEndpoint)
                .orElseThrow(e -> new EdcException("Cannot register dataplane on controlplane", e));
    }

    private static final class DataplaneOnPrepare implements OnPrepare {
        @Override
        public Result<DataFlow> action(DataFlow dataFlow) {
            return Result.success(dataFlow);
        }
    }

    private static final class DataplaneOnStart implements OnStart {
        @Override
        public Result<DataFlow> action(DataFlow dataFlow) {
            return Result.success(dataFlow);
        }
    }

    private static final class DataplaneOnStarted implements OnStarted {
        @Override
        public Result<DataFlow> action(DataFlow dataFlow) {
            return Result.success(dataFlow);
        }
    }

    private static final class DataplaneOnTerminate implements OnTerminate {
        @Override
        public Result<DataFlow> action(DataFlow dataFlow) {
            return Result.success(dataFlow);
        }
    }

    @Settings
    public record ApiConfiguration(
            @Setting(key = "edc.hostname") String hostname
    ) {
        public String dataFlowEndpoint() {
            return "http://%s:8181/api/v1/dataflows".formatted(hostname);
        }
    }
}
