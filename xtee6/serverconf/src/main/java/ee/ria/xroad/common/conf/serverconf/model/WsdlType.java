package ee.ria.xroad.common.conf.serverconf.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Wsdl.
 */
@Getter
@Setter
public class WsdlType {

    private final List<ServiceType> service = new ArrayList<>();

    private Long id;

    private ClientType client;

    private String url;

    private String wsdlLocation;

    private boolean disabled;

    private String disabledNotice;

    private Date refreshedDate;
}
