.PHONY: up down logs build validate

# Start the database and message broker in the background
up:
	docker compose up -d

# Stop and remove the containers
down:
	docker compose down

# View the infrastructure logs
logs:
	docker compose logs -f

# Validate the Maven POM structure
validate:
	mvn clean validate

# Build the entire backend locally (used once modules exist)
build:
	mvn clean install -DskipTests