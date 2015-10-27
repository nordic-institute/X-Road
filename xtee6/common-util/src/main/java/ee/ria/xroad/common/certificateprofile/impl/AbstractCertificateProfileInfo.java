package ee.ria.xroad.common.certificateprofile.impl;

import java.util.Arrays;
import java.util.stream.Collectors;

import javax.security.auth.x500.X500Principal;

import org.apache.commons.lang.StringUtils;

import ee.ria.xroad.common.certificateprofile.CertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.DnFieldDescription;
import ee.ria.xroad.common.certificateprofile.DnFieldValue;

abstract class AbstractCertificateProfileInfo
        implements CertificateProfileInfo {

    protected final DnFieldDescription[] fields;

    AbstractCertificateProfileInfo(DnFieldDescription[] fields) {
        this.fields = fields;
    }

    @Override
    public DnFieldDescription[] getSubjectFields() {
        return fields;
    }

    @Override
    public X500Principal createSubjectDn(DnFieldValue[] values) {
        return new X500Principal(
            StringUtils.join(
                Arrays.stream(values)
                    .map(this::toString)
                    .collect(Collectors.toList()),
                ", "
            )
        );
    }

    @Override
    public void validateSubjectField(DnFieldValue field) throws Exception {
        DnFieldDescription description = getDescription(field);
        if (description.isRequired() && StringUtils.isBlank(field.getValue())) {
            throw new RuntimeException(
                String.format("Field '%s (%s)' is missing value",
                    description.getLabel(),
                    description.getId()
                )
            );
        }
    }

    private String toString(DnFieldValue value) {
        return String.format("%s=%s", value.getId(), value.getValue());
    }

    private DnFieldDescription getDescription(DnFieldValue value) {
        return Arrays.stream(fields)
                .filter(f -> f.getId().equals(value.getId()))
                .findFirst()
                .orElseThrow(() ->
                    new RuntimeException("Unknown field: " + value.getId())
                );
    }
}
