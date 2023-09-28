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
package ee.ria.xroad.proxy.opmonitoring;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.opmonitoring.AbstractOpMonitoringBuffer;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Contains method for storing operational monitoring data.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OpMonitoring {

    private static final String OP_MONITORING_BUFFER_IMPL_CLASS =
            SystemProperties.PREFIX + "proxy.opMonitoringBufferImpl";

    private static AbstractOpMonitoringBuffer opMonitoringBuffer;

    /**
     * Initializes the operational monitoring using the provided actor system.
     *
     * @throws Exception if initialization fails
     */
    public static void init() throws Exception {
        Class<? extends AbstractOpMonitoringBuffer> clazz = getOpMonitoringManagerImpl();

        log.trace("Using implementation class: {}", clazz);
        opMonitoringBuffer = clazz.getDeclaredConstructor().newInstance();
        opMonitoringBuffer.start();
    }

    public static void shutdown() throws Exception {
        opMonitoringBuffer.stop();
    }

    /**
     * Store the operational monitoring data.
     */
    public static void store(OpMonitoringData data) {
        log.trace("store()");

        try {
            opMonitoringBuffer.store(data);
        } catch (Throwable t) {
            log.error("Storing operational monitoring data failed", t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends AbstractOpMonitoringBuffer> getOpMonitoringManagerImpl() {
        String opMonitoringBufferImplClassName = System.getProperty(
                OP_MONITORING_BUFFER_IMPL_CLASS,
                NullOpMonitoringBuffer.class.getName());

        try {
            Class<?> clazz = Class.forName(opMonitoringBufferImplClassName);

            return (Class<? extends AbstractOpMonitoringBuffer>) clazz;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Unable to load operational monitoring buffer impl: "
                    + opMonitoringBufferImplClassName, e);
        }
    }

}
