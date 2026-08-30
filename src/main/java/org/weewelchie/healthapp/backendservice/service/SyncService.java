package org.weewelchie.healthapp.backendservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.weewelchie.healthapp.backendservice.dto.*;
import org.weewelchie.healthapp.backendservice.entity.Device;
import org.weewelchie.healthapp.backendservice.entity.BloodPressureReading;
import org.weewelchie.healthapp.backendservice.entity.WeightEntry;
import org.weewelchie.healthapp.backendservice.repository.DeviceRepository;
import org.weewelchie.healthapp.backendservice.repository.BloodPressureReadingRepository;
import org.weewelchie.healthapp.backendservice.repository.WeightEntryRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncService {

    private final DeviceRepository deviceRepository;
    private final BloodPressureReadingRepository bloodPressureReadingRepository;
    private final WeightEntryRepository weightEntryRepository;

    /**
     * Registers a new device anonymously by generating a secure random UUID token.
     */
    @Transactional
    public DeviceRegistrationResponseDto registerDevice() {
        UUID token = UUID.randomUUID();
        Device device = new Device(token, Instant.now());
        deviceRepository.save(device);
        log.info("Registered new device with token: {}", token);
        return new DeviceRegistrationResponseDto(token);
    }

    /**
     * Performs synchronization logic (Last Write Wins merge).
     */
    @Transactional
    public SyncResponseDto syncData(UUID deviceToken, SyncPayloadDto payload) {
        log.info("Starting sync for device token: {}. Payload contains {} readings, {} weights.",
                deviceToken,
                payload.getReadings() != null ? payload.getReadings().size() : 0,
                payload.getWeights() != null ? payload.getWeights().size() : 0);

        // Resiliency: Auto-register device if it doesn't exist
        ensureDeviceExists(deviceToken);

        Instant syncStartTime = Instant.now();

        // 1. Merge Blood Pressure Readings
        if (payload.getReadings() != null) {
            for (BloodPressureReadingDto clientReading : payload.getReadings()) {
                mergeBloodPressureReading(deviceToken, clientReading);
            }
        }

        // 2. Merge Weight Entries
        if (payload.getWeights() != null) {
            for (WeightEntryDto clientWeight : payload.getWeights()) {
                mergeWeightEntry(deviceToken, clientWeight);
            }
        }

        // 3. Retrieve updates from DB since lastSyncTime
        Instant lastSyncTime = payload.getLastSyncTime();
        List<BloodPressureReading> updatedReadings;
        List<WeightEntry> updatedWeights;

        if (lastSyncTime == null) {
            updatedReadings = bloodPressureReadingRepository.findByDeviceToken(deviceToken);
            updatedWeights = weightEntryRepository.findByDeviceToken(deviceToken);
        } else {
            updatedReadings = bloodPressureReadingRepository.findByDeviceTokenAndServerUpdatedAtGreaterThan(deviceToken, lastSyncTime);
            updatedWeights = weightEntryRepository.findByDeviceTokenAndServerUpdatedAtGreaterThan(deviceToken, lastSyncTime);
        }

        // Map to DTOs
        List<BloodPressureReadingDto> readingDtos = updatedReadings.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

        List<WeightEntryDto> weightDtos = updatedWeights.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

        log.info("Sync complete. Returning {} updated readings, {} updated weights. syncTime: {}",
                readingDtos.size(), weightDtos.size(), syncStartTime);

        return new SyncResponseDto(readingDtos, weightDtos, syncStartTime);
    }

    private void ensureDeviceExists(UUID deviceToken) {
        if (!deviceRepository.existsById(deviceToken)) {
            log.info("Device token {} not found. Auto-registering device.", deviceToken);
            Device device = new Device(deviceToken, Instant.now());
            deviceRepository.save(device);
        }
    }

    private void mergeBloodPressureReading(UUID deviceToken, BloodPressureReadingDto clientDto) {
        Optional<BloodPressureReading> optReading = bloodPressureReadingRepository
                .findByDeviceTokenAndClientId(deviceToken, clientDto.getId());

        if (optReading.isPresent()) {
            BloodPressureReading existing = optReading.get();
            Instant incomingTime = clientDto.getUpdatedAt() != null ? clientDto.getUpdatedAt() : clientDto.getTakenAt();
            Instant existingTime = existing.getClientUpdatedAt() != null ? existing.getClientUpdatedAt() : existing.getTakenAt();

            if (incomingTime != null && (existingTime == null || incomingTime.isAfter(existingTime) || incomingTime.equals(existingTime))) {
                // Incoming is newer or equal (Last Write Wins)
                existing.setSystolic(clientDto.getSystolic());
                existing.setDiastolic(clientDto.getDiastolic());
                existing.setHeartRate(clientDto.getHeartRate());
                existing.setNote(sanitizeNote(clientDto.getNote()));
                existing.setTakenAt(clientDto.getTakenAt());
                existing.setClientUpdatedAt(clientDto.getUpdatedAt());
                existing.setDeleted(clientDto.getDeleted() != null && clientDto.getDeleted());
                bloodPressureReadingRepository.save(existing);
            }
        } else {
            // New record
            BloodPressureReading reading = new BloodPressureReading();
            reading.setDeviceToken(deviceToken);
            reading.setClientId(clientDto.getId());
            reading.setSystolic(clientDto.getSystolic());
            reading.setDiastolic(clientDto.getDiastolic());
            reading.setHeartRate(clientDto.getHeartRate());
            reading.setNote(sanitizeNote(clientDto.getNote()));
            reading.setTakenAt(clientDto.getTakenAt());
            reading.setClientUpdatedAt(clientDto.getUpdatedAt());
            reading.setDeleted(clientDto.getDeleted() != null && clientDto.getDeleted());
            bloodPressureReadingRepository.save(reading);
        }
    }

    private void mergeWeightEntry(UUID deviceToken, WeightEntryDto clientDto) {
        Optional<WeightEntry> optWeight = weightEntryRepository
                .findByDeviceTokenAndClientId(deviceToken, clientDto.getId());

        if (optWeight.isPresent()) {
            WeightEntry existing = optWeight.get();
            Instant incomingTime = clientDto.getUpdatedAt() != null ? clientDto.getUpdatedAt() : clientDto.getTakenAt();
            Instant existingTime = existing.getClientUpdatedAt() != null ? existing.getClientUpdatedAt() : existing.getTakenAt();

            if (incomingTime != null && (existingTime == null || incomingTime.isAfter(existingTime) || incomingTime.equals(existingTime))) {
                // Incoming is newer or equal (Last Write Wins)
                existing.setGrams(clientDto.getGrams());
                existing.setNote(sanitizeNote(clientDto.getNote()));
                existing.setTakenAt(clientDto.getTakenAt());
                existing.setClientUpdatedAt(clientDto.getUpdatedAt());
                existing.setDeleted(clientDto.getDeleted() != null && clientDto.getDeleted());
                weightEntryRepository.save(existing);
            }
        } else {
            // New record
            WeightEntry weight = new WeightEntry();
            weight.setDeviceToken(deviceToken);
            weight.setClientId(clientDto.getId());
            weight.setGrams(clientDto.getGrams());
            weight.setNote(sanitizeNote(clientDto.getNote()));
            weight.setTakenAt(clientDto.getTakenAt());
            weight.setClientUpdatedAt(clientDto.getUpdatedAt());
            weight.setDeleted(clientDto.getDeleted() != null && clientDto.getDeleted());
            weightEntryRepository.save(weight);
        }
    }

    private BloodPressureReadingDto mapToDto(BloodPressureReading entity) {
        return new BloodPressureReadingDto(
                entity.getClientId(),
                entity.getTakenAt(),
                entity.getSystolic(),
                entity.getDiastolic(),
                entity.getHeartRate(),
                entity.getNote(),
                entity.getClientUpdatedAt(),
                entity.getDeleted()
        );
    }

    private WeightEntryDto mapToDto(WeightEntry entity) {
        return new WeightEntryDto(
                entity.getClientId(),
                entity.getTakenAt(),
                entity.getGrams(),
                entity.getNote(),
                entity.getClientUpdatedAt(),
                entity.getDeleted()
        );
    }

    private String sanitizeNote(String note) {
        if (note == null) return null;
        return note.length() > 1000 ? note.substring(0, 1000) : note;
    }
}
