# Deployment Guide

This guide walks through deploying the African Language Assistant to AWS.

## Prerequisites

### 1. Install Required Tools

#### AWS CLI
```bash
# macOS
brew install awscli

# Linux
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install

# Windows
# Download and run the AWS CLI MSI installer from:
# https://awscli.amazonaws.com/AWSCLIV2.msi
```

#### AWS SAM CLI
```bash
# macOS
brew install aws-sam-cli

# Linux
pip install aws-sam-cli

# Windows
# Download and run the AWS SAM CLI MSI installer from:
# https://github.com/aws/aws-sam-cli/releases/latest
```

#### Java 21
```bash
# macOS
brew install openjdk@21

# Linux (Ubuntu/Debian)
sudo apt-get update
sudo apt-get install openjdk-21-jdk

# Windows
# Download and install from:
# https://adoptium.net/
```

#### Maven
```bash
# macOS
brew install maven

# Linux (Ubuntu/Debian)
sudo apt-get install maven

# Windows
# Download and install from:
# https://maven.apache.org/download.cgi
```

### 2. Configure AWS Credentials

```bash
aws configure
```

Enter your:
- AWS Access Key ID
- AWS Secret Access Key
- Default region (e.g., us-east-1)
- Default output format (json)

### 3. Verify Prerequisites

```bash
# Check AWS CLI
aws --version

# Check SAM CLI
sam --version

# Check Java
java -version

# Check Maven
mvn -version
```

## Deployment Steps

### Step 1: Build the Application

```bash
# Clean and build the project
mvn clean package

# This creates target/language-assistant-1.0.0-aws.jar
```

### Step 2: Create S3 Bucket for SAM Artifacts

SAM needs an S3 bucket to upload deployment artifacts:

```bash
# Create bucket (replace YOUR-BUCKET-NAME with a unique name)
aws s3 mb s3://YOUR-BUCKET-NAME --region us-east-1

# Update samconfig.toml with your bucket name
# Edit samconfig.toml and set s3_bucket = "YOUR-BUCKET-NAME"
```

### Step 3: Deploy to Development

```bash
# Deploy using the deployment script
./scripts/deploy.sh dev

# Or manually with SAM
sam deploy --config-env default
```

SAM will:
1. Package the application
2. Upload to S3
3. Create/update CloudFormation stack
4. Deploy all resources (API Gateway, Lambda, DynamoDB, S3, etc.)

### Step 4: Get API Endpoint

```bash
# Get the API endpoint URL
aws cloudformation describe-stacks \
  --stack-name african-language-assistant \
  --query 'Stacks[0].Outputs[?OutputKey==`ApiEndpoint`].OutputValue' \
  --output text
```

### Step 5: Create API Key

```bash
# Get the API key
aws apigateway get-api-keys \
  --include-values \
  --query 'items[?name==`language-assistant-usage-plan-dev`].value' \
  --output text
```

### Step 6: Test the Deployment

```bash
# Replace with your actual API endpoint and key
export API_ENDPOINT="https://xxxxx.execute-api.us-east-1.amazonaws.com/dev"
export API_KEY="your-api-key"

# Test text query
curl -X POST $API_ENDPOINT/query \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: $API_KEY" \
  -d '{
    "userId": "test-user",
    "sessionId": "test-session",
    "message": "How do I apply for a birth certificate?",
    "preferredLanguage": "en"
  }'
```

## Deploy to Production

### Step 1: Update Configuration

Edit `samconfig.toml` to configure production settings if needed.

### Step 2: Deploy

```bash
./scripts/deploy.sh prod
```

### Step 3: Verify Production Deployment

```bash
# Get production API endpoint
aws cloudformation describe-stacks \
  --stack-name african-language-assistant-prod \
  --query 'Stacks[0].Outputs[?OutputKey==`ApiEndpoint`].OutputValue' \
  --output text
```

## Local Testing

### Test Locally with SAM

```bash
# Start local API Gateway
./scripts/local-test.sh

# In another terminal, test the local endpoint
curl -X POST http://localhost:3000/query \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "test-user",
    "sessionId": "test-session",
    "message": "How do I apply for a birth certificate?",
    "preferredLanguage": "en"
  }'
```

### Invoke Function Directly

```bash
# Test InputHandlerFunction with sample event
sam local invoke InputHandlerFunction \
  --event events/text-query.json \
  --env-vars env.json
```

## Monitoring Deployment

### View CloudFormation Stack

```bash
# List stack resources
aws cloudformation describe-stack-resources \
  --stack-name african-language-assistant

# View stack events
aws cloudformation describe-stack-events \
  --stack-name african-language-assistant \
  --max-items 20
```

### View Lambda Functions

