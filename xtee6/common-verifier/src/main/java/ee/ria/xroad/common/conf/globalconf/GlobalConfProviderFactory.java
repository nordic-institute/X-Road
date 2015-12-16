package ee.ria.xroad.common.conf.globalconf;

import java.lang.reflect.Constructor;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import ee.ria.xroad.common.SystemProperties;

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
