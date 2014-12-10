package ee.cyber.sdsb.common.conf.globalconf;

import lombok.Data;

import ee.cyber.sdsb.common.identifier.GlobalGroupId;

@Data
public final class GlobalGroupInfo {

    private final GlobalGroupId id;
    private final String description;
}
