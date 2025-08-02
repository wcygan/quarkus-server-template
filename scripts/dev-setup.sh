#!/bin/bash

# Quarkus User Management API - Development Environment Setup Script
# This script sets up the complete development environment

set -e

echo "🚀 Setting up Quarkus User Management API Development Environment..."

# Check if Docker is running
if ! docker info >/dev/null 2>&1; then
    echo "❌ Error: Docker is not running. Please start Docker and try again."
    exit 1
fi

# Start MySQL database
echo "📦 Starting MySQL database..."
docker-compose up -d mysql

# Wait for MySQL to be ready
echo "⏳ Waiting for MySQL to be ready..."
timeout=60
counter=0
while ! docker-compose exec mysql mysqladmin ping -h"localhost" --silent; do
    sleep 2
    counter=$((counter + 2))
    if [ $counter -ge $timeout ]; then
        echo "❌ Error: MySQL failed to start within $timeout seconds"
        exit 1
    fi
done

echo "✅ MySQL is ready!"

# Run Flyway migrations
echo "🏗️  Running database migrations..."
./mvnw flyway:migrate

# Generate jOOQ classes
echo "🔧 Generating jOOQ classes from database schema..."
./mvnw generate-sources

echo "✅ Development environment setup complete!"
echo ""
echo "🎯 Next steps:"
echo "   1. Start Quarkus in dev mode: ./mvnw quarkus:dev"
echo "   2. Open http://localhost:8080 in your browser"
echo "   3. Check health status: http://localhost:8080/q/health"
echo "   4. Access Dev UI: http://localhost:8080/q/dev"
echo ""
echo "🛠️  Useful development commands:"
echo "   - Stop database: docker-compose down"
echo "   - Reset database: docker-compose down -v && ./scripts/dev-setup.sh"
echo "   - View logs: docker-compose logs -f mysql"
echo "   - Run tests: ./mvnw test"