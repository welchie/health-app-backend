package org.weewelchie.healthapp.backendservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeightEntryDto {
    private String id;
    private Instant takenAt;
    private Integer grams;
    private String note;
    private Instant updatedAt;
    private Boolean deleted;
}
