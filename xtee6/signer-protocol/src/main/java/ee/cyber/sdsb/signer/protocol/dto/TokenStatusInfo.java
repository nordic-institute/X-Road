package ee.cyber.sdsb.signer.protocol.dto;

import java.io.Serializable;

/**
 * Token status info DTO.
 */
public enum TokenStatusInfo implements Serializable {

    OK, // Normal operation status
    USER_PIN_LOCKED, // Blocked
    USER_PIN_INCORRECT, // Incorrect PIN was entered
    USER_PIN_INVALID, // Invalid PIN
    USER_PIN_EXPIRED, // PIN expired
    USER_PIN_COUNT_LOW, // Only a few tries left
    USER_PIN_FINAL_TRY, // Final try
    NOT_INITIALIZED

}
