package org.weewelchie.healthapp.backendservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BloodPressureReadingDto {
    private String id;
    private Instant takenAt;
    private Integer systolic;
    private Integer diastolic;
    private Integer heartRate;
    private String note;
    private Instant updatedAt;
    private Boolean deleted;
}
