package ee.ria.xroad.common.metadata;

/**
 * Contains String constants for metadata request names.
 */
public final class MetadataRequests {

    private MetadataRequests() {
    }

    public static final String LIST_CLIENTS = "/listClients";
    public static final String LIST_CENTRAL_SERVICES = "/listCentralServices";
    public static final String WSDL = "/wsdl";
    public static final String LIST_METHODS = "listMethods";
    public static final String ALLOWED_METHODS = "allowedMethods";
    public static final String GET_WSDL = "getWsdl";

}
