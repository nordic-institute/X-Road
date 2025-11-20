package com.nepal.transparency.watchdog;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GeoLocation {
    private double latitude;
    private double longitude;
    private String ward;
}
