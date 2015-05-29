package ee.ria.xroad.common.conf.globalconf;

import lombok.Data;

import ee.ria.xroad.common.identifier.GlobalGroupId;

/**
 * Value object containing global group identifier and description.
 */
@Data
public final class GlobalGroupInfo {

    private final GlobalGroupId id;
    private final String description;
}
