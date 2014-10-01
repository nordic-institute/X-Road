package ee.cyber.sdsb.common.conf.serverconf.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

import ee.cyber.sdsb.common.identifier.ClientId;

@Getter
@Setter
public class ClientType {

    public static final String STATUS_SAVED = "saved";
    public static final String STATUS_REGINPROG = "registration in progress";
    public static final String STATUS_REGISTERED = "registered";
    public static final String STATUS_DELINPROG = "deletion in progress";
    public static final String STATUS_GLOBALERR = "global error";

    private Long id;

    private ServerConfType conf;

    private ClientId identifier;

    private String contacts;
    private String clientStatus;
    private String isAuthentication;

    private final List<WsdlType> wsdl = new ArrayList<>();
    private final List<LocalGroupType> localGroup = new ArrayList<>();
    private final List<CertificateType> isCert = new ArrayList<>();
    private final List<AclType> acl = new ArrayList<>();

}
