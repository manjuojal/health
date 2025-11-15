#!/bin/bash

set -e

echo "========================================"
echo "Building Health Monitor Starter"
echo "========================================"
echo ""

echo "Step 1: Building health-monitor-starter..."
mvn clean install

if [ $? -ne 0 ]; then
    echo ""
    echo "ERROR: Failed to build health-monitor-starter"
    exit 1
fi

echo ""
echo "========================================"
echo "Build completed successfully!"
echo "========================================"
echo ""

echo "To run the test application:"
echo "  cd ../scheduler-demo-app"
echo "  mvn spring-boot:run"
echo ""
echo "To run with Spring Boot Admin:"
echo "  1. Start Admin Server: cd ../admin-server && mvn spring-boot:run"
echo "  2. Start App: cd ../scheduler-demo-app && mvn spring-boot:run"
echo "  3. Access UI: http://localhost:8080 (admin/admin)"
echo ""
echo "Or for other test applications:"
echo "  cd ../health-monitor-test-app"
echo "  mvn spring-boot:run"
echo ""
