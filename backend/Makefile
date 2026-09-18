.PHONY: up down logs build validate

# Start all infrastructure (databases + Kafka) in the background
up:
	docker compose up -d

# Stop and remove all containers
down:
	docker compose down

# View the infrastructure logs
logs:
	docker compose logs -f

# Validate the Maven POM structure
validate:
	mvn clean validate

# Build the entire backend locally
build:
	mvn clean install -DskipTests

# Run all tests
test:
	mvn clean test