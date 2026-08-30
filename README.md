# Health App Backend

This is the Spring Boot backend service for the **Personal Health App**, providing an offline-first synchronization engine and secure anonymous backup support for mobile clients.

---

## Technical Stack

*   **Java:** Version 25 (Java 25 Toolchain configured)
*   **Framework:** Spring Boot 4.1.1 & Spring Data JPA
*   **Database:** H2 Database (In-Memory for zero-setup local development)
*   **Documentation:** Springdoc OpenAPI (Swagger UI)
*   **Build Tool:** Gradle

---

## API Documentation

### 1. Anonymous Device Registration
Allows client apps to register a device and receive a secure token to authenticate future requests without requiring a sign-up form.
*   **Endpoint:** `POST /api/v1/devices`
*   **Headers:** `Content-Type: application/json`
*   **Response (200 OK):**
    ```json
    {
      "deviceToken": "3fa85f64-5717-4562-b3fc-2c963f66afa6"
    }
    ```

### 2. Synchronization Endpoint
Synchronizes locally modified blood pressure and weight records from the client and retrieves remote updates.
*   **Endpoint:** `POST /api/v1/sync`
*   **Headers:**
    *   `Content-Type: application/json`
    *   `X-Device-Token: <your-device-token-uuid>`
*   **Request Payload (`SyncPayload`):**
    ```json
    {
      "readings": [
        {
          "id": "client-uuid-1",
          "takenAt": "2026-08-30T10:00:00Z",
          "systolic": 120,
          "diastolic": 80,
          "heartRate": 72,
          "note": "Took medication",
          "updatedAt": "2026-08-30T10:05:00Z",
          "deleted": false
        }
      ],
      "weights": [
        {
          "id": "client-uuid-2",
          "takenAt": "2026-08-30T08:00:00Z",
          "grams": 78200,
          "note": "Morning weigh-in",
          "updatedAt": "2026-08-30T08:05:00Z",
          "deleted": false
        }
      ],
      "lastSyncTime": "2026-08-29T15:00:00Z"
    }
    ```
*   **Response (200 OK):** Returns all updates on the server modified since the provided `lastSyncTime`, along with a new `syncTime` to store on the client for the next sync.
    ```json
    {
      "readings": [],
      "weights": [],
      "syncTime": "2026-08-30T17:15:00Z"
    }
    ```

---

## Sync & Conflict Resolution Mechanics

*   **Last Write Wins (LWW):** Conflicts are resolved by comparing client-side modification timestamps (`updatedAt`, falling back to `takenAt`). The version with the more recent timestamp wins.
*   **Monotonic Server Clock:** The server automatically tracks a `server_updated_at` column whenever a record is created or updated in the database. Delta syncs query using this server-side modification clock to prevent missed updates across multiple devices.
*   **Auto-Registration Resiliency:** If a client attempts to synchronize with a device token that does not exist in the database (e.g., due to a database reset or wipe), the server automatically registers the token and accepts the payload, preventing synchronization crashes.

---

## Getting Started

### Prerequisites
*   JDK 25

### Run the Server
Run the Spring Boot application locally:
```bash
./gradlew bootRun
```
*   **API Base URL:** `http://localhost:8080/api/v1`
*   **Swagger UI Dashboard:** [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
*   **H2 Database Console:** [http://localhost:8080/h2-console](http://localhost:8080/h2-console)
    *   **JDBC URL:** `jdbc:h2:mem:healthappdb`
    *   **User Name:** `sa`
    *   **Password:** *(leave blank)*

### Run Tests and Coverage Reports
Run integration and unit tests:
```bash
./gradlew test jacocoTestReport
```
View the generated JaCoCo coverage report in your browser:
```bash
open build/reports/jacoco/test/html/index.html
```
