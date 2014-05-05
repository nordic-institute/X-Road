package ee.cyber.sdsb.common.identifier;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlJavaTypeAdapter(IdentifierTypeConverter.ServiceIdAdapter.class)
public final class ServiceId extends AbstractServiceId {

    private final String memberClass;
    private final String memberCode;
    private final String serviceVersion;
    private final String subsystemCode;

    private ServiceId(String sdsbInstance,
            String memberClass, String memberCode, String subsystemCode,
            String serviceCode) {
        this(sdsbInstance, memberClass, memberCode, subsystemCode, serviceCode,
                null);
    }

    private ServiceId(String sdsbInstance,
            String memberClass, String memberCode, String subsystemCode,
            String serviceCode, String serviceVersion) {
        super(SdsbObjectType.SERVICE, sdsbInstance, serviceCode);

        this.memberClass = memberClass;
        this.memberCode = memberCode;
        this.serviceVersion = serviceVersion;
        this.subsystemCode = subsystemCode;
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
        return new ServiceId(sdsbInstance, memberClass, memberCode,
                subsystemCode, serviceCode, serviceVersion);
    }
}
