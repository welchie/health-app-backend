package org.weewelchie.healthapp.backendservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.weewelchie.healthapp.backendservice.dto.DisplayBloodPressureDto;
import org.weewelchie.healthapp.backendservice.dto.DisplaySummaryDto;
import org.weewelchie.healthapp.backendservice.dto.DisplayWeightDto;
import org.weewelchie.healthapp.backendservice.repository.DeviceRepository;
import org.weewelchie.healthapp.backendservice.service.DisplayService;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class DisplayController {

    private final DisplayService displayService;
    private final DeviceRepository deviceRepository;

    @GetMapping("/readings")
    public ResponseEntity<List<DisplayBloodPressureDto>> getReadings(
            @RequestHeader(value = "X-Device-Token", required = false) String headerToken,
            @RequestParam(value = "token", required = false) String queryToken,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(value = "limit", required = false, defaultValue = "100") int limit) {

        UUID deviceToken = resolveDeviceToken(headerToken, queryToken);
        if (deviceToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!deviceRepository.existsById(deviceToken)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        List<DisplayBloodPressureDto> list = displayService.getReadings(deviceToken, startDate, endDate, limit);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/weights")
    public ResponseEntity<List<DisplayWeightDto>> getWeights(
            @RequestHeader(value = "X-Device-Token", required = false) String headerToken,
            @RequestParam(value = "token", required = false) String queryToken,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(value = "limit", required = false, defaultValue = "100") int limit) {

        UUID deviceToken = resolveDeviceToken(headerToken, queryToken);
        if (deviceToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!deviceRepository.existsById(deviceToken)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        List<DisplayWeightDto> list = displayService.getWeights(deviceToken, startDate, endDate, limit);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/summary")
    public ResponseEntity<DisplaySummaryDto> getSummary(
            @RequestHeader(value = "X-Device-Token", required = false) String headerToken,
            @RequestParam(value = "token", required = false) String queryToken) {

        UUID deviceToken = resolveDeviceToken(headerToken, queryToken);
        if (deviceToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!deviceRepository.existsById(deviceToken)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        DisplaySummaryDto summary = displayService.getSummary(deviceToken);
        return ResponseEntity.ok(summary);
    }

    @GetMapping(value = "/export/csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestHeader(value = "X-Device-Token", required = false) String headerToken,
            @RequestParam(value = "token", required = false) String queryToken) {

        UUID deviceToken = resolveDeviceToken(headerToken, queryToken);
        if (deviceToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!deviceRepository.existsById(deviceToken)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        String csv = displayService.generateCsv(deviceToken);
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"health_data_export.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    private UUID resolveDeviceToken(String headerToken, String queryToken) {
        String tokenStr = (headerToken != null && !headerToken.trim().isEmpty())
                ? headerToken.trim()
                : (queryToken != null && !queryToken.trim().isEmpty() ? queryToken.trim() : null);

        if (tokenStr == null) {
            return null;
        }

        try {
            return UUID.fromString(tokenStr);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID format for device token: {}", tokenStr);
            return null;
        }
    }
}
