package org.weewelchie.healthapp.backendservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.weewelchie.healthapp.backendservice.dto.DeviceRegistrationResponseDto;
import org.weewelchie.healthapp.backendservice.dto.SyncPayloadDto;
import org.weewelchie.healthapp.backendservice.dto.SyncResponseDto;
import org.weewelchie.healthapp.backendservice.service.SyncService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class SyncController {

    private final SyncService syncService;

    @PostMapping("/devices")
    public ResponseEntity<DeviceRegistrationResponseDto> registerDevice() {
        log.info("Received request to register device");
        DeviceRegistrationResponseDto response = syncService.registerDevice();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sync")
    public ResponseEntity<SyncResponseDto> syncData(
            @RequestHeader(value = "X-Device-Token", required = false) String deviceTokenStr,
            @RequestBody SyncPayloadDto payload) {
        
        log.info("Received sync request with device token: {}", deviceTokenStr);
        
        if (deviceTokenStr == null || deviceTokenStr.trim().isEmpty()) {
            log.warn("Missing X-Device-Token header");
            return ResponseEntity.badRequest().build();
        }

        UUID deviceToken;
        try {
            deviceToken = UUID.fromString(deviceTokenStr.trim());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID format for X-Device-Token: {}", deviceTokenStr);
            return ResponseEntity.badRequest().build();
        }

        if (payload == null) {
            return ResponseEntity.badRequest().build();
        }

        if (payload.getReadings() != null && payload.getReadings().size() > 1000) {
            log.warn("Payload readings list size {} exceeds limit of 1000", payload.getReadings().size());
            return ResponseEntity.badRequest().build();
        }

        if (payload.getWeights() != null && payload.getWeights().size() > 1000) {
            log.warn("Payload weights list size {} exceeds limit of 1000", payload.getWeights().size());
            return ResponseEntity.badRequest().build();
        }

        SyncResponseDto response = syncService.syncData(deviceToken, payload);
        return ResponseEntity.ok(response);
    }
}
