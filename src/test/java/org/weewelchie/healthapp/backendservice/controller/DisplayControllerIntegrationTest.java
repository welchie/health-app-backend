package org.weewelchie.healthapp.backendservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.weewelchie.healthapp.backendservice.entity.BloodPressureReading;
import org.weewelchie.healthapp.backendservice.entity.Device;
import org.weewelchie.healthapp.backendservice.entity.WeightEntry;
import org.weewelchie.healthapp.backendservice.repository.BloodPressureReadingRepository;
import org.weewelchie.healthapp.backendservice.repository.DeviceRepository;
import org.weewelchie.healthapp.backendservice.repository.WeightEntryRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DisplayControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private BloodPressureReadingRepository bloodPressureReadingRepository;

    @Autowired
    private WeightEntryRepository weightEntryRepository;

    private UUID deviceToken;

    @BeforeEach
    void setUp() {
        bloodPressureReadingRepository.deleteAll();
        weightEntryRepository.deleteAll();
        deviceRepository.deleteAll();

        deviceToken = UUID.randomUUID();
        deviceRepository.save(new Device(deviceToken, Instant.now()));
    }

    @Test
    void getReadings_unauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/readings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getReadings_notFoundForUnknownDevice() throws Exception {
        mockMvc.perform(get("/api/v1/readings")
                        .header("X-Device-Token", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getReadings_successWithHeaderToken() throws Exception {
        BloodPressureReading reading = new BloodPressureReading();
        reading.setDeviceToken(deviceToken);
        reading.setClientId("bp-1");
        reading.setTakenAt(Instant.now());
        reading.setSystolic(135);
        reading.setDiastolic(85);
        reading.setHeartRate(60);
        reading.setNote("Morning test");
        reading.setDeleted(false);
        bloodPressureReadingRepository.save(reading);

        mockMvc.perform(get("/api/v1/readings")
                        .header("X-Device-Token", deviceToken.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("bp-1"))
                .andExpect(jsonPath("$[0].systolic").value(135))
                .andExpect(jsonPath("$[0].diastolic").value(85))
                .andExpect(jsonPath("$[0].category").value("High (stage 1)"));
    }

    @Test
    void getReadings_successWithQueryParamToken() throws Exception {
        BloodPressureReading reading = new BloodPressureReading();
        reading.setDeviceToken(deviceToken);
        reading.setClientId("bp-query");
        reading.setTakenAt(Instant.now());
        reading.setSystolic(118);
        reading.setDiastolic(75);
        reading.setHeartRate(65);
        reading.setDeleted(false);
        bloodPressureReadingRepository.save(reading);

        mockMvc.perform(get("/api/v1/readings")
                        .param("token", deviceToken.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("bp-query"))
                .andExpect(jsonPath("$[0].category").value("Normal"));
    }

    @Test
    void getWeights_successWithCalculatedUnits() throws Exception {
        WeightEntry weight = new WeightEntry();
        weight.setDeviceToken(deviceToken);
        weight.setClientId("w-1");
        weight.setTakenAt(Instant.now());
        weight.setGrams(81647);
        weight.setDeleted(false);
        weightEntryRepository.save(weight);

        mockMvc.perform(get("/api/v1/weights")
                        .param("token", deviceToken.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("w-1"))
                .andExpect(jsonPath("$[0].grams").value(81647))
                .andExpect(jsonPath("$[0].kg").value(81.6))
                .andExpect(jsonPath("$[0].stonePounds").value("12 st 12.0 lb"));
    }

    @Test
    void getSummary_returnsAggregatesAndAverages() throws Exception {
        Instant now = Instant.now();

        // 1. Add BP readings (one recent, one older)
        BloodPressureReading r1 = new BloodPressureReading();
        r1.setDeviceToken(deviceToken);
        r1.setClientId("bp-recent");
        r1.setTakenAt(now.minus(2, ChronoUnit.DAYS));
        r1.setSystolic(130);
        r1.setDiastolic(80);
        r1.setHeartRate(70);
        r1.setDeleted(false);
        bloodPressureReadingRepository.save(r1);

        BloodPressureReading r2 = new BloodPressureReading();
        r2.setDeviceToken(deviceToken);
        r2.setClientId("bp-older");
        r2.setTakenAt(now.minus(20, ChronoUnit.DAYS));
        r2.setSystolic(140);
        r2.setDiastolic(90);
        r2.setHeartRate(60);
        r2.setDeleted(false);
        bloodPressureReadingRepository.save(r2);

        // 2. Add weights (latest and baseline 25 days ago)
        WeightEntry w1 = new WeightEntry();
        w1.setDeviceToken(deviceToken);
        w1.setClientId("w-latest");
        w1.setTakenAt(now.minus(1, ChronoUnit.DAYS));
        w1.setGrams(80000);
        w1.setDeleted(false);
        weightEntryRepository.save(w1);

        WeightEntry w2 = new WeightEntry();
        w2.setDeviceToken(deviceToken);
        w2.setClientId("w-baseline");
        w2.setTakenAt(now.minus(25, ChronoUnit.DAYS));
        w2.setGrams(81000);
        w2.setDeleted(false);
        weightEntryRepository.save(w2);

        mockMvc.perform(get("/api/v1/summary")
                        .param("token", deviceToken.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReadings").value(2))
                .andExpect(jsonPath("$.totalWeights").value(2))
                .andExpect(jsonPath("$.latestReading.id").value("bp-recent"))
                .andExpect(jsonPath("$.latestWeight.id").value("w-latest"))
                .andExpect(jsonPath("$.averages.sevenDay.systolic").value(130))
                .andExpect(jsonPath("$.averages.sevenDay.count").value(1))
                .andExpect(jsonPath("$.averages.thirtyDay.systolic").value(135))
                .andExpect(jsonPath("$.averages.thirtyDay.count").value(2))
                .andExpect(jsonPath("$.weightChange30d.grams").value(-1000));
    }

    @Test
    void exportCsv_returnsValidCsvAttachment() throws Exception {
        BloodPressureReading bp = new BloodPressureReading();
        bp.setDeviceToken(deviceToken);
        bp.setClientId("bp-csv");
        bp.setTakenAt(Instant.now());
        bp.setSystolic(120);
        bp.setDiastolic(80);
        bp.setHeartRate(72);
        bp.setNote("Morning test");
        bp.setDeleted(false);
        bloodPressureReadingRepository.save(bp);

        mockMvc.perform(get("/api/v1/export/csv")
                        .param("token", deviceToken.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"health_data_export.csv\""))
                .andExpect(content().contentType("text/csv; charset=UTF-8"))
                .andExpect(content().string(containsString("Type,Date Taken (UTC),Systolic (mmHg)")))
                .andExpect(content().string(containsString("Blood Pressure")))
                .andExpect(content().string(containsString("120")))
                .andExpect(content().string(containsString("High (stage 1)")));
    }
}
