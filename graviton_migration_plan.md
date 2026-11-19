# AWS Graviton Migration Analysis & Execution Plan

## 1. Executive Summary & Verification

We verified your AWS account and live infrastructure using the AWS CLI and AWS Cost Optimization Hub. 

AWS has generated an official recommendation for your production backend instance:

| Detail | Current Configuration | Recommended Configuration |
| :--- | :--- | :--- |
| **Instance ID** | `i-018755deeaf165529` (`health-app-docker-server`) | `i-018755deeaf165529` |
| **Region / AZ** | `eu-west-1` (`eu-west-1a`) | `eu-west-1` |
| **Instance Type** | **`t3.micro`** (Intel x86_64, 2 vCPU, 1 GB RAM) | **`t4g.micro`** (AWS Graviton2 ARM64, 2 vCPU, 1 GB RAM) |
| **Monthly Cost** | **$8.32 / month** ($0.0104/hr) | **$6.71 / month** ($0.0084/hr) |
| **Monthly Savings** | — | **$1.61 / month** (~20% reduction) |
| **Annual Savings** | — | **$19.32 / year** |
| **Effort Classification** | — | **Very High** (Architecture change x86_64 &rarr; ARM64) |
| **Recommendation ID** | `b7e4de2d35a5c8c2` | Cost Optimization Hub / Compute Optimizer |

> [!NOTE]
> **Bonus Account Finding:** Cost Optimization Hub also identified an unattached 8 GB gp2 EBS volume (`vol-049734fd2c86ec9fa`) attached to stopped instance `i-03ca29d3d4174badc` costing **$0.88/month**. Deleting or snapshotting it saves additional money.

---

## 2. Compatibility Assessment

Your backend application is **100% compatible** with AWS Graviton (ARM64):

