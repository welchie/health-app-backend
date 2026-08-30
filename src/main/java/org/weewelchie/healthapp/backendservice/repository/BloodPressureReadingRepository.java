package org.weewelchie.healthapp.backendservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.weewelchie.healthapp.backendservice.entity.BloodPressureReading;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BloodPressureReadingRepository extends JpaRepository<BloodPressureReading, Long> {

    Optional<BloodPressureReading> findByDeviceTokenAndClientId(UUID deviceToken, String clientId);

    List<BloodPressureReading> findByDeviceToken(UUID deviceToken);

    List<BloodPressureReading> findByDeviceTokenAndServerUpdatedAtGreaterThan(UUID deviceToken, Instant lastSyncTime);
}
