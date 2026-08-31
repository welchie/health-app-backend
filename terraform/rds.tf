# DB Subnet Group (RDS must span at least 2 Availability Zones)
resource "aws_db_subnet_group" "db_subnet" {
  name       = "${var.app_name}-db-subnet-group"
  subnet_ids = [aws_subnet.private_1.id, aws_subnet.private_2.id]

  tags = {
    Name = "${var.app_name}-db-subnet-group"
  }
}

# RDS PostgreSQL Instance
resource "aws_db_instance" "postgres" {
  identifier             = "${var.app_name}-db"
  allocated_storage      = 20
  max_allocated_storage  = 100
  engine                 = "postgres"
  engine_version         = "15.4" # Or matching your local docker setup version
  instance_class         = "db.t4g.micro" # AWS Free Tier eligible ARM instance class
  db_name                = var.db_name
  username               = var.db_username
  password               = var.db_password
  db_subnet_group_name   = aws_db_subnet_group.db_subnet.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  skip_final_snapshot    = true # Set to false in production to prevent data loss!

  tags = {
    Name = "${var.app_name}-postgres-db"
  }
}

# RDS Security Group
resource "aws_security_group" "rds" {
  name        = "${var.app_name}-rds-sg"
  description = "Access control for RDS PostgreSQL"
  vpc_id      = aws_vpc.main.id

  # Ingress: Allow traffic to port 5432 only from the ECS tasks' security group
  ingress {
    description     = "Allow database access from ECS tasks"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_tasks.id]
  }

  # Egress: Deny all outgoing traffic by default (RDS does not need to start outbound connections)
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.app_name}-rds-sg"
  }
}
