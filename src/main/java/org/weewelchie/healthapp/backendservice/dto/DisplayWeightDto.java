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
public class DisplayWeightDto {
    private String id;
    private Instant takenAt;
    private Integer grams;
    private Double kg;
    private String stonePounds;
    private String note;
}
