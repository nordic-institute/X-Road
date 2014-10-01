package ee.cyber.xroad.mediator.client;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.joda.time.DateTime;

@RequiredArgsConstructor
class ActivationInfo {

    private static final int FRESHNESS_MINUTES = 60;

    private final DateTime created = new DateTime();

    @Getter
    private final boolean activated;

    boolean isExpired() {
        return new DateTime().minusMinutes(FRESHNESS_MINUTES).isAfter(created);
    }
}
