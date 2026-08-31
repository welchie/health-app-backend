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

### AWS Deployment with Terraform (Budget-Friendly EC2)
A budget-friendly AWS deployment configuration is located in the `terraform/` directory. It provisions:
*   A single **EC2 Instance** (`t3.micro` - Free Tier eligible).
*   An **Elastic IP** (static public IP).
*   **VPC Security Group** exposing only ports 80 (HTTP), 443 (HTTPS), and 22 (SSH).
*   **IAM Instance Profile** enabling secure connection via AWS Systems Manager (SSM) without SSH keys.
*   Automatic installation of **Docker** and **Docker Compose** on startup.

#### Prerequisites
*   [Terraform CLI](https://developer.hashicorp.com/terraform/downloads) installed.
*   An active AWS account with configured CLI credentials.

#### Step 1: Provision Infrastructure
1.  Navigate to the directory and initialize Terraform:
    ```bash
    cd terraform
    terraform init
    ```
2.  Plan and apply the configuration:
    ```bash
    terraform plan -out=tfplan
    terraform apply tfplan
    ```
3.  Note the outputs:
    *   `server_public_ip`: Point your domain's **A record** to this IP (e.g. `api.yourdomain.com`).
    *   `ssm_connect_command`: Copy this command to log into the terminal of the EC2 instance without SSH keys.

#### Step 2: Deploy Code to EC2 Instance
You can deploy your code directly to the server using `rsync` or by cloning your Git repository directly on the EC2 instance.
Using `rsync` (replace `YOUR_PEM_KEY` with your SSH key path or use standard credentials):
```bash
# From the project root folder (health-app-backend)
rsync -avz --exclude-from='.dockerignore' . ec2-user@<server_public_ip>:/home/ec2-user/app
```

#### Step 3: Run the Application
1.  Log into your EC2 server using AWS SSM:
    ```bash
    aws ssm start-session --target <instance_id> --region <aws_region>
    ```
    *(Alternatively, use `ssh ec2-user@<server_public_ip>` if you configured SSH keys).*
2.  Switch to the app directory and start the services:
    ```bash
    cd /home/ec2-user/app
    
    # Run with standard HTTP (port 80)
    docker compose -f docker-compose.prod.yml up --build -d

    # OR Run with auto-generated SSL (HTTPS on port 443) using Caddy
    DOMAIN_NAME=api.yourdomain.com docker compose -f docker-compose.prod.yml up --build -d
    ```
    *Caddy will automatically fetch, configure, and renew your SSL certificates from Let's Encrypt for your domain.*
