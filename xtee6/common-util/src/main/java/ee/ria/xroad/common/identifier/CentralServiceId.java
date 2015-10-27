package ee.ria.xroad.common.identifier;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Central service ID.
 */
@XmlJavaTypeAdapter(IdentifierTypeConverter.CentralServiceIdAdapter.class)
public final class CentralServiceId extends ServiceId {

    CentralServiceId() { // required by Hibernate
        this(null, null);
    }

    private CentralServiceId(String xRoadInstance, String serviceCode) {
        super(XRoadObjectType.CENTRALSERVICE, xRoadInstance, null, null,
                null, serviceCode);
    }

    @Override
    protected String[] getFieldsForStringFormat() {
        return new String[] {serviceCode};
    }

    /**
     * Factory method for creating a new CentralServiceId.
     * @param xRoadInstance instance of the new service
     * @param serviceCode code if the new service
     * @return CentralServiceId
     */
    public static CentralServiceId create(String xRoadInstance,
            String serviceCode) {
        validateField("xRoadInstance", xRoadInstance);
        validateField("serviceCode", serviceCode);
        return new CentralServiceId(xRoadInstance, serviceCode);
    }

}
