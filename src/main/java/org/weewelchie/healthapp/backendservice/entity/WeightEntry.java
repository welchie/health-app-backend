package org.weewelchie.healthapp.backendservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "weight_entries", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"device_token", "client_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeightEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_token", nullable = false)
    private UUID deviceToken;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "taken_at", nullable = false)
    private Instant takenAt;

    @Column(name = "grams", nullable = false)
    private Integer grams;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "client_updated_at")
    private Instant clientUpdatedAt;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    @Column(name = "server_updated_at", nullable = false)
    private Instant serverUpdatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        serverUpdatedAt = Instant.now();
    }
}