```bash
# List Lambda functions
aws lambda list-functions \
  --query 'Functions[?starts_with(FunctionName, `language-assistant`)].FunctionName'
```

### View DynamoDB Tables

```bash
# List tables
aws dynamodb list-tables \
  --query 'TableNames[?starts_with(@, `language-assistant`)]'
```

### View S3 Buckets

```bash
# List buckets
aws s3 ls | grep language-assistant
```

## Updating the Application

### Step 1: Make Code Changes

Edit your Java code, configuration, or infrastructure template.

### Step 2: Rebuild

```bash
mvn clean package
```

### Step 3: Redeploy

```bash
# Development
./scripts/deploy.sh dev

# Production
./scripts/deploy.sh prod
```

SAM will automatically:
- Detect changes
- Create a changeset
- Show you what will change
- Apply the changes after confirmation

## Rollback

### Automatic Rollback

If deployment fails, CloudFormation automatically rolls back to the previous stable version.

### Manual Rollback

```bash
# Rollback to previous version
aws cloudformation rollback-stack \
  --stack-name african-language-assistant
```

## Cleanup

### Delete Development Stack

```bash
# Delete all resources
aws cloudformation delete-stack \
  --stack-name african-language-assistant

# Wait for deletion to complete
aws cloudformation wait stack-delete-complete \
  --stack-name african-language-assistant
```

### Delete Production Stack

```bash
aws cloudformation delete-stack \
  --stack-name african-language-assistant-prod
```

### Delete S3 Buckets

```bash
# Empty and delete audio bucket
aws s3 rm s3://language-assistant-audio-dev-ACCOUNT-ID --recursive
aws s3 rb s3://language-assistant-audio-dev-ACCOUNT-ID

# Delete SAM artifacts bucket
aws s3 rm s3://YOUR-BUCKET-NAME --recursive
aws s3 rb s3://YOUR-BUCKET-NAME
```

## Troubleshooting

### Build Fails

```bash
# Clean Maven cache
mvn clean

# Rebuild with debug output
mvn package -X
```

### Deployment Fails

```bash
# Check CloudFormation events for errors
aws cloudformation describe-stack-events \
  --stack-name african-language-assistant \
  --max-items 10

# View detailed error messages
aws cloudformation describe-stack-resources \
  --stack-name african-language-assistant
```

### Lambda Function Errors

```bash
# View recent logs
aws logs tail /aws/lambda/language-assistant-input-handler-dev --follow

# View specific log stream
aws logs get-log-events \
  --log-group-name /aws/lambda/language-assistant-input-handler-dev \
  --log-stream-name 'STREAM-NAME'
```

### Permission Errors

Ensure your AWS credentials have the following permissions:
- CloudFormation: Full access
- Lambda: Full access
- API Gateway: Full access
- DynamoDB: Full access
- S3: Full access
- IAM: Create/update roles and policies
- CloudWatch: Create log groups and alarms

### SAM CLI Issues

```bash
# Update SAM CLI
pip install --upgrade aws-sam-cli

# Verify installation
sam --version
```

## Best Practices

### 1. Use Separate AWS Accounts
- Development account for testing
- Production account for live services

### 2. Enable CloudTrail
- Track all API calls
- Audit security and compliance

### 3. Set Up Budgets
- Monitor AWS costs
- Alert when approaching Free Tier limits

### 4. Use Parameter Store
- Store sensitive configuration
- Avoid hardcoding secrets

### 5. Implement CI/CD
- Automate testing and deployment
- Use GitHub Actions, AWS CodePipeline, or similar

### 6. Monitor Metrics
- Set up CloudWatch dashboards
- Configure alarms for critical metrics

### 7. Regular Backups
- Export DynamoDB tables periodically
- Version control infrastructure code

### 8. Security Scanning
- Scan dependencies for vulnerabilities
- Use AWS Security Hub

## Cost Estimation

### Free Tier (First 12 Months)
- Lambda: 1M requests/month, 400,000 GB-seconds
- DynamoDB: 25 GB storage, 25 WCU, 25 RCU
- S3: 5 GB storage, 20,000 GET, 2,000 PUT
- API Gateway: 1M API calls/month
- CloudWatch: 10 custom metrics, 10 alarms

### Beyond Free Tier
- Lambda: ~$0.20 per 1M requests
- DynamoDB: ~$1.25 per million write requests
- S3: ~$0.023 per GB/month
- Bedrock: ~$0.01 per 1K tokens (varies by model)
- Polly: ~$4 per 1M characters

### Estimated Monthly Cost (Low Usage)
- Within Free Tier: $0
- Beyond Free Tier: $5-20/month (depending on usage)

## Support

For deployment issues:
1. Check CloudFormation events
2. Review CloudWatch logs
3. Consult AWS documentation
4. Open an issue in the repository
