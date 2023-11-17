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
package ee.ria.xroad.opmonitordaemon;

import ee.ria.xroad.common.db.DatabaseCtx;
import ee.ria.xroad.common.db.TransactionCallback;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.EmptyInterceptor;
import org.hibernate.type.Type;

import java.io.Serializable;

/**
 * Database context for the operational monitoring daemon
 */
final class OpMonitorDaemonDatabaseCtx {

    private static final DatabaseCtx CTX = new DatabaseCtx("op-monitor",
            new StringValueTruncator());

    private OpMonitorDaemonDatabaseCtx() {
    }

    /**
     * @return the database context instance
     */
    static DatabaseCtx get() {
        return CTX;
    }

    /**
     * Convenience method for executing a database operation in a transaction.
     * @param <T> the type of result
     * @param callback the callback
     * @return the result
     * @throws Exception if an error occurs
     */
    static <T> T doInTransaction(TransactionCallback<T> callback)
            throws Exception {
        return CTX.doInTransaction(callback);
    }

    private static class StringValueTruncator extends EmptyInterceptor {
        private static final long serialVersionUID = 1L;

        private static final String SOAP_FAULT_STRING = "faultString";

        private static final int FAULT_MAX_LENGTH = 2048;
        private static final int MAX_LENGTH = 255;

        @Override
        public boolean onFlushDirty(Object entity, Serializable id,
                Object[] currentState, Object[] previousState,
                String[] propertyNames, Type[] types) {
            if (entity instanceof OperationalDataRecord) {
                truncateStringProperties(currentState, propertyNames, types);
                return true;
            }

            return false;
        }

        @Override
        public boolean onSave(Object entity, Serializable id, Object[] state,
                String[] propertyNames, Type[] types) {
            if (entity instanceof OperationalDataRecord) {
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
