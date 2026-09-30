package org.weewelchie.healthapp.backendservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisplayBloodPressureDto {
    private String id;
    private Instant takenAt;
    private Integer systolic;
    private Integer diastolic;
    private Integer heartRate;
    private String category;
    private String note;
}
