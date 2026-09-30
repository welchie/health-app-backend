package org.weewelchie.healthapp.backendservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.weewelchie.healthapp.backendservice.dto.*;
import org.weewelchie.healthapp.backendservice.entity.BloodPressureReading;
import org.weewelchie.healthapp.backendservice.entity.WeightEntry;
import org.weewelchie.healthapp.backendservice.repository.BloodPressureReadingRepository;
import org.weewelchie.healthapp.backendservice.repository.WeightEntryRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DisplayService {

    public static final double GRAMS_PER_POUND = 453.59237;
    public static final int POUNDS_PER_STONE = 14;

    private final BloodPressureReadingRepository bpRepository;
    private final WeightEntryRepository weightRepository;

    @Transactional(readOnly = true)
    public List<DisplayBloodPressureDto> getReadings(UUID deviceToken, Instant startDate, Instant endDate, int limit) {
        List<BloodPressureReading> readings;
        if (startDate != null && endDate != null) {
            readings = bpRepository.findByDeviceTokenAndDeletedFalseAndTakenAtBetweenOrderByTakenAtDesc(deviceToken, startDate, endDate);
        } else {
            readings = bpRepository.findByDeviceTokenAndDeletedFalseOrderByTakenAtDesc(deviceToken);
        }

        if (limit > 0 && readings.size() > limit) {
            readings = readings.subList(0, limit);
        }

        return readings.stream().map(this::mapToDisplayBp).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DisplayWeightDto> getWeights(UUID deviceToken, Instant startDate, Instant endDate, int limit) {
        List<WeightEntry> weights;
        if (startDate != null && endDate != null) {
            weights = weightRepository.findByDeviceTokenAndDeletedFalseAndTakenAtBetweenOrderByTakenAtDesc(deviceToken, startDate, endDate);
        } else {
            weights = weightRepository.findByDeviceTokenAndDeletedFalseOrderByTakenAtDesc(deviceToken);
        }

        if (limit > 0 && weights.size() > limit) {
            weights = weights.subList(0, limit);
        }

        return weights.stream().map(this::mapToDisplayWeight).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DisplaySummaryDto getSummary(UUID deviceToken) {
        List<BloodPressureReading> readings = bpRepository.findByDeviceTokenAndDeletedFalseOrderByTakenAtDesc(deviceToken);
        List<WeightEntry> weights = weightRepository.findByDeviceTokenAndDeletedFalseOrderByTakenAtDesc(deviceToken);

        DisplayBloodPressureDto latestBp = readings.isEmpty() ? null : mapToDisplayBp(readings.get(0));
        DisplayWeightDto latestWeight = weights.isEmpty() ? null : mapToDisplayWeight(weights.get(0));

        Instant now = Instant.now();
        Instant sevenDaysAgo = now.minus(7, ChronoUnit.DAYS);
        Instant thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS);

        List<BloodPressureReading> readings7d = readings.stream()
                .filter(r -> r.getTakenAt().isAfter(sevenDaysAgo))
                .toList();

        List<BloodPressureReading> readings30d = readings.stream()
                .filter(r -> r.getTakenAt().isAfter(thirtyDaysAgo))
                .toList();

        Map<String, BpAverageDto> averages = new LinkedHashMap<>();
        averages.put("sevenDay", computeBpAverage(readings7d));
        averages.put("thirtyDay", computeBpAverage(readings30d));

        WeightChangeDto weightChange = computeWeightChange(weights, thirtyDaysAgo);

        return DisplaySummaryDto.builder()
                .totalReadings(readings.size())
                .totalWeights(weights.size())
                .latestReading(latestBp)
                .latestWeight(latestWeight)
                .averages(averages)
                .weightChange30d(weightChange)
                .build();
    }

    @Transactional(readOnly = true)
    public String generateCsv(UUID deviceToken) {
        List<BloodPressureReading> readings = bpRepository.findByDeviceTokenAndDeletedFalseOrderByTakenAtDesc(deviceToken);
        List<WeightEntry> weights = weightRepository.findByDeviceTokenAndDeletedFalseOrderByTakenAtDesc(deviceToken);

        record ExportRow(Instant takenAt, String type, String systolic, String diastolic, String heartRate, String weightKg, String weightStLb, String category, String note) {}

        List<ExportRow> rows = new ArrayList<>();

        for (BloodPressureReading bp : readings) {
            rows.add(new ExportRow(
                    bp.getTakenAt(),
                    "Blood Pressure",
                    String.valueOf(bp.getSystolic()),
                    String.valueOf(bp.getDiastolic()),
                    bp.getHeartRate() != null ? String.valueOf(bp.getHeartRate()) : "",
                    "",
                    "",
                    categorise(bp.getSystolic(), bp.getDiastolic()),
                    bp.getNote() != null ? bp.getNote().replace("\"", "\"\"") : ""
            ));
        }

        for (WeightEntry w : weights) {
            double kg = Math.round(w.getGrams() / 100.0) / 10.0;
            rows.add(new ExportRow(
                    w.getTakenAt(),
                    "Weight",
                    "",
                    "",
                    "",
                    String.format("%.1f", kg),
                    formatStoneLbs(w.getGrams()),
                    "",
                    w.getNote() != null ? w.getNote().replace("\"", "\"\"") : ""
            ));
        }

        rows.sort((a, b) -> b.takenAt().compareTo(a.takenAt()));

        StringBuilder csv = new StringBuilder();
        csv.append("Type,Date Taken (UTC),Systolic (mmHg),Diastolic (mmHg),Heart Rate (bpm),Weight (kg),Weight (st/lb),Category,Note\r\n");

        for (ExportRow r : rows) {
            csv.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\r\n",
                    r.type(),
                    r.takenAt(),
                    r.systolic(),
                    r.diastolic(),
                    r.heartRate(),
                    r.weightKg(),
                    r.weightStLb(),
                    r.category(),
                    r.note()
            ));
        }

        return csv.toString();
    }

    public DisplayBloodPressureDto mapToDisplayBp(BloodPressureReading entity) {
        return DisplayBloodPressureDto.builder()
                .id(entity.getClientId())
                .takenAt(entity.getTakenAt())
                .systolic(entity.getSystolic())
                .diastolic(entity.getDiastolic())
                .heartRate(entity.getHeartRate())
                .category(categorise(entity.getSystolic(), entity.getDiastolic()))
                .note(entity.getNote())
                .build();
    }

    public DisplayWeightDto mapToDisplayWeight(WeightEntry entity) {
        double kg = Math.round(entity.getGrams() / 100.0) / 10.0;
        return DisplayWeightDto.builder()
                .id(entity.getClientId())
                .takenAt(entity.getTakenAt())
                .grams(entity.getGrams())
                .kg(kg)
                .stonePounds(formatStoneLbs(entity.getGrams()))
                .note(entity.getNote())
                .build();
    }

    private BpAverageDto computeBpAverage(List<BloodPressureReading> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }

        long sumSys = 0;
        long sumDia = 0;
        long sumHr = 0;
        int hrCount = 0;

        for (BloodPressureReading r : list) {
            sumSys += r.getSystolic();
            sumDia += r.getDiastolic();
            if (r.getHeartRate() != null) {
                sumHr += r.getHeartRate();
                hrCount++;
            }
        }

        int avgSys = (int) Math.round((double) sumSys / list.size());
        int avgDia = (int) Math.round((double) sumDia / list.size());
        Integer avgHr = hrCount > 0 ? (int) Math.round((double) sumHr / hrCount) : null;

        return BpAverageDto.builder()
                .systolic(avgSys)
                .diastolic(avgDia)
                .heartRate(avgHr)
                .count(list.size())
                .category(categorise(avgSys, avgDia))
                .build();
    }

    private WeightChangeDto computeWeightChange(List<WeightEntry> weights, Instant thirtyDaysAgo) {
        if (weights == null || weights.size() < 2) {
            return null;
        }

        WeightEntry latest = weights.get(0);
        // Find baseline entry closest to or before 30 days ago, or the oldest entry
        WeightEntry baseline = weights.stream()
                .filter(w -> w.getTakenAt().isBefore(thirtyDaysAgo))
                .findFirst()
                .orElse(weights.get(weights.size() - 1));

        if (latest.equals(baseline)) {
            return null;
        }

        int diffGrams = latest.getGrams() - baseline.getGrams();
        double diffKg = Math.round((diffGrams / 1000.0) * 10.0) / 10.0;
        double diffLbs = Math.round((diffGrams / GRAMS_PER_POUND) * 10.0) / 10.0;

        String prefix = diffGrams > 0 ? "+" : "";
        String display = String.format("%s%.1f lb (%s%.1f kg)", prefix, diffLbs, prefix, diffKg);

        return WeightChangeDto.builder()
                .grams(diffGrams)
                .display(display)
                .build();
    }

    public static String categorise(int systolic, int diastolic) {
        if (systolic >= 180 || diastolic >= 120) return "Very high";
        if (systolic >= 140 || diastolic >= 90) return "High (stage 2)";
        if (systolic >= 130 || diastolic >= 80) return "High (stage 1)";
        if (systolic >= 120) return "Elevated";
        if (systolic < 90 || diastolic < 60) return "Low";
        return "Normal";
    }

    public static String formatStoneLbs(int grams) {
        double totalPounds = grams / GRAMS_PER_POUND;
        int stones = (int) (totalPounds / POUNDS_PER_STONE);
        double pounds = Math.round((totalPounds - stones * POUNDS_PER_STONE) * 10.0) / 10.0;
        if (pounds >= POUNDS_PER_STONE) {
            stones += 1;
            pounds = 0.0;
        }
        return String.format("%d st %.1f lb", stones, pounds);
    }
}
