package ee.cyber.sdsb.common.conf.globalconf;

import lombok.Data;

import ee.cyber.sdsb.common.identifier.ClientId;

@Data
public final class MemberInfo {

    private final ClientId id;
    private final String name;

}
