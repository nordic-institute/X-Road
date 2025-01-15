package ee.ria.xroad.common.conf.globalconfextension;

import ee.ria.xroad.common.conf.AbstractXmlConf;
import ee.ria.xroad.common.conf.globalconf.GlobalConfSource;

public interface GlobalConfExtensionFactory {

    <T extends AbstractXmlConf<?>> T createExtension(GlobalConfSource globalConfSource, String extensionFileName, Class<T> extensionClass);
}
