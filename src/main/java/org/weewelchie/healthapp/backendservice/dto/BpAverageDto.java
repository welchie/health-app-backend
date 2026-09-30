package org.weewelchie.healthapp.backendservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BpAverageDto {
    private Integer systolic;
    private Integer diastolic;
    private Integer heartRate;
    private int count;
    private String category;
}
