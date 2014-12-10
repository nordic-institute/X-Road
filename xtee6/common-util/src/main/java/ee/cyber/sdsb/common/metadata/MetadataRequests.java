package ee.cyber.sdsb.common.metadata;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MetadataRequests {

    public static final String LIST_CLIENTS = "/listClients";
    public static final String LIST_CENTRAL_SERVICES = "/listCentralServices";
    public static final String WSDL = "/wsdl";
    public static final String LIST_METHODS = "listMethods";
    public static final String ALLOWED_METHODS = "allowedMethods";
    public static final String GET_WSDL = "getWsdl";

}
