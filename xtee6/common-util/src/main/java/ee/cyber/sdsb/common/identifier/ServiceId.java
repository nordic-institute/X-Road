package ee.cyber.sdsb.common.identifier;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlJavaTypeAdapter(IdentifierTypeConverter.ServiceIdAdapter.class)
public class ServiceId extends SdsbId {

    private final String memberClass;
    private final String memberCode;
    private final String serviceVersion;
    private final String subsystemCode;
    protected final String serviceCode;

    ServiceId() { // required by Hibernate
        this(null, null, null, null, null, null);
    }

    protected ServiceId(SdsbObjectType type, String sdsbInstance,
            String memberClass, String memberCode, String subsystemCode,
            String serviceCode) {
        this(type, sdsbInstance, memberClass, memberCode, subsystemCode,
                serviceCode, null);
    }

    protected ServiceId(SdsbObjectType type, String sdsbInstance,
            String memberClass, String memberCode, String subsystemCode,
            String serviceCode, String serviceVersion) {
        super(type, sdsbInstance);

        this.memberClass = memberClass;
        this.memberCode = memberCode;
        this.serviceVersion = serviceVersion;
        this.subsystemCode = subsystemCode;
        this.serviceCode = serviceCode;
    }

    public String getMemberClass() {
        return memberClass;
    }

    public String getMemberCode() {
        return memberCode;
    }

    /**
     * Returns subsystem code, if present, or null otherwise.
     */
    public String getSubsystemCode() {
        return subsystemCode;
    }

    public String getServiceVersion() {
        return serviceVersion;
    }

    public String getServiceCode() {
        return serviceCode;
    }

    public ClientId getClientId() {
        return ClientId.create(getSdsbInstance(),
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
     */
    public static ServiceId create(ClientId client, String serviceCode) {
        return create(client.getSdsbInstance(), client.getMemberClass(),
                client.getMemberCode(), client.getSubsystemCode(), serviceCode);
    }

    /**
     * Factory method for creating a new ServiceId.
     */
    public static ServiceId create(ClientId client, String serviceCode,
            String serviceVersion) {
        return create(client.getSdsbInstance(), client.getMemberClass(),
                client.getMemberCode(), client.getSubsystemCode(), serviceCode,
                serviceVersion);
    }

    /**
     * Factory method for creating a new ServiceId.
     */
    public static ServiceId create(String sdsbInstance,
            String memberClass, String memberCode, String subsystemCode,
            String serviceCode) {
        return create(sdsbInstance, memberClass, memberCode, subsystemCode,
                serviceCode, null);
    }

    /**
     * Factory method for creating a new ServiceId.
     */
    public static ServiceId create(String sdsbInstance,
            String memberClass, String memberCode, String subsystemCode,
            String serviceCode, String serviceVersion) {
        validateField("sdsbInstance", sdsbInstance);
        validateField("memberClass", memberClass);
        validateField("memberCode", memberCode);
        validateField("serviceCode", serviceCode);
        return new ServiceId(SdsbObjectType.SERVICE, sdsbInstance, memberClass,
                memberCode, subsystemCode, serviceCode, serviceVersion);
    }
}
