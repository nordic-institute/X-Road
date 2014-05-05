package ee.cyber.sdsb.common.identifier;

public abstract class AbstractServiceId extends SdsbId {

    protected final String serviceCode;

    protected AbstractServiceId(SdsbObjectType type, String sdsbInstance,
            String serviceCode) {
        super(type, sdsbInstance);

        this.serviceCode = serviceCode;
    }

    public String getServiceCode() {
        return serviceCode;
    }

}
