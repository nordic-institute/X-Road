package ee.ria.xroad.common.conf.globalconf;

import lombok.Data;

/**
 * Value object containing approved CA information.
 */
@Data
public class ApprovedCAInfo {

    private final String name;

    private final Boolean authenticationOnly;

    private final String certificateProfileInfo;
}
