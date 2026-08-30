package org.weewelchie.healthapp.backendservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.weewelchie.healthapp.backendservice.entity.WeightEntry;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WeightEntryRepository extends JpaRepository<WeightEntry, Long> {

    Optional<WeightEntry> findByDeviceTokenAndClientId(UUID deviceToken, String clientId);

    List<WeightEntry> findByDeviceToken(UUID deviceToken);

    List<WeightEntry> findByDeviceTokenAndServerUpdatedAtGreaterThan(UUID deviceToken, Instant lastSyncTime);
}
