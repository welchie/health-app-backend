package org.weewelchie.healthapp.backendservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncPayloadDto {
    private List<BloodPressureReadingDto> readings;
    private List<WeightEntryDto> weights;
    private Instant lastSyncTime;
}
