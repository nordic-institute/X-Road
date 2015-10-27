package ee.ria.xroad.common.certificateprofile.impl;

import lombok.Data;

import ee.ria.xroad.common.certificateprofile.DnFieldValue;

/**
 * Default implementation of DnFieldValue.
 */
@Data
public class DnFieldValueImpl implements DnFieldValue {

    private final String id;
    private final String value;

}
