# Security and Rate Limiting Implementation

## Overview

This document summarizes the security and rate limiting features implemented for the African Language Assistant as part of Task 15.

## Task 15.1: API Gateway Security

### API Key Authentication

**Implementation**: `template.yaml`

- Enabled API key requirement for all endpoints via `Auth.ApiKeyRequired: true`
- Created usage plan with API key association
- Added API key resource (`LanguageAssistantApiKey`) for testing
- API key ID exported in CloudFormation outputs for easy access

**Validates**: Requirement 10.1 - API Gateway SHALL validate that requests include a valid API key

### Rate Limiting

**Implementation**: `template.yaml`

- Configured throttle settings in usage plan:
  - Rate limit: 10 requests per second
  - Burst limit: 20 requests
  - Monthly quota: 10,000 requests
- Custom gateway response for throttled requests (429 status)
- Returns appropriate error message in JSON format

**Validates**: Requirement 10.4 - API Gateway SHALL reject requests exceeding rate limits with 429 status

### CORS Configuration

**Implementation**: `template.yaml`

- Configured CORS headers for API Gateway:
  - Allow Methods: POST, GET, OPTIONS
  - Allow Headers: Content-Type, X-Amz-Date, Authorization, X-Api-Key, X-Amz-Security-Token
  - Allow Origin: * (configurable for production)
  - Max Age: 600 seconds
- Added CORS headers to all gateway responses (throttled, unauthorized, bad request)

**Validates**: Requirement 15.5 - System SHALL include CORS headers to allow web client access

### Request Validation

**Implementation**: `template.yaml`

- Defined request models for input validation:
  - `TextInputModel`: Validates text query structure
    - Required fields: userId, sessionId, message
    - Message length: 1-999 characters
    - Optional preferredLanguage with enum validation
  - `AudioInputModel`: Validates audio query structure
    - Required fields: userId, sessionId, audioData, audioFormat
    - Audio format enum: wav, mp3, m4a
- Enabled request body validation on API endpoints
- Custom gateway response for invalid requests (400 status)

**Validates**: Requirement 1.2 - System SHALL validate that the message is not empty and contains fewer than 1000 characters

### Gateway Responses

**Implementation**: `template.yaml`

Custom error responses configured for:

1. **THROTTLED (429)**:
   ```json
   {
     "error": true,
     "errorCode": "RATE_LIMIT_EXCEEDED",
     "message": "Too many requests. Please try again later.",
     "fallbackMessage": "Too many requests. Please try again later.",
     "retryable": true
   }
   ```

2. **UNAUTHORIZED (401)**:
   ```json
   {
     "error": true,
     "errorCode": "UNAUTHORIZED",
     "message": "Invalid or missing API key.",
     "fallbackMessage": "Invalid or missing API key.",
     "retryable": false
   }
   ```

3. **BAD_REQUEST_BODY (400)**:
   ```json
   {
     "error": true,
     "errorCode": "INVALID_REQUEST",
     "message": "Invalid request body.",
     "fallbackMessage": "Invalid request body.",
     "retryable": false
   }
   ```

## Task 15.2: Bedrock Guardrails

### Guardrail Configuration

**Implementation**: `template.yaml`

Created `BedrockGuardrail` resource with comprehensive content filtering:

#### Content Policy Filters

- **Sexual Content**: HIGH strength (input and output)
- **Violence**: HIGH strength (input and output)
- **Hate Speech**: HIGH strength (input and output)
- **Insults**: MEDIUM strength (input and output)
- **Misconduct**: MEDIUM strength (input and output)
- **Prompt Attacks**: HIGH strength (input only)

#### Topic Policy

Blocked topics:
1. **Medical Diagnosis**: Prevents medical advice, diagnosis, or prescriptions
2. **Legal Advice**: Prevents specific legal advice or representation

#### Word Policy

Blocked sensitive terms:
- "password"
- "credit card"
- "social security"
- Profanity (via managed word list)

#### Blocked Messages

- **Input blocked**: "I cannot process this request as it contains inappropriate content. Please rephrase your question."
- **Output blocked**: "I cannot provide a response to this request. Please ask a different question about public services."

**Validates**: Requirement 10.3 - System SHALL apply Bedrock guardrails to prevent generation of harmful content

### Environment Configuration

**Implementation**: `template.yaml`, `application.yml`

- Added guardrail ID and version to Lambda environment variables
- Configured in `application.yml` with environment variable fallbacks
- Guardrail automatically applied to all Bedrock invocations

### Service Updates

**Implementation**: `BedrockService.java`

- Added guardrail configuration parameters to constructor
- Updated documentation to explain guardrail protection
- Log message indicates guardrail protection is active
- Tests verify guardrail behavior

### Testing

**Implementation**: `BedrockServiceTest.java`, `GUARDRAILS.md`

