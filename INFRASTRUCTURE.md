# Infrastructure Documentation

## Overview

The African Language Assistant uses AWS serverless architecture with infrastructure defined as code using AWS SAM (Serverless Application Model).

## Architecture Components

### API Gateway
- **Type**: REST API with API Key authentication
- **Endpoints**:
  - `POST /query` - Text input processing
  - `POST /audio` - Audio input processing
- **Rate Limiting**: 10 requests/second, burst of 20
- **Usage Plan**: 10,000 requests per month (Free Tier)
- **CORS**: Enabled for web client access

### Lambda Functions

#### 1. InputHandlerFunction
- **Runtime**: Java 21
- **Memory**: 512MB
- **Timeout**: 30 seconds
- **Concurrency**: 10 (reserved)
- **Purpose**: Process text queries, detect language, generate responses
- **Permissions**: DynamoDB (Sessions, Cache), Bedrock

#### 2. AudioHandlerFunction
- **Runtime**: Java 21
- **Memory**: 512MB
- **Timeout**: 30 seconds
- **Concurrency**: 10 (reserved)
- **Purpose**: Handle audio uploads, initiate transcription
- **Permissions**: S3 (Audio Bucket), Transcribe

#### 3. TranscriptionProcessorFunction
- **Runtime**: Java 21
- **Memory**: 512MB
- **Timeout**: 30 seconds
- **Concurrency**: 10 (reserved)
- **Purpose**: Process transcription results, cleanup S3
- **Trigger**: S3 event (transcription JSON files)
- **Permissions**: S3 (Audio Bucket), Transcribe

#### 4. TextToSpeechFunction
- **Runtime**: Java 21
- **Memory**: 512MB
- **Timeout**: 30 seconds
- **Concurrency**: 10 (reserved)
- **Purpose**: Convert text to speech using Polly
- **Permissions**: S3 (Audio Bucket), Polly

### DynamoDB Tables

#### Sessions Table
- **Name**: `language-assistant-sessions-{environment}`
- **Billing**: On-Demand (Pay per request)
- **Primary Key**: `sessionId` (String)
- **GSI**: `UserIdIndex` on `userId`
- **TTL**: Enabled on `ttl` attribute (30 minutes)
- **Encryption**: Server-side encryption enabled
- **Attributes**:
  - `sessionId`: Unique session identifier
  - `userId`: User identifier
  - `language`: Detected/preferred language
  - `context`: Session context map
  - `conversationHistory`: List of conversation turns
  - `lastActivity`: Timestamp of last activity
  - `ttl`: Expiration timestamp (Unix epoch)

#### ResponseCache Table
- **Name**: `language-assistant-cache-{environment}`
- **Billing**: On-Demand (Pay per request)
- **Primary Key**: `queryHash` (String)
- **Sort Key**: `language` (String)
- **TTL**: Enabled on `ttl` attribute (24 hours)
- **Encryption**: Server-side encryption enabled
- **Attributes**:
  - `queryHash`: Hash of normalized query
  - `language`: Response language
  - `response`: Cached response text
  - `hitCount`: Number of cache hits
  - `ttl`: Expiration timestamp (Unix epoch)

### S3 Bucket

#### Audio Bucket
- **Name**: `language-assistant-audio-{environment}-{account-id}`
- **Purpose**: Temporary storage for audio files
- **Encryption**: AES-256 server-side encryption
- **Lifecycle Policy**: Delete objects after 1 day
- **Public Access**: Blocked (all settings)
- **CORS**: Enabled for presigned URL access
- **Versioning**: Disabled (temporary storage)

### CloudWatch

#### Log Groups
- `/aws/lambda/language-assistant-input-handler-{environment}` (7 days retention)
- `/aws/lambda/language-assistant-audio-handler-{environment}` (7 days retention)
- `/aws/lambda/language-assistant-transcription-processor-{environment}` (7 days retention)
- `/aws/lambda/language-assistant-tts-{environment}` (7 days retention)

#### Alarms
- **ErrorRateAlarm**: Triggers when error rate > 5%
- **LatencyAlarm**: Triggers when p95 latency > 3 seconds

#### Metrics Namespaces
- `AWS/Lambda` - Standard Lambda metrics
- `LanguageAssistant` - Custom application metrics

## IAM Roles and Policies

### Lambda Execution Roles
Each Lambda function has a dedicated execution role with least privilege access:

#### InputHandlerFunction Role
- DynamoDB: Read/Write on Sessions and Cache tables
- Bedrock: InvokeModel permission
- CloudWatch Logs: Create log streams and put log events

#### AudioHandlerFunction Role
- S3: Read/Write on Audio Bucket
- Transcribe: StartTranscriptionJob, GetTranscriptionJob
- CloudWatch Logs: Create log streams and put log events

