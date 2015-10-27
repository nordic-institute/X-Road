package ee.ria.xroad.common.identifier;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Security category ID.
 */
@XmlJavaTypeAdapter(IdentifierTypeConverter.SecurityCategoryIdAdapter.class)
public final class SecurityCategoryId extends XRoadId {

    private final String securityCategory;

    SecurityCategoryId() { // required by Hibernate
        this(null, null);
    }

    private SecurityCategoryId(String xRoadInstance, String securityCategory) {
        super(XRoadObjectType.SECURITYCATEGORY, xRoadInstance);

        this.securityCategory = securityCategory;
    }

    /**
     * Returns the security category code.
     * @return String
     */
    public String getCategoryCode() {
        return securityCategory;
    }

    @Override
    protected String[] getFieldsForStringFormat() {
        return new String[] {securityCategory};
    }

    /**
     * Factory method for creating a new GlobalGroupId.
     * @param xRoadInstance instance of the new security category
     * @param securityCategory code of the new security category
     * @return SecurityCategoryId
     */
    public static SecurityCategoryId create(String xRoadInstance,
            String securityCategory) {
        validateField("xRoadInstance", xRoadInstance);
        validateField("securityCategory", securityCategory);
        return new SecurityCategoryId(xRoadInstance, securityCategory);
    }

}
