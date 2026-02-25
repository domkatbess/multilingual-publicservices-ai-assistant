#!/bin/bash

# Local testing script using SAM CLI
# Usage: ./scripts/local-test.sh

set -e

echo "Building application..."
mvn clean package

echo "Starting local API..."
sam local start-api --env-vars env.json

# To invoke a specific function locally:
# sam local invoke InputHandlerFunction --event events/text-query.json