#### TranscriptionProcessorFunction Role
- S3: Read/Delete on Audio Bucket
- Transcribe: GetTranscriptionJob
- CloudWatch Logs: Create log streams and put log events

#### TextToSpeechFunction Role
- S3: Read/Write on Audio Bucket
- Polly: SynthesizeSpeech
- CloudWatch Logs: Create log streams and put log events

## Free Tier Optimization

### Lambda
- **Free Tier**: 1M requests/month, 400,000 GB-seconds compute
- **Configuration**: 512MB × 30s = 15 GB-seconds per invocation
- **Capacity**: ~26,666 invocations/month within free tier
- **Concurrency Limit**: 10 to prevent runaway costs

### DynamoDB
- **Free Tier**: 25 GB storage, 25 WCU, 25 RCU
- **Billing Mode**: On-Demand (pay per request, no provisioned capacity)
- **Optimization**: TTL for automatic cleanup, efficient query patterns

### S3
- **Free Tier**: 5 GB storage, 20,000 GET requests, 2,000 PUT requests
- **Optimization**: 1-day lifecycle policy, minimal metadata

### Bedrock
- **Pricing**: Pay per token (no free tier)
- **Optimization**: Response caching, efficient prompts

### Polly
- **Free Tier**: 5M characters/month (standard), 1M characters/month (neural)
- **Optimization**: Cache common phrases, use standard voices for high volume

### Transcribe
- **Free Tier**: 60 minutes/month
- **Optimization**: Standard transcription (not real-time)

## Deployment

### Prerequisites
- AWS CLI configured with appropriate credentials
- AWS SAM CLI installed
- Java 21 JDK
- Maven 3.8+

### Build
```bash
mvn clean package
```

### Deploy to Development
```bash
./scripts/deploy.sh dev
```

### Deploy to Production
```bash
./scripts/deploy.sh prod
```

### Local Testing
```bash
./scripts/local-test.sh
```

## Environment Variables

### Lambda Functions
- `AWS_REGION`: AWS region (e.g., us-east-1)
- `SESSIONS_TABLE`: DynamoDB Sessions table name
- `CACHE_TABLE`: DynamoDB Cache table name
- `AUDIO_BUCKET`: S3 Audio bucket name
- `SPRING_CLOUD_FUNCTION_DEFINITION`: Function name for Spring Cloud Function

### Application Configuration
See `src/main/resources/application.yml` for detailed configuration.

## Monitoring

### Key Metrics to Monitor
- Lambda invocation count and errors
- Lambda duration (p50, p95, p99)
- DynamoDB read/write capacity consumption
- S3 storage size and request count
- Bedrock token usage
- Polly character count
- Transcribe minutes used

### Cost Monitoring
- Set up AWS Budgets for cost alerts
- Monitor Free Tier usage in AWS Billing Console
- CloudWatch alarm for 80% Free Tier threshold

## Security

### Data Encryption
- **In Transit**: TLS 1.2+ for all API calls
- **At Rest**: 
  - DynamoDB: Server-side encryption with AWS managed keys
  - S3: AES-256 encryption
  - CloudWatch Logs: Encrypted by default

### Access Control
- API Gateway: API Key authentication
- Lambda: IAM execution roles with least privilege
- S3: Presigned URLs with 1-hour expiration
- DynamoDB: IAM-based access control

### Data Privacy
- No PII storage beyond session duration (30 minutes)
- Automatic cleanup via TTL
- Audio files deleted after 1 day
- No persistent user data

## Disaster Recovery

### Backup Strategy
- DynamoDB: Point-in-time recovery disabled (temporary data)
- S3: No versioning (temporary storage)
- Infrastructure: Version-controlled SAM template

### Rollback Procedure
```bash
# Rollback to previous stack version
aws cloudformation rollback-stack --stack-name african-language-assistant
```

## Troubleshooting

### Common Issues

#### Lambda Timeout
- Check CloudWatch Logs for slow operations
- Verify Bedrock/Polly/Transcribe response times
- Consider increasing timeout (max 15 minutes for Lambda)

#### DynamoDB Throttling
- Check CloudWatch metrics for throttled requests
- On-Demand mode should auto-scale, but verify
- Consider adding exponential backoff in code

#### S3 Access Denied
- Verify IAM role permissions
- Check bucket policy and CORS configuration
- Ensure presigned URLs haven't expired

#### API Gateway 429 (Rate Limit)
- User exceeded rate limit (10 req/sec)
- Implement client-side retry with exponential backoff
- Consider increasing rate limit if needed

## Maintenance

### Regular Tasks
- Review CloudWatch Logs weekly
- Monitor Free Tier usage monthly
- Update dependencies quarterly
- Review and optimize costs monthly

### Updates
- Test changes in dev environment first
- Use canary deployments for production
- Monitor error rates after deployment
- Keep rollback plan ready
