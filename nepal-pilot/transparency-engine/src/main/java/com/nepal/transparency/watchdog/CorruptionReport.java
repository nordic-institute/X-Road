package com.nepal.transparency.watchdog;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reports")
@Data
public class CorruptionReport {

    @Id
    private String reportId;

    private LocalDateTime timestamp;

    @Embedded
    private GeoLocation location;

    private String projectTid; // Corresponds to Budget TrackingID

    @Embedded
    private Evidence evidence;

    private String whistleblowerHash;

    private String userType; // ANONYMOUS, CITIZEN, RESIDENT, ENGINEER

    // Consensus fields
    private int reputationPoints;
    private boolean verified;

    @PrePersist
    public void generateId() {
        if (this.reportId == null) {
            this.reportId = "UUID-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
    }
}
