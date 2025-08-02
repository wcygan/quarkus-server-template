#!/bin/bash

# Quarkus User Management API - Environment Verification Script
# Verifies that the complete development environment is working correctly

set -e

echo "🔍 Verifying Quarkus User Management API Development Environment..."
echo ""

# Function to check command existence
check_command() {
    if command -v $1 >/dev/null 2>&1; then
        echo "✅ $1 is installed"
    else
        echo "❌ $1 is not installed or not in PATH"
        return 1
    fi
}

# Function to check if port is available
check_port() {
    if lsof -Pi :$1 -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo "⚠️  Port $1 is already in use"
        return 1
    else
        echo "✅ Port $1 is available"
    fi
}

echo "1. Checking prerequisites..."
check_command java
check_command mvn
check_command docker
check_command docker-compose

echo ""
echo "2. Checking Java version..."
java_version=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$java_version" -ge 17 ]; then
    echo "✅ Java $java_version (meets requirement: Java 17+)"
else
    echo "❌ Java $java_version (requires Java 17+)"
    exit 1
fi

echo ""
echo "3. Checking Docker..."
if docker info >/dev/null 2>&1; then
    echo "✅ Docker is running"
else
    echo "❌ Docker is not running. Please start Docker."
    exit 1
fi

echo ""
echo "4. Checking ports..."
check_port 3306
check_port 8080
check_port 8081

echo ""
echo "5. Validating Docker Compose configuration..."
docker-compose config >/dev/null 2>&1
echo "✅ docker-compose.yml is valid"

echo ""
echo "6. Checking Maven configuration..."
if ./mvnw --version >/dev/null 2>&1; then
    echo "✅ Maven wrapper is working"
else
    echo "❌ Maven wrapper failed"
    exit 1
fi

echo ""
echo "7. Validating Maven dependencies..."
./mvnw dependency:resolve >/dev/null 2>&1
echo "✅ Maven dependencies resolved"

echo ""
echo "8. Testing MySQL startup..."
echo "   Starting MySQL container..."
docker-compose up -d mysql >/dev/null 2>&1

echo "   Waiting for MySQL to be ready..."
timeout=60
counter=0
while ! docker-compose exec mysql mysqladmin ping -h"localhost" --silent 2>/dev/null; do
    sleep 2
    counter=$((counter + 2))
    if [ $counter -ge $timeout ]; then
        echo "❌ MySQL failed to start within $timeout seconds"
        docker-compose logs mysql
        exit 1
    fi
done
echo "✅ MySQL is running and accessible"

echo ""
echo "9. Testing database connection..."
if docker-compose exec mysql mysql -u userapi -puserapi -e "SELECT 1" userapi >/dev/null 2>&1; then
    echo "✅ Database connection successful"
else
    echo "❌ Database connection failed"
    exit 1
fi

echo ""
echo "10. Testing Flyway migrations..."
./mvnw flyway:migrate >/dev/null 2>&1
echo "✅ Flyway migrations executed successfully"

echo ""
echo "11. Testing jOOQ code generation..."
./mvnw generate-sources >/dev/null 2>&1
if [ -d "target/generated-sources/jooq" ] && [ "$(find target/generated-sources/jooq -name '*.java' | wc -l)" -gt 0 ]; then
    echo "✅ jOOQ classes generated successfully"
else
    echo "❌ jOOQ code generation failed"
    exit 1
fi

echo ""
echo "12. Cleaning up..."
docker-compose down >/dev/null 2>&1
echo "✅ Environment cleanup completed"

echo ""
echo "🎉 Environment verification completed successfully!"
echo ""
echo "✅ All checks passed. Your development environment is ready!"
echo ""
echo "🚀 Next steps:"
echo "   1. Start development environment: ./scripts/dev-start.sh"
echo "   2. Begin implementation following PHASE-2.md"
echo ""
echo "📚 Useful commands:"
echo "   • Quick setup: ./scripts/dev-setup.sh"
echo "   • Start dev mode: ./scripts/dev-start.sh"
echo "   • Run tests: ./mvnw test"
echo "   • View this help again: ./scripts/verify-environment.sh"