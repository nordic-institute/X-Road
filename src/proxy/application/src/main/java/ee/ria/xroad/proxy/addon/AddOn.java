/*
 * The MIT License
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
package ee.ria.xroad.proxy.addon;

import com.google.common.collect.ImmutableList;
import io.grpc.BindableService;

import java.util.ArrayList;
import java.util.List;

/**
 * Interface for proxy addons
 */
public interface AddOn {

    /**
     * Initialization hook called during proxy startup
     *
     * @param bindableServiceRegistry proxy gRPC service registry
     */
    void init(BindableServiceRegistry bindableServiceRegistry);

    void shutdown();

    class BindableServiceRegistry {
        private final List<BindableService> bindableServices = new ArrayList<>();

        /**
         * Register gRPC bindable service to already present server.
         *
         * @param bindableService
         */
        public void register(BindableService bindableService) {
            bindableServices.add(bindableService);
        }

        public List<BindableService> getRegisteredServices() {
            return ImmutableList.copyOf(bindableServices);
        }
    }
}
