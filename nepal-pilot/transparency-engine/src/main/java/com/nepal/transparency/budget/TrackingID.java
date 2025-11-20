package com.nepal.transparency.budget;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TrackingID implements Serializable {

    @Column(name = "tid_year")
    private String year;

    @Column(name = "tid_province")
    private String province;

    @Column(name = "tid_district")
    private String district;

    @Column(name = "tid_type")
    private String type;

    @Column(name = "tid_sequence")
    private String sequence;

    @Override
    public String toString() {
        return String.format("%s-%s-%s-%s-%s", year, province, district, type, sequence);
    }

    public static TrackingID fromString(String tid) {
        String[] parts = tid.split("-");
        if (parts.length != 5) {
            throw new IllegalArgumentException("Invalid Tracking ID format");
        }
        return new TrackingID(parts[0], parts[1], parts[2], parts[3], parts[4]);
    }
}
