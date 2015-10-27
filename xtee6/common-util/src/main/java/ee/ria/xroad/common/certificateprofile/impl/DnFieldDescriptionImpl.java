package ee.ria.xroad.common.certificateprofile.impl;

import lombok.Data;
import lombok.experimental.Accessors;

import ee.ria.xroad.common.certificateprofile.DnFieldDescription;

/**
 * Default implementation of DnFieldDescription.
 */
@Data
@Accessors(chain = true)
public class DnFieldDescriptionImpl implements DnFieldDescription {

    private final String id;
    private final String label;
    private final String defaultValue;
    private boolean isReadOnly;
    private boolean isRequired = true;
}
