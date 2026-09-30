package org.weewelchie.healthapp.backendservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisplaySummaryDto {
    private int totalReadings;
    private int totalWeights;
    private DisplayBloodPressureDto latestReading;
    private DisplayWeightDto latestWeight;
    private Map<String, BpAverageDto> averages;
    private WeightChangeDto weightChange30d;
}
