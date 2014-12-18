package ee.cyber.sdsb.proxy.conf;

import java.util.Date;

import lombok.Getter;

import org.joda.time.DateTime;

abstract class AbstractCachedInfo {

    @Getter
    private final DateTime createdAt = new DateTime();

    abstract boolean verifyValidity(Date atDate);

}
