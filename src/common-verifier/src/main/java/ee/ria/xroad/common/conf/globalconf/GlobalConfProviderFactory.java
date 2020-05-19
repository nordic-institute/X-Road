/**
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
 package ee.ria.xroad.common.conf.globalconf;

import ee.ria.xroad.common.SystemProperties;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Constructor;

@Slf4j
class GlobalConfProviderFactory {

    public static final String GLOBALCONF_PROVIDER_CLASS =
            SystemProperties.PREFIX + "common.conf.globalconf.provider";

    private static Constructor<?> instanceConstructor;

    GlobalConfProviderFactory() throws Exception {
        try {
            String providerClassName =
                    System.getProperty(GLOBALCONF_PROVIDER_CLASS);
            if (!StringUtils.isEmpty(providerClassName)) {
                Class<?> providerClass = Class.forName(providerClassName);
                if (!GlobalConfProvider.class.isAssignableFrom(providerClass)) {
                    throw new Exception(providerClass + " does not implement "
                            + GlobalConfProvider.class);
                }

                instanceConstructor =
                        providerClass.getDeclaredConstructor(boolean.class);
                log.info("Using {} as GlobalConfProvider", providerClass);
            }
        } catch (Exception e) {
            log.error("Could not create an instance constructor"
                    + " for GlobalConfProvider", e);
        } finally {
            if (instanceConstructor == null) {
                instanceConstructor = GlobalConfImpl.class
                        .getDeclaredConstructor(boolean.class);
            }
        }
    }

    GlobalConfProvider createInstance(boolean reloadIfChanged) {
        try {
            return (GlobalConfProvider)
                    instanceConstructor.newInstance(reloadIfChanged);
        } catch (Exception e) {
            log.error("Could not create an instance of GlobalConfProvider!", e);
        }
        return null;
    }

}
