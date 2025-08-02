#!/bin/bash

# Quarkus User Management API - Quick Development Start Script
# Starts the complete development environment in one command

set -e

echo "🚀 Starting Quarkus User Management API Development Environment..."

# Check if MySQL is already running
if docker-compose ps mysql | grep -q "Up"; then
    echo "✅ MySQL is already running"
else
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
fi

# Check if jOOQ classes exist
if [ ! -d "target/generated-sources/jooq" ]; then
    echo "🔧 Generating jOOQ classes (first run)..."
    ./mvnw generate-sources
fi

# Start Quarkus in development mode
echo "🎯 Starting Quarkus in development mode..."
echo "   📱 Application: http://localhost:8080"
echo "   ❤️  Health Check: http://localhost:8080/q/health"
echo "   🛠️  Dev UI: http://localhost:8080/q/dev"
echo ""
echo "Press Ctrl+C to stop the development server"
echo ""

./mvnw quarkus:dev