.PHONY: up down logs build validate test

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
	sh ./mvnw clean validate

# Build the entire backend locally
build:
	sh ./mvnw clean install -DskipTests

# Run all tests
test:
	sh ./mvnw clean test
