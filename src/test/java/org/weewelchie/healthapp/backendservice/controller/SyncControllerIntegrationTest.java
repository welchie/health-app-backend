package org.weewelchie.healthapp.backendservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.weewelchie.healthapp.backendservice.dto.BloodPressureReadingDto;
import org.weewelchie.healthapp.backendservice.dto.SyncPayloadDto;
import org.weewelchie.healthapp.backendservice.dto.WeightEntryDto;
import org.weewelchie.healthapp.backendservice.entity.BloodPressureReading;
import org.weewelchie.healthapp.backendservice.entity.WeightEntry;
import org.weewelchie.healthapp.backendservice.repository.BloodPressureReadingRepository;
import org.weewelchie.healthapp.backendservice.repository.DeviceRepository;
import org.weewelchie.healthapp.backendservice.repository.WeightEntryRepository;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SyncControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private BloodPressureReadingRepository bloodPressureReadingRepository;

    @Autowired
    private WeightEntryRepository weightEntryRepository;

    @BeforeEach
    void setUp() {
        bloodPressureReadingRepository.deleteAll();
        weightEntryRepository.deleteAll();
        deviceRepository.deleteAll();
    }

    @Test
    void registerDevice_shouldReturnUuid() throws Exception {
        mockMvc.perform(post("/api/v1/devices")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceToken", notNullValue()))
                .andDo(result -> {
                    String content = result.getResponse().getContentAsString();
                    UUID token = UUID.fromString(objectMapper.readTree(content).get("deviceToken").asText());
                    assertThat(deviceRepository.existsById(token)).isTrue();
                });
    }

    @Test
    void syncData_shouldAutoRegisterUnknownDeviceAndSaveNewRecords() throws Exception {
        UUID unknownToken = UUID.randomUUID();
        
        BloodPressureReadingDto reading = new BloodPressureReadingDto(
                "bp-1",
                Instant.parse("2026-08-23T19:00:00Z"),
                120,
                80,
                72,
                "Feels good",
                Instant.parse("2026-08-23T19:05:00Z"),
                false
        );

        WeightEntryDto weight = new WeightEntryDto(
                "w-1",
                Instant.parse("2026-08-23T08:00:00Z"),
                80000,
                "Before breakfast",
                Instant.parse("2026-08-23T08:05:00Z"),
                false
        );

        SyncPayloadDto payload = new SyncPayloadDto(
                Collections.singletonList(reading),
                Collections.singletonList(weight),
                null
        );

        mockMvc.perform(post("/api/v1/sync")
                        .header("X-Device-Token", unknownToken.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncTime", notNullValue()))
                .andExpect(jsonPath("$.readings", hasSize(1)))
                .andExpect(jsonPath("$.weights", hasSize(1)))
                .andExpect(jsonPath("$.readings[0].id").value("bp-1"))
                .andExpect(jsonPath("$.weights[0].id").value("w-1"));

        // Verify it was stored in the DB
        assertThat(deviceRepository.existsById(unknownToken)).isTrue();
        
        List<BloodPressureReading> readings = bloodPressureReadingRepository.findByDeviceToken(unknownToken);
        assertThat(readings).hasSize(1);
        assertThat(readings.get(0).getClientId()).isEqualTo("bp-1");
        assertThat(readings.get(0).getSystolic()).isEqualTo(120);

        List<WeightEntry> weights = weightEntryRepository.findByDeviceToken(unknownToken);
        assertThat(weights).hasSize(1);
        assertThat(weights.get(0).getClientId()).isEqualTo("w-1");
        assertThat(weights.get(0).getGrams()).isEqualTo(80000);
    }

    @Test
    void syncData_shouldPerformLastWriteWinsMergeOnConflict() throws Exception {
        UUID deviceToken = UUID.randomUUID();
        
        // Auto-register device
        mockMvc.perform(post("/api/v1/devices")).andDo(result -> {});

        // 1. Seed the DB with an initial reading and weight
        BloodPressureReading bpSeed = new BloodPressureReading(
                null,
                deviceToken,
                "bp-1",
                Instant.parse("2026-08-23T10:00:00Z"),
                130,
                85,
                70,
                "First note",
                Instant.parse("2026-08-23T10:00:00Z"),
                false,
                Instant.now()
        );
        bloodPressureReadingRepository.save(bpSeed);

        // 2. Client sends an older modification (should lose, db wins)
        BloodPressureReadingDto clientOlder = new BloodPressureReadingDto(
                "bp-1",
                Instant.parse("2026-08-23T10:00:00Z"),
                110, // Older value
                70,
                60,
                "Client older note",
                Instant.parse("2026-08-23T09:00:00Z"), // Older timestamp
                false
        );

        SyncPayloadDto payloadOlder = new SyncPayloadDto(
                Collections.singletonList(clientOlder),
                Collections.emptyList(),
                null
        );

        mockMvc.perform(post("/api/v1/sync")
                        .header("X-Device-Token", deviceToken.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payloadOlder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readings", hasSize(1)))
                .andExpect(jsonPath("$.readings[0].systolic").value(130)) // DB value won
                .andExpect(jsonPath("$.readings[0].diastolic").value(85));

        // 3. Client sends a newer modification (should win, overwrites db)
        BloodPressureReadingDto clientNewer = new BloodPressureReadingDto(
                "bp-1",
                Instant.parse("2026-08-23T10:00:00Z"),
                120, // Newer value
                80,
                68,
                "Client newer note",
                Instant.parse("2026-08-23T11:00:00Z"), // Newer timestamp
                false
        );

        SyncPayloadDto payloadNewer = new SyncPayloadDto(
                Collections.singletonList(clientNewer),
                Collections.emptyList(),
                null
        );

        mockMvc.perform(post("/api/v1/sync")
                        .header("X-Device-Token", deviceToken.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payloadNewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readings", hasSize(1)))
                .andExpect(jsonPath("$.readings[0].systolic").value(120)) // Client newer won
                .andExpect(jsonPath("$.readings[0].diastolic").value(80));
    }

    @Test
    void syncData_shouldReturn400IfReadingsExceedLimit() throws Exception {
        UUID deviceToken = UUID.randomUUID();
        
        BloodPressureReadingDto reading = new BloodPressureReadingDto(
                "bp-1", Instant.now(), 120, 80, 70, "Note", Instant.now(), false
        );
        List<BloodPressureReadingDto> oversizedList = Collections.nCopies(1001, reading);

        SyncPayloadDto payload = new SyncPayloadDto(
                oversizedList,
                Collections.emptyList(),
                null
        );

        mockMvc.perform(post("/api/v1/sync")
                        .header("X-Device-Token", deviceToken.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }
}