1. **Java 25 / Spring Boot 4.1.1**: Java bytecode is CPU-architecture independent. The base images in your [Dockerfile](file:///Users/chriswelch/workspace/HealthApp/health-app-backend/Dockerfile) (`eclipse-temurin:25-jdk` and `eclipse-temurin:25-jre`) are official multi-architecture images with native `linux/arm64` support.
2. **PostgreSQL 18**: The Docker image `postgres:18-alpine` in [docker-compose.prod.yml](file:///Users/chriswelch/workspace/HealthApp/health-app-backend/docker-compose.prod.yml) is multi-arch and supports `linux/arm64`.
3. **Caddy 2 Proxy**: The Docker image `caddy:2-alpine` natively supports `linux/arm64`.
4. **Amazon Linux 2023**: Amazon provides first-class `al2023-ami-2023.*-arm64` images.
5. **Local Consistency**: Your local development machine is already Apple Silicon (`arm64`), meaning local builds and production will share the identical CPU architecture.

---

## 3. Critical Considerations & Risks

> [!CAUTION]
> **High Risk: Production Database Data Loss**
> PostgreSQL runs in Docker with its data stored in the volume `pgdata` on the EC2 instance's root EBS volume (`delete_on_termination = true`).
> Because AWS EC2 does not allow switching CPU architectures (x86 &rarr; ARM64) on an existing instance, the instance **must be replaced**.
> Recreating the instance will terminate the root volume and **permanently delete the database** unless an explicit dump/backup is taken and restored!
> 
> *Live Data Verified on Instance:*
> - `devices`: 8 registered devices
> - `blood_pressure_readings`: 88 readings
> - `weight_entries`: 21 entries

> [!WARNING]
> **Code Changes Required in Terraform**
> You cannot just change `instance_type = "t4g.micro"`:
> 1. [main.tf](file:///Users/chriswelch/workspace/HealthApp/health-app-backend/terraform/main.tf#L165) filters for `al2023-ami-2023.*-x86_64`. It will fail to launch `t4g.micro` because `t4g` requires an `arm64` AMI.
> 2. [main.tf](file:///Users/chriswelch/workspace/HealthApp/health-app-backend/terraform/main.tf#L215-L220) downloads x86_64 binaries for `docker-compose` and `buildx` in `user_data`. On Graviton, these will throw `Exec format error`.
> 3. `lifecycle { ignore_changes = [ami, user_data] }` in `aws_instance.server` prevents Terraform from replacing the AMI in-place. An explicit replacement must be instructed.

> [!IMPORTANT]
> **Downtime & Networking**
> Recreating the EC2 instance takes approximately **3 to 5 minutes**. 
> - **Elastic IP (`108.133.59.82`)**: Managed by Terraform (`aws_eip.ip`). Terraform will automatically re-associate this static IP with the new Graviton instance, so DNS (`api.weewelchie.org`) does **not** need to change.
> - **SSL Certificates**: Caddy will automatically request Let's Encrypt certificates upon booting the new instance.

---

## 4. Financial & Strategic Verdict

* **Savings**: **$1.61/month** ($19.32/year).
* **Performance Gain**: AWS Graviton2 provides ~20–40% better compute efficiency and lower latency per vCPU compared to 5th-gen Intel.
* **Effort**: Low-to-Medium when using the automated plan below, but requires ~5 minutes of scheduled downtime and a database backup/restore step.
* **Recommendation**: **Proceed** if you want to modernize the infrastructure, align production with your local ARM64 environment, and gain better CPU performance. If a $1.61/month saving is not worth 5 minutes of planned downtime, you can safely dismiss the recommendation in AWS Console.

---

## 5. Step-by-Step Migration Plan

### Phase 1: Update Terraform Code

#### 1. Update [variables.tf](file:///Users/chriswelch/workspace/HealthApp/health-app-backend/terraform/variables.tf)
Update `instance_type` default to `t4g.micro` and add `instance_architecture`:

```hcl
variable "instance_type" {
  description = "EC2 instance type (t4g.micro Graviton2 ARM64)"
  type        = string
  default     = "t4g.micro"
}

variable "instance_architecture" {
  description = "Architecture for the EC2 instance (arm64 for Graviton, x86_64 for Intel/AMD)"
  type        = string
  default     = "arm64"
}
```

#### 2. Update [main.tf](file:///Users/chriswelch/workspace/HealthApp/health-app-backend/terraform/main.tf)

1. Make AMI filter dynamic using `var.instance_architecture`:
```hcl
data "aws_ami" "amazon_linux_2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-${var.instance_architecture}"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}
```

2. Make Docker plugin downloads architecture-aware in `user_data`:
```bash
              # Determine architecture for Docker plugins
              ARCH=$(uname -m)
              if [ "$ARCH" = "aarch64" ]; then
                COMPOSE_ARCH="aarch64"
                BUILDX_ARCH="arm64"
              else
                COMPOSE_ARCH="x86_64"
                BUILDX_ARCH="amd64"
              fi

              # Install Docker Compose V2
              mkdir -p /usr/local/lib/docker/cli-plugins
              curl -SL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-$${COMPOSE_ARCH}" -o /usr/local/lib/docker/cli-plugins/docker-compose
              chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

              # Install Docker Buildx plugin
              curl -SL "https://github.com/docker/buildx/releases/download/v0.17.1/buildx-v0.17.1.linux-$${BUILDX_ARCH}" -o /usr/local/lib/docker/cli-plugins/docker-buildx
              chmod +x /usr/local/lib/docker/cli-plugins/docker-buildx
```

---

### Phase 2: Live Database Backup (Pre-Migration)

Before destroying the old instance, dump the PostgreSQL database to your local machine:

```bash
# 1. SSH into the current EC2 instance and create a SQL dump
ssh ec2-user@108.133.59.82 "docker exec healthapp-db pg_dump -U postgres healthapp" > backup_healthapp_$(date +%Y%m%d_%H%M%S).sql

# 2. Verify backup contains data
head -n 25 backup_healthapp_*.sql
grep -E "INSERT INTO|COPY public\." backup_healthapp_*.sql | head -n 10
```

*(Alternatively, via AWS SSM Session Manager):*
```bash
aws ssm send-command \
  --instance-ids i-018755deeaf165529 \
  --document-name "AWS-RunShellScript" \
  --parameters 'commands=["docker exec healthapp-db pg_dump -U postgres healthapp > /home/ec2-user/backup.sql"]' \
  --region eu-west-1
```

---

### Phase 3: Infrastructure Replacement via Terraform

Apply Terraform with an explicit replacement of `aws_instance.server`:

```bash
cd terraform
terraform plan -replace="aws_instance.server"
terraform apply -replace="aws_instance.server" -auto-approve
```

*What happens during this step:*
1. Old `t3.micro` instance is terminated.
2. New `t4g.micro` instance is provisioned with Amazon Linux 2023 ARM64.
3. User data script runs, installs Docker, architecture-appropriate Docker Compose, Buildx, creates 2GB swap space, and configures CloudWatch monitoring.
4. Elastic IP (`108.133.59.82`) is automatically re-associated with the new `t4g.micro` instance.

---

### Phase 4: Deploy Backend & Restore Database

Wait ~2 minutes for the new instance to complete user data initialization, then:

```bash
# 1. Sync backend application code to new instance
rsync -avz --exclude-from='.dockerignore' --exclude='.git' --exclude='build' . ec2-user@108.133.59.82:/home/ec2-user/app

# 2. Start PostgreSQL and Caddy first
ssh ec2-user@108.133.59.82 "cd /home/ec2-user/app && docker compose -f docker-compose.prod.yml up -d postgres caddy"

# 3. Wait for PostgreSQL container to become healthy
ssh ec2-user@108.133.59.82 "docker exec healthapp-db pg_isready -U postgres"

# 4. Restore the database dump from your local machine
cat backup_healthapp_*.sql | ssh ec2-user@108.133.59.82 "docker exec -i healthapp-db psql -U postgres -d healthapp"

# 5. Build and start the backend service
ssh ec2-user@108.133.59.82 "cd /home/ec2-user/app && docker compose -f docker-compose.prod.yml up --build -d backend"
```

---

### Phase 5: Verification & Testing

```bash
# 1. Check container health status on EC2
ssh ec2-user@108.133.59.82 "docker ps"

# 2. Verify restored record counts
ssh ec2-user@108.133.59.82 "docker exec healthapp-db psql -U postgres -d healthapp -c 'SELECT count(*) FROM devices;' -c 'SELECT count(*) FROM blood_pressure_readings;' -c 'SELECT count(*) FROM weight_entries;'"

# 3. Verify public HTTPS health endpoint
curl -i https://api.weewelchie.org/actuator/health

# 4. Verify CloudWatch custom metric publishing
aws cloudwatch get-metric-data \
  --metric-data-queries '[{"Id":"m1","MetricStat":{"Metric":{"Namespace":"HealthApp","MetricName":"BackendUnhealthy"},"Period":60,"Stat":"Maximum"}}]' \
  --start-time $(date -u -v-15M +%Y-%m-%dT%H:%M:%SZ) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%SZ) \
  --region eu-west-1
```

---

### Phase 6: Rollback Plan

If unexpected issues occur:
1. In `terraform/variables.tf`, revert `instance_type` to `"t3.micro"` and `instance_architecture` to `"x86_64"`.
2. Run `terraform apply -replace="aws_instance.server" -auto-approve`.
3. Restore database from `backup_healthapp_*.sql`.
4. Launch containers via `docker compose -f docker-compose.prod.yml up --build -d`.
