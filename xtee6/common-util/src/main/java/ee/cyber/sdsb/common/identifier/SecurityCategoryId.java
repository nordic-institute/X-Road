package ee.cyber.sdsb.common.identifier;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlJavaTypeAdapter(IdentifierTypeConverter.SecurityCategoryIdAdapter.class)
public final class SecurityCategoryId extends SdsbId {

    private final String securityCategory;

    private SecurityCategoryId(String sdsbInstance, String securityCategory) {
        super(SdsbObjectType.SECURITYCATEGORY, sdsbInstance);

        this.securityCategory = securityCategory;
    }

    public String getCategoryCode() {
        return securityCategory;
    }

    @Override
    protected String[] getFieldsForStringFormat() {
        return new String[] { securityCategory };
    }

    /**
     * Factory method for creating a new GlobalGroupId.
     */
    public static SecurityCategoryId create(String sdsbInstance,
            String securityCategory) {
        validateField("sdsbInstance", sdsbInstance);
        validateField("securityCategory", securityCategory);
        return new SecurityCategoryId(sdsbInstance, securityCategory);
    }

}
