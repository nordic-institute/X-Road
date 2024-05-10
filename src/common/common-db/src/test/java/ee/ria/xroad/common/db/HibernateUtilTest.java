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
package ee.ria.xroad.common.db;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;

import org.hibernate.HibernateException;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HibernateUtilTest {
    private static final String TEST_SESSION_FACTORY_NAME = "testSessionFactory";

    @Mock
    private Configuration configuration;

    @BeforeEach
    public void setUp() {
        when(configuration.configure()).thenReturn(configuration);
        when(configuration.configure(anyString())).thenReturn(configuration);
    }

    @Test
    void getSessionFactoryHideErrorDetails() {

        try (
                MockedStatic<HibernateUtil> db = Mockito.mockStatic(HibernateUtil.class, CALLS_REAL_METHODS);
                MockedStatic<SystemProperties> system = Mockito.mockStatic(SystemProperties.class)
        ) {
            db.when(HibernateUtil::createEmptyConfiguration).thenReturn(configuration);
            system.when(SystemProperties::getDatabasePropertiesFile)
                    .thenReturn(HibernateUtilTest.class.getResource("/empty_db.properties").getFile());
            when(configuration.buildSessionFactory())
                    .thenThrow(new HibernateException("username and ip address"));
            HibernateUtil.getSessionFactory(TEST_SESSION_FACTORY_NAME);

            fail("Should have thrown an exception");
        } catch (Exception e) {
            assertThat(e)
                    .isInstanceOf(CodedException.class)
                    .hasMessageContaining("DatabaseError: Error accessing database (testSessionFactory)");
        }


    }
}
