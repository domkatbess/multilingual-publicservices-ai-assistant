# African Language Assistant

A serverless multilingual AI assistant that enables access to public services (government, health, education, emergency) in low-resource African languages including Hausa, Yoruba, Igbo, and Fulfulde.

## Features

- **Multilingual Support**: Hausa, Yoruba, Igbo, Fulfulde, and English
- **Text and Voice Input**: Support for both text queries and voice input via speech-to-text
- **Text-to-Speech**: Audio responses for users with limited literacy
- **AI-Powered Responses**: Context-aware responses using Amazon Bedrock
- **Session Management**: Maintains conversation context across multiple turns
- **Response Caching**: Optimizes costs and response times for common queries
- **Service Categories**: Specialized handling for Government, Health, Education, and Emergency services
- **Free Tier Optimized**: Designed to stay within AWS Free Tier limits

## Architecture

Built on AWS serverless architecture:
- **API Gateway**: REST API with API key authentication
- **Lambda Functions**: Java 21 with Spring Boot and Spring Cloud Function
- **DynamoDB**: Session management and response caching
- **S3**: Temporary audio file storage
- **Amazon Bedrock**: AI/ML for language understanding and generation
- **Amazon Polly**: Text-to-speech conversion
- **Amazon Transcribe**: Speech-to-text conversion
- **CloudWatch**: Logging and monitoring

## Prerequisites

- AWS Account with appropriate permissions
- AWS CLI configured
- AWS SAM CLI installed
- Java 21 JDK
- Maven 3.8+

## Quick Start

### 1. Build the Application

```bash
mvn clean package
```

### 2. Deploy to AWS

```bash
# Deploy to development environment
./scripts/deploy.sh dev

# Deploy to production environment
./scripts/deploy.sh prod
```

### 3. Get API Endpoint

```bash
aws cloudformation describe-stacks \
  --stack-name african-language-assistant \
  --query 'Stacks[0].Outputs[?OutputKey==`ApiEndpoint`].OutputValue' \
  --output text
```

### 4. Test the API

```bash
# Text query example
curl -X POST https://your-api-endpoint/dev/query \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: your-api-key" \
  -d '{
    "userId": "user123",
    "sessionId": "session456",
    "message": "How do I apply for a birth certificate?",
    "preferredLanguage": "en"
  }'
```

## Local Development

### Run Locally with SAM

```bash
./scripts/local-test.sh
```

### Test Individual Functions

```bash
sam local invoke InputHandlerFunction --event events/text-query.json
```

## Project Structure

```
.
├── src/
│   └── main/
│       ├── java/com/africanservices/assistant/
│       │   ├── Application.java
│       │   ├── config/
│       │   │   └── AwsConfig.java
│       │   ├── function/          # Lambda function handlers
│       │   ├── service/           # Business logic services
│       │   ├── model/             # Data models
│       │   └── util/              # Utility classes
│       └── resources/
│           └── application.yml
├── template.yaml                  # SAM infrastructure template
├── pom.xml                        # Maven dependencies
├── samconfig.toml                 # SAM deployment configuration
├── scripts/
│   ├── deploy.sh                  # Deployment script
│   └── local-test.sh              # Local testing script
├── events/                        # Sample event payloads
└── INFRASTRUCTURE.md              # Detailed infrastructure docs

```

## Configuration

### Environment Variables

Key environment variables (set automatically by SAM):
- `AWS_REGION`: AWS region
- `SESSIONS_TABLE`: DynamoDB Sessions table name
- `CACHE_TABLE`: DynamoDB Cache table name
- `AUDIO_BUCKET`: S3 Audio bucket name

### Application Configuration

See `src/main/resources/application.yml` for detailed configuration options.

## API Endpoints

### POST /query
Process text-based queries

**Request:**
```json
{
  "userId": "string",
  "sessionId": "string",
  "message": "string",
  "preferredLanguage": "string (optional)"
}
```

**Response:**
```json
{
  "responseText": "string",
  "detectedLanguage": "string",
  "intent": "string",
  "audioUrl": "string (optional)"
}
```

### POST /audio
Process voice-based queries

**Request:**
```json
{
  "userId": "string",
  "sessionId": "string",
  "audioData": "base64 string",
  "audioFormat": "string (wav|mp3|m4a)"
}
```

## Monitoring

### CloudWatch Logs
- View logs in AWS Console: CloudWatch > Log Groups
- Log groups: `/aws/lambda/language-assistant-*`

### CloudWatch Metrics
- Lambda invocations, errors, duration
- DynamoDB read/write capacity
- Custom application metrics

### Alarms
- Error rate > 5%
- P95 latency > 3 seconds
- Free Tier usage > 80%

## Cost Optimization

The application is designed to stay within AWS Free Tier limits:
- Lambda: 512MB memory, 30s timeout, 10 concurrent executions
- DynamoDB: On-demand billing with TTL cleanup
- S3: 1-day lifecycle policy for audio files
- Response caching to reduce Bedrock calls
- Standard Polly voices for high-volume requests

## Security

- API Gateway with API key authentication
- TLS encryption for all API communications
- Server-side encryption for DynamoDB and S3
- IAM roles with least privilege access
- No PII storage beyond session duration (30 minutes)
- Automatic data cleanup via TTL

## Documentation

- [Infrastructure Documentation](INFRASTRUCTURE.md) - Detailed infrastructure setup and configuration
- [Requirements](.kiro/specs/african-language-assistant/requirements.md) - Functional requirements
- [Design](.kiro/specs/african-language-assistant/design.md) - Technical design document
- [Tasks](.kiro/specs/african-language-assistant/tasks.md) - Implementation tasks

## License

MIT License

## Support

For issues and questions, please open an issue in the repository.
