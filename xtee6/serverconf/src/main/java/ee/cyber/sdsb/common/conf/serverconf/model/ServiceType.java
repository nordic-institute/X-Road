package ee.cyber.sdsb.common.conf.serverconf.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

import ee.cyber.sdsb.common.identifier.SecurityCategoryId;

@Getter
@Setter
public class ServiceType {

    private Long id;

    private WsdlType wsdl;

    private String serviceCode;

    private String serviceVersion;

    private String title;

    private String url;

    private Boolean sslAuthentication;

    private int timeout;

    private final List<SecurityCategoryId> requiredSecurityCategory =
            new ArrayList<>();

}
