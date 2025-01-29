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
package org.niis.xroad.opmonitor.core;

import ee.ria.xroad.common.db.DatabaseCtxV2;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.Interceptor;
import org.hibernate.type.Type;
import org.niis.xroad.opmonitor.core.entity.OperationalDataRecordEntity;

import java.util.Map;

/**
 * Database context for the operational monitoring daemon
 */
public final class OpMonitorDaemonDatabaseCtx {

    public static DatabaseCtxV2 create(Map<String, String> hibernateProperties) {
        return new DatabaseCtxV2("op-monitor", hibernateProperties, new StringValueTruncator());
    }
    private OpMonitorDaemonDatabaseCtx() {
    }

    private static final class StringValueTruncator implements Interceptor {

        private static final String SOAP_FAULT_STRING = "faultString";

        private static final int FAULT_MAX_LENGTH = 2048;
        private static final int MAX_LENGTH = 255;

        @Override
        public boolean onFlushDirty(Object entity, Object id,
                                    Object[] currentState, Object[] previousState,
                                    String[] propertyNames, Type[] types) {
            if (entity instanceof OperationalDataRecordEntity) {
                truncateStringProperties(currentState, propertyNames, types);
                return true;
            }

            return false;
        }

        @Override
        public boolean onSave(Object entity, Object id, Object[] state,
                              String[] propertyNames, Type[] types) {
            if (entity instanceof OperationalDataRecordEntity) {
                truncateStringProperties(state, propertyNames, types);
                return true;
            }

            return false;
        }

        private static void truncateStringProperties(Object[] state,
                                                     String[] propertyNames, Type[] types) {
            for (int i = 0; i < types.length; i++) {
                if (types[i].getReturnedClass() == String.class) {
                    int maxLength = MAX_LENGTH;
                    if (propertyNames[i].equals(SOAP_FAULT_STRING)) {
                        maxLength = FAULT_MAX_LENGTH;
                    }

                    state[i] = StringUtils.substring((String) state[i], 0,
                            maxLength);
                }
            }
        }

    }
}
