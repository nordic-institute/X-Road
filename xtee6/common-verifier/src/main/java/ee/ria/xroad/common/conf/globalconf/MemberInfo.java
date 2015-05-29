package ee.ria.xroad.common.conf.globalconf;

import lombok.Data;

import ee.ria.xroad.common.identifier.ClientId;

/**
 * Value object containing client identifier and name.
 */
@Data
public final class MemberInfo {

    private final ClientId id;
    private final String name;

}
