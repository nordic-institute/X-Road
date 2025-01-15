package ee.ria.xroad.common.conf.globalconf.globalconfextension;

import ee.ria.xroad.common.conf.AbstractXmlConf;
import ee.ria.xroad.common.conf.globalconf.GlobalConfSource;
import ee.ria.xroad.common.conf.globalconfextension.GlobalConfExtensionFactory;

public class GlobalConfExtensionFactoryImpl implements GlobalConfExtensionFactory {

    @Override
    public <T extends AbstractXmlConf<?>> T createExtension(GlobalConfSource globalConfSource,
                                                            String extensionFileName,
                                                            Class<T> extensionClass) {
        var loader = new GlobalConfExtensionLoaderImpl<T>(globalConfSource, extensionFileName, extensionClass);
        return loader.getExtension();
    }
}
