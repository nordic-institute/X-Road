
package org.niis.xroad.restapi.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Model for alert data
 */
@Data
public class AlertData {
    @JsonProperty("current_time")
    private OffsetDateTime currentTime;
    @JsonProperty("backup_restore_running_since")
    private OffsetDateTime backupRestoreRunningSince;
    @JsonProperty("global_conf_valid")
    private Boolean globalConfValid;
    @JsonProperty("soft_token_pin_entered")
    private Boolean softTokenPinEntered;
}
