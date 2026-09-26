# Health App Backend

This is the Spring Boot backend service for the **Personal Health App**, providing an offline-first synchronization engine and secure anonymous backup support for mobile clients.

---

## Technical Stack

*   **Java:** Version 25 (Java 25 Toolchain configured)
*   **Framework:** Spring Boot 4.1.1 & Spring Data JPA
*   **Database:** H2 Database (In-Memory for zero-setup local development)
*   **Documentation:** Springdoc OpenAPI (Swagger UI)
*   **Operations & Health:** Spring Boot Actuator
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
Run the Spring Boot application locally using the default in-memory H2 database:
```bash
./gradlew bootRun
```

To run the application with the **PostgreSQL production profile (`prod`)**:
```bash
# Optional: Override database connection details using environment variables
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/healthapp
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=mysecurepassword

./gradlew bootRun --args='--spring.profiles.active=prod'
```
*   **API Base URL:** `http://localhost:8080/api/v1`
*   **Actuator Health Endpoint:** [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
*   **Swagger UI Dashboard:** [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
*   **H2 Database Console:** [http://localhost:8080/h2-console](http://localhost:8080/h2-console)
    *   **JDBC URL:** `jdbc:h2:mem:healthappdb`
    *   **User Name:** `sa`
    *   **Password:** *(leave blank)*

### Run via Docker Compose (Local PostgreSQL Integration)
You can build the backend Docker image and spin up both the application and a PostgreSQL database locally using Docker Compose:
```bash
docker compose up --build
```
This builds the multi-stage Docker image, runs the database, waits until PostgreSQL is fully healthy, and launches the backend service connected to the database.

### Run Tests and Coverage Reports
Run integration and unit tests:
```bash
./gradlew test jacocoTestReport
```
View the generated JaCoCo coverage report in your browser:
```bash
open build/reports/jacoco/test/html/index.html
```

---

## AWS Deployment & Updates (Budget-Friendly EC2)

The backend service can be deployed to a single AWS EC2 instance (`t3.micro`, Free Tier eligible) managed with Terraform and Docker Compose.

* **Networking & Security:** VPC Security Group exposing ports `80` (HTTP), `443` (HTTPS), and `22` (SSH).
* **Access:** AWS Systems Manager (SSM) Session Manager or direct SSH.
* **SSL / Reverse Proxy:** Caddy reverse proxies port 80/443 to Spring Boot on port 8080 with automatic Let's Encrypt certificates.

### Step 1: Provision Infrastructure (Initial Setup)
1. Navigate to the terraform directory:
   ```bash
   cd terraform
   terraform init
   terraform apply
   ```
2. Note the outputs:
   * `server_public_ip`: Public IP of your EC2 instance.
   * `ssm_connect_command`: Command to connect via AWS SSM.

### Step 2: Push / Sync Code to EC2
From the project root on your local machine, run `rsync` to sync your code to `/home/ec2-user/app`:
```bash
rsync -avz --exclude-from='.dockerignore' --exclude='.git' --exclude='build' . ec2-user@<server_public_ip>:/home/ec2-user/app
```

### Step 3: Rebuild & Restart Containers on EC2
Run the build and restart command directly via SSH (or log in via AWS SSM):

#### **Production Setup (with Caddy Reverse Proxy on Port 80/443 - Recommended):**
```bash
ssh ec2-user@<server_public_ip> "cd /home/ec2-user/app && docker compose -f docker-compose.prod.yml up --build -d"
```
*(With custom domain and automatic SSL certificates):*
```bash
ssh ec2-user@<server_public_ip> "cd /home/ec2-user/app && DOMAIN_NAME=api.yourdomain.com docker compose -f docker-compose.prod.yml up --build -d"
```

#### **Standard Setup (Direct Port 8080):**
```bash
ssh ec2-user@<server_public_ip> "cd /home/ec2-user/app && docker compose up --build -d"
```
*Note: If running standard setup, port 8080 is only accessible within the EC2 host unless port 8080 is opened in the AWS Security Group.*

### Step 4: Verify Deployment & Health Status
Check that the service is running and healthy:

* **Via your Custom Domain (HTTPS):**
  ```bash
  curl -i https://api.weewelchie.org/actuator/health
  ```
  *(Or open `https://api.weewelchie.org/actuator/health` directly in any web browser).*

* **From your local machine via HTTP (verifies automatic redirect to HTTPS):**
  ```bash
  curl -i http://<server_public_ip>/actuator/health
  ```

* **Directly on the EC2 host (port 8080):**
  ```bash
  ssh ec2-user@<server_public_ip> "curl -i http://localhost:8080/actuator/health"
  ```

**Expected JSON Response (200 OK):**
```json
{"groups":["liveness","readiness"],"status":"UP"}
```

---

### Step 5: Configure AWS CloudWatch Alarms & SNS Email Notifications

The system includes automated health monitoring using AWS CloudWatch and Amazon SNS.

#### How It Works:
1. **On-Host Monitoring:** A script (`/usr/local/bin/check-health.sh`) runs every minute via cron on the EC2 host.
2. **Metric Publishing:** It probes `http://127.0.0.1:8080/actuator/health`. If healthy (HTTP 200), it publishes `BackendUnhealthy = 0` to CloudWatch namespace `HealthApp`. If down or non-200, it publishes `BackendUnhealthy = 1`.
3. **CloudWatch Alarm:** The alarm (`health-app-backend-unhealthy`) monitors `BackendUnhealthy >= 1`.
4. **Email Notification:** When triggered, an alert is dispatched via Amazon SNS to the configured `alert_email`.

#### Configuration via Terraform:
1. In `terraform/variables.tf`, set your email:
   ```hcl
   variable "alert_email" {
     default = "your-email@example.com"
   }
   ```
2. Apply the Terraform configuration:
   ```bash
   cd terraform
   terraform apply
   ```
3. **Confirm Subscription:** AWS SNS sends a confirmation email with the subject **`AWS Notification - Subscription Confirmation`**. Click the **"Confirm subscription"** link in that email to authorize AWS to send alert emails to your inbox.

---

### Step 6: Test That Alerts Fire

You can verify that your alert pipeline and email notifications work using any of the following methods:

#### Method 1: Instant Alarm Simulation (Fastest & Zero Downtime — Recommended)
Trigger the CloudWatch alarm directly using the AWS CLI:
```bash
aws cloudwatch set-alarm-state \
  --alarm-name "health-app-backend-unhealthy" \
  --state-value ALARM \
  --state-reason "Testing alert notifications" \
  --region eu-west-1
```
* **Result:** CloudWatch transitions to **`ALARM`** and immediately sends an alert email to your inbox.
* **Auto-Recovery:** Within 60 seconds, the cron job on EC2 reports that the service is healthy (`0`), and CloudWatch sends an **`OK`** recovery email.

#### Method 2: Simulate Failure Metric from EC2
Push a failure metric (`1`) directly from the server:
```bash
ssh ec2-user@<server_public_ip> "aws cloudwatch put-metric-data --namespace 'HealthApp' --metric-name 'BackendUnhealthy' --value 1 --region eu-west-1"
```
* **Result:** CloudWatch receives an unhealthy datapoint, triggers the alarm, and sends the email alert.

#### Method 3: Real End-to-End Container Failure Test
Test the full failure-detection pipeline by stopping the backend container:
1. **Stop the container:**
   ```bash
   ssh ec2-user@<server_public_ip> "cd /home/ec2-user/app && docker compose -f docker-compose.prod.yml stop backend"
   ```
2. **Wait 60–90 seconds:** The cron monitor detects that `/actuator/health` is unreachable, pushes `BackendUnhealthy = 1`, and CloudWatch sends the **`ALARM`** email.
3. **Restart the container:**
   ```bash
   ssh ec2-user@<server_public_ip> "cd /home/ec2-user/app && docker compose -f docker-compose.prod.yml start backend"
   ```
4. **Wait 60 seconds:** The monitor verifies the service is back up, pushes `BackendUnhealthy = 0`, and CloudWatch sends the **`OK`** recovery email.

