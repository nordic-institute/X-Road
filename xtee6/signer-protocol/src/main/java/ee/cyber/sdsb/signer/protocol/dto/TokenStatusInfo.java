package ee.cyber.sdsb.signer.protocol.dto;

import java.io.Serializable;

public enum TokenStatusInfo implements Serializable {

    OK, // Normal operation status
    USER_PIN_LOCKED, // Blocked
    USER_PIN_INCORRECT, // Incorrect PIN was entered
    USER_PIN_INVALID, //
    USER_PIN_EXPIRED,
    NOT_INITIALIZED

    // TODO: more statuses?
}
