package ee.cyber.sdsb.common.conf.serverconf.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WsdlType {

    private Long id;

    private ClientType client;

    private String url;

    private String wsdlLocation;

    private boolean disabled;

    private String disabledNotice;

    private boolean publish;

    private Date publishedDate;

    private Date refreshedDate;

    private final List<ServiceType> service = new ArrayList<>();

    private String backend;

    private String backendURL;
}
