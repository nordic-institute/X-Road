package ee.cyber.sdsb.common.conf.globalconf;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

import ee.cyber.sdsb.common.conf.AbstractXmlConf;
import ee.cyber.sdsb.common.conf.globalconf.privateparameters.ManagementServiceType;
import ee.cyber.sdsb.common.conf.globalconf.privateparameters.ObjectFactory;
import ee.cyber.sdsb.common.conf.globalconf.privateparameters.PrivateParametersType;

/**
 * Contains private parameters of a configuration instance.
 */
public class PrivateParameters extends AbstractXmlConf<PrivateParametersType> {

    /**
     * The content identifier for the private parameters.
     */
    public static final String CONTENT_ID_PRIVATE_PARAMETERS =
            "PRIVATE-PARAMETERS";

    /**
     * The default file name of private parameters.
     */
    public static final String FILE_NAME_PRIVATE_PARAMETERS =
            "private-params.xml";

    PrivateParameters() {
        super(ObjectFactory.class, PrivateParametersSchemaValidator.class);
    }

    String getInstanceIdentifier() {
        return confType.getInstanceIdentifier();
    }

    List<ConfigurationSource> getConfigurationSource() {
        return confType.getConfigurationAnchor().stream()
                .map(ConfigurationAnchor::new)
                .collect(Collectors.toList());
    }

    BigInteger getTimeStampingIntervalSeconds() {
        return confType.getTimeStampingIntervalSeconds();
    }

    ManagementServiceType getManagementService() {
        return confType.getManagementService();
    }
}
