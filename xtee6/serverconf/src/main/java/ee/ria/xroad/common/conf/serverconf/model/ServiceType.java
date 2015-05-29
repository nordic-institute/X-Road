package ee.ria.xroad.common.conf.serverconf.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

import ee.ria.xroad.common.identifier.SecurityCategoryId;

/**
 * Service.
 */
@Getter
@Setter
public class ServiceType {

    private final List<SecurityCategoryId> requiredSecurityCategory =
            new ArrayList<>();

    private Long id;

    private WsdlType wsdl;

    private String serviceCode;

    private String serviceVersion;

    private String title;

    private String url;

    private Boolean sslAuthentication;

    private int timeout;

}