- Added unit tests for guardrail configuration
- Tests verify service works with and without guardrails
- Tests simulate guardrail blocking harmful content
- Documentation includes manual testing procedures

## Additional Security Features

### Data Encryption

**Implementation**: `template.yaml`

- DynamoDB tables: Server-side encryption enabled (SSE)
- S3 bucket: AES256 encryption enabled
- API Gateway: TLS encryption for all communications

**Validates**: 
- Requirement 10.5 - System SHALL encrypt data at rest in DynamoDB and S3
- Requirement 10.6 - System SHALL use TLS encryption for all API communications

### Lambda Concurrency Limits

**Implementation**: `template.yaml`

- Reserved concurrent executions: 10 per function
- Prevents runaway costs and resource exhaustion
- Aligns with free tier optimization

**Validates**: Requirement 11.2 - System SHALL set concurrent execution limit to 10 functions

### S3 Security

**Implementation**: `template.yaml`

- Public access blocked on all levels
- Lifecycle policy: Delete files after 1 day
- CORS configured for controlled access
- Encryption at rest enabled

**Validates**: Requirement 11.4 - System SHALL configure S3 lifecycle policies to delete files after 1 day

## Deployment

### CloudFormation Outputs

The following outputs are available after deployment:

- `ApiEndpoint`: API Gateway endpoint URL
- `ApiKeyId`: API key ID for authentication
- `BedrockGuardrailId`: Guardrail identifier
- `BedrockGuardrailVersion`: Guardrail version

### Usage

1. Deploy the stack:
   ```bash
   sam build
   sam deploy --guided
   ```

2. Get API key value:
   ```bash
   aws apigateway get-api-key --api-key <ApiKeyId> --include-value
   ```

3. Make authenticated request:
   ```bash
   curl -X POST <ApiEndpoint>/query \
     -H "x-api-key: <api-key-value>" \
     -H "Content-Type: application/json" \
     -d '{"userId":"user1","sessionId":"session1","message":"Where is the health clinic?"}'
   ```

## Testing Security Features

### API Key Validation

Test without API key (should fail with 401):
```bash
curl -X POST <ApiEndpoint>/query \
  -H "Content-Type: application/json" \
  -d '{"userId":"user1","sessionId":"session1","message":"test"}'
```

### Rate Limiting

Test by sending >10 requests per second (should return 429):
```bash
for i in {1..15}; do
  curl -X POST <ApiEndpoint>/query \
    -H "x-api-key: <api-key>" \
    -H "Content-Type: application/json" \
    -d '{"userId":"user1","sessionId":"session1","message":"test"}' &
done
```

### Request Validation

Test with invalid message length (should fail with 400):
```bash
curl -X POST <ApiEndpoint>/query \
  -H "x-api-key: <api-key>" \
  -H "Content-Type: application/json" \
  -d '{"userId":"user1","sessionId":"session1","message":""}'
```

### Guardrails

Test with harmful content (should be blocked):
```bash
curl -X POST <ApiEndpoint>/query \
  -H "x-api-key: <api-key>" \
  -H "Content-Type: application/json" \
  -d '{"userId":"user1","sessionId":"session1","message":"How to harm someone"}'
```

## Monitoring

### CloudWatch Metrics

Monitor security events:
- API Gateway 4xx errors (authentication/validation failures)
- API Gateway 5xx errors (server errors)
- Lambda errors (including guardrail blocks)
- Throttled requests count

### CloudWatch Logs

Security-related log entries:
- API key validation failures
- Rate limit exceeded events
- Request validation errors
- Guardrail blocks (in Lambda logs)

### Alarms

Configured alarms:
- Error rate > 5%
- Latency > 3 seconds (p95)

## Requirements Validation

This implementation validates the following requirements:

- ✅ 1.2: Input validation (message length)
- ✅ 10.1: API key authentication
- ✅ 10.3: Bedrock guardrails for harmful content
- ✅ 10.4: Rate limiting with 429 status
- ✅ 10.5: Data encryption at rest
- ✅ 10.6: TLS encryption in transit
- ✅ 11.2: Lambda concurrency limits
- ✅ 11.4: S3 lifecycle policies
- ✅ 15.5: CORS headers for web clients

## Files Modified

1. `template.yaml` - Infrastructure configuration
2. `application.yml` - Application configuration
3. `pom.xml` - AWS SDK version update
4. `BedrockService.java` - Guardrail documentation
5. `BedrockServiceTest.java` - Guardrail tests
6. `GUARDRAILS.md` - Guardrail documentation (new)
7. `SECURITY_IMPLEMENTATION.md` - This file (new)

## Next Steps

For production deployment:
1. Configure specific CORS origins (not wildcard)
2. Set up API key rotation policy
3. Configure CloudWatch alarms with SNS notifications
4. Review and adjust rate limits based on usage patterns
5. Test guardrails with multilingual content
6. Set up WAF rules for additional protection
7. Enable API Gateway access logging
