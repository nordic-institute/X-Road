package ee.cyber.sdsb.common.identifier;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlJavaTypeAdapter(IdentifierTypeConverter.CentralServiceIdAdapter.class)
public final class CentralServiceId extends ServiceId {

    CentralServiceId() { // required by Hibernate
        this(null, null);
    }

    private CentralServiceId(String sdsbInstance, String serviceCode) {
        super(SdsbObjectType.CENTRALSERVICE, sdsbInstance, null, null,
                null, serviceCode);
    }

    @Override
    protected String[] getFieldsForStringFormat() {
        return new String[] { serviceCode };
    }

    /**
     * Factory method for creating a new CentralServiceId.
     */
    public static CentralServiceId create(String sdsbInstance,
            String serviceCode) {
        validateField("sdsbInstance", sdsbInstance);
        validateField("serviceCode", serviceCode);
        return new CentralServiceId(sdsbInstance, serviceCode);
    }

}
