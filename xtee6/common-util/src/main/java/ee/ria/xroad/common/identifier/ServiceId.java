package ee.ria.xroad.common.identifier;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Service ID.
 */
@XmlJavaTypeAdapter(IdentifierTypeConverter.ServiceIdAdapter.class)
public class ServiceId extends XroadId {

    private final String memberClass;
    private final String memberCode;
    private final String serviceVersion;
    private final String subsystemCode;
    protected final String serviceCode;

    ServiceId() { // required by Hibernate
        this(null, null, null, null, null, null);
    }

    protected ServiceId(XroadObjectType type, String xRoadInstance,
            String memberClass, String memberCode, String subsystemCode,
            String serviceCode) {
        this(type, xRoadInstance, memberClass, memberCode, subsystemCode,
                serviceCode, null);
    }

    protected ServiceId(XroadObjectType type, String xRoadInstance,
            String memberClass, String memberCode, String subsystemCode,
            String serviceCode, String serviceVersion) {
        super(type, xRoadInstance);

        this.memberClass = memberClass;
        this.memberCode = memberCode;
        this.serviceVersion = serviceVersion;
        this.subsystemCode = subsystemCode;
        this.serviceCode = serviceCode;
    }

    /**
     * Returns the member class of the service provider.
     * @return String
     */
    public String getMemberClass() {
        return memberClass;
    }

    /**
     * Returns the member code of the service provider.
     * @return String
     */
    public String getMemberCode() {
        return memberCode;
    }

    /**
     * Returns subsystem code, if present, or null otherwise.
     * @return String or null
     */
    public String getSubsystemCode() {
        return subsystemCode;
    }

    /**
     * Returns the service version.
     * @return String
     */
    public String getServiceVersion() {
        return serviceVersion;
    }

    /**
     * Returns the service code.
     * @return String
     */
    public String getServiceCode() {
        return serviceCode;
    }

    /**
     * Returns the provider client ID of this service.
     * @return ClientId
     */
    public ClientId getClientId() {
        return ClientId.create(getXRoadInstance(),
                memberClass, memberCode, subsystemCode);
    }

    @Override
    protected String[] getFieldsForStringFormat() {
        return new String[] {
                memberClass, memberCode, subsystemCode, serviceCode,
                serviceVersion };
    }

    /**
     * Factory method for creating a new ServiceId.
     * @param client ID of the service provider
     * @param serviceCode code of the new service
     * @return ServiceId
     */
    public static ServiceId create(ClientId client, String serviceCode) {
        return create(client.getXRoadInstance(), client.getMemberClass(),
                client.getMemberCode(), client.getSubsystemCode(), serviceCode);
    }

    /**
     * Factory method for creating a new ServiceId.
     * @param client ID of the service provider
     * @param serviceCode code of the new service
     * @param serviceVersion version of the new service
     * @return ServiceId
     */
    public static ServiceId create(ClientId client, String serviceCode,
            String serviceVersion) {
        return create(client.getXRoadInstance(), client.getMemberClass(),
                client.getMemberCode(), client.getSubsystemCode(), serviceCode,
                serviceVersion);
    }

    /**
     * Factory method for creating a new ServiceId.
     * @param xRoadInstance instance of the service provider
     * @param memberClass class of the service provider
     * @param memberCode code of the service provider
     * @param subsystemCode subsystem code of the service provider
     * @param serviceCode code of the new service
     * @return ServiceId
     */
    public static ServiceId create(String xRoadInstance,
            String memberClass, String memberCode, String subsystemCode,
            String serviceCode) {
        return create(xRoadInstance, memberClass, memberCode, subsystemCode,
                serviceCode, null);
    }

    /**
     * Factory method for creating a new ServiceId.
     * @param xRoadInstance instance of the service provider
     * @param memberClass class of the service provider
     * @param memberCode code of the service provider
     * @param subsystemCode subsystem code of the service provider
     * @param serviceCode code of the new service
     * @param serviceVersion version of the new service
     * @return ServiceId
     */
    public static ServiceId create(String xRoadInstance,
            String memberClass, String memberCode, String subsystemCode,
            String serviceCode, String serviceVersion) {
        validateField("xRoadInstance", xRoadInstance);
        validateField("memberClass", memberClass);
        validateField("memberCode", memberCode);
        validateField("serviceCode", serviceCode);
        return new ServiceId(XroadObjectType.SERVICE, xRoadInstance, memberClass,
                memberCode, subsystemCode, serviceCode, serviceVersion);
    }
}
