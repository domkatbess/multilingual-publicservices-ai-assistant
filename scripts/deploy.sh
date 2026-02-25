#!/bin/bash

# Deployment script for African Language Assistant
# Usage: ./scripts/deploy.sh [dev|prod]

set -e

ENVIRONMENT=${1:-dev}

echo "Building application..."
mvn clean package

echo "Deploying to $ENVIRONMENT environment..."
if [ "$ENVIRONMENT" = "prod" ]; then
    sam deploy --config-env prod
else
    sam deploy --config-env default
fi

echo "Deployment complete!"
echo "To get the API endpoint, run:"
echo "aws cloudformation describe-stacks --stack-name african-language-assistant${ENVIRONMENT:+-$ENVIRONMENT} --query 'Stacks[0].Outputs[?OutputKey==\`ApiEndpoint\`].OutputValue' --output text"
