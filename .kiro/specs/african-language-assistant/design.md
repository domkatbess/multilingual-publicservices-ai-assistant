# Technical Design: African Language Assistant

## Overview

A serverless multilingual AI assistant that enables access to public services (government, health, education, emergency) in low-resource African languages including Hausa, Yoruba, Igbo, and Fulfulde. The system removes language and literacy barriers by allowing users to interact naturally via speech or text in their native language.

## Architecture

### High-Level Components

```
User Interface (Web/Mobile)
    ↓
API Gateway
    ↓
Lambda Functions (Java)
    ↓
┌─────────────┬──────────────┬─────────────┐
│   Bedrock   │    Polly     │  Transcribe │
│   (AI/ML)   │    (TTS)     │    (STT)    │
└─────────────┴──────────────┴─────────────┘
    ↓
DynamoDB (Session/Cache)
```

### AWS Services

- **API Gateway**: REST API endpoints for client requests
- **Lambda**: Serverless compute for request processing (Java 17 runtime with Spring Boot)
- **Bedrock**: AI model inference for language understanding and generation
- **Polly**: Text-to-speech synthesis for audio responses
- **Transcribe**: Speech-to-text conversion for voice input
- **DynamoDB**: Session management and response caching
- **S3**: Audio file storage (temporary)
- **CloudWatch**: Logging and monitoring

### Technology Stack

- **Java 21**: Latest LTS version with virtual threads and improved performance
- **Spring Boot 3.x**: Framework for dependency injection and configuration
- **Spring Cloud Function**: Adapter for AWS Lambda integration
- **AWS SDK for Java v2**: Modern async SDK for AWS services
- **Maven**: Build and dependency management

## Core Workflows

### 1. Text Input Flow

```
User Text Input → API Gateway → Lambda (Input Handler)
    ↓
Language Detection (Bedrock)
    ↓
Intent Classification (Bedrock)
    ↓
Response Generation (Bedrock)
    ↓
[Optional] TTS Conversion (Polly)
    ↓
Response to User
```

### 2. Voice Input Flow

```
User Audio → API Gateway → Lambda (Audio Handler)
    ↓
Upload to S3 (temporary)
    ↓
Transcribe (STT)
    ↓
[Continue with Text Input Flow]
```

## Component Design

### Lambda Functions

#### 1. InputHandlerFunction
**Purpose**: Process text-based user queries

**Implementation**: Spring Boot Lambda function using Spring Cloud Function

**Responsibilities**:
- Validate input using Spring Validation
- Detect language via injected LanguageDetectionService
- Route to appropriate service handler
- Manage session context via SessionService

**Spring Components**:
- `@Component` function bean implementing `Function<InputRequest, APIGatewayProxyResponseEvent>`
- `@Autowired` services for language detection, response generation, session management
- `@Value` for configuration properties

**Input**:
```json
{
  "userId": "string",
  "sessionId": "string",
  "message": "string",
  "preferredLanguage": "string (optional)"
}
```

**Output**:
```json
{
  "responseText": "string",
  "detectedLanguage": "string",
  "intent": "string",
  "audioUrl": "string (optional)"
}
```

#### 2. AudioHandlerFunction
**Purpose**: Process voice-based user queries

**Implementation**: Spring Boot Lambda function using Spring Cloud Function

**Responsibilities**:
- Accept audio upload
- Store temporarily in S3 via S3Service
- Invoke Transcribe via TranscriptionService
- Forward transcribed text to InputHandler

**Spring Components**:
- `@Component` function bean implementing `Function<AudioRequest, APIGatewayProxyResponseEvent>`
- `@Autowired` S3Service, TranscriptionService, InputHandlerFunction
- `@ConfigurationProperties` for AWS service configuration

**Input**:
```json
{
  "userId": "string",
  "sessionId": "string",
  "audioData": "base64 string",
  "audioFormat": "string"
}
```

#### 3. TranscriptionProcessorFunction
**Purpose**: Handle Transcribe completion events

**Responsibilities**:
- Retrieve transcription results
- Clean up temporary S3 objects
- Forward to InputHandler

#### 4. ResponseGeneratorService
**Purpose**: Generate contextual responses using Bedrock

**Implementation**: Spring `@Service` component

**Responsibilities**:
- Call Bedrock with user query and context
- Apply service-specific prompts (health, education, etc.)
- Format response for target language
- Cache common responses in DynamoDB via CacheService

**Spring Components**:
- `@Service` annotation for service layer
- `@Autowired` BedrockClient, CacheService, PromptTemplateManager
- `@Cacheable` for Spring Cache abstraction integration

**Bedrock Integration**:
- Model: Amazon Titan or Claude (multilingual support)
- Prompt engineering for African language context
- Temperature: 0.7 for balanced creativity/accuracy

#### 5. TextToSpeechService
**Purpose**: Convert text responses to speech

**Implementation**: Spring `@Service` component

**Responsibilities**:
- Call Polly for supported languages
- Store audio in S3 with expiring URLs via S3Service
- Return audio URL to client

**Spring Components**:
- `@Service` annotation for service layer
- `@Autowired` PollyClient, S3Service
- `@Value` for voice configuration per language

**Polly Configuration**:
- Voice selection based on language
- Neural engine for better quality
- SSML support for pronunciation

### DynamoDB Schema

#### Sessions Table
```
{
  "sessionId": "string (PK)",
  "userId": "string (GSI)",
  "language": "string",
  "context": "map",
  "lastActivity": "number (TTL)",
  "conversationHistory": "list"
}
```

#### ResponseCache Table
```
{
  "queryHash": "string (PK)",
  "language": "string (SK)",
  "response": "string",
  "ttl": "number",
  "hitCount": "number"
}
```

## Language Support Strategy

### Supported Languages (Phase 1)
- Hausa (ha)
- Yoruba (yo)
- Igbo (ig)
- Fulfulde/Fulani (ff)
- English (en) - fallback

### Language Detection
- Use Bedrock's multilingual capabilities
- Fallback to character set analysis
- User preference override

### Translation Approach
- Direct generation in target language (preferred)
- English pivot for unsupported language pairs
- Maintain cultural context and idioms

## Service Categories

### 1. Government Services
- Document requirements
- Application procedures
- Office locations and hours
- Eligibility criteria

### 2. Health Services
- Symptom assessment (non-diagnostic)
- Facility locations
- Vaccination schedules
- Health education

### 3. Education Services
- School enrollment
- Scholarship information
- Educational resources
- Literacy programs

### 4. Emergency Services
- Emergency contact numbers
- First aid guidance
- Disaster preparedness
- Reporting procedures

## Prompt Engineering

### System Prompt Template
```
You are a helpful assistant for public services in {LANGUAGE}.
You provide accurate, culturally appropriate information about {SERVICE_CATEGORY}.
Keep responses clear, concise, and actionable.
If you don't know something, direct users to appropriate authorities.
Always be respectful and patient.
```

### Context Management
- Maintain last 5 conversation turns
- Include detected intent and service category
- Preserve user preferences (language, location)

## Free Tier Optimization

### Lambda
- Memory: 512MB (balance performance/cost)
- Timeout: 30 seconds
- Concurrent executions: 10 (within free tier)

### Bedrock
- Use on-demand pricing
- Implement response caching
- Batch similar requests when possible

### Polly
- Cache common phrases
- Generate audio only when requested
- Use standard voices (cheaper than neural for high volume)

### Transcribe
- Use standard transcription (not real-time)
- Batch processing where possible
- Clean up S3 files immediately

### DynamoDB
- On-demand capacity mode
- TTL for automatic cleanup
- Efficient query patterns with GSIs

### S3
- Lifecycle policy: delete after 1 day
- Use standard storage class
- Minimal metadata

## Error Handling

### Graceful Degradation
1. Bedrock unavailable → Use cached responses or fallback messages
2. Polly unavailable → Return text-only response
3. Transcribe fails → Prompt user to type instead
4. Language detection fails → Default to English with language selection prompt

### Error Response Format
```json
{
  "error": true,
  "errorCode": "string",
  "message": "string (in user's language)",
  "fallbackMessage": "string (in English)",
  "retryable": boolean
}
```

## Security Considerations

### Authentication
- API Gateway with API keys
- Rate limiting per user/IP
- CORS configuration for web clients

### Data Privacy
- No PII storage beyond session duration
- Encrypted data in transit (TLS)
- Encrypted data at rest (DynamoDB, S3)
- Session TTL: 30 minutes

### Content Safety
- Input validation and sanitization
- Bedrock guardrails for harmful content
- Audit logging for compliance

## Monitoring and Observability

### CloudWatch Metrics
- Request count by language
- Response latency by service
- Error rates by component
- Cache hit rates
- Cost per request

### CloudWatch Logs
- Structured JSON logging
- Request/response correlation IDs
- Language detection accuracy
- User satisfaction indicators

### Alarms
- Error rate > 5%
- Latency > 3 seconds (p95)
- Free tier threshold warnings

## Deployment Strategy

### Infrastructure as Code
- AWS SAM or CDK for deployment
- Separate dev/prod environments
- Environment-specific configuration

### CI/CD Pipeline
- Automated testing (unit, integration)
- Gradual rollout with canary deployments
- Automated rollback on errors

## Testing Strategy

### Unit Tests
- Lambda function logic
- Input validation
- Error handling
- Response formatting

### Integration Tests
- End-to-end workflows
- AWS service mocking
- Multi-language scenarios
- Cache behavior

### Load Tests
- Free tier limit validation
- Concurrent user handling
- Response time under load

## Future Enhancements

### Phase 2 Features
- Additional languages (Swahili, Amharic, Zulu)
- Real-time voice conversation
- SMS/WhatsApp integration
- Offline capability with progressive web app
- Community feedback loop for accuracy

### Scalability Path
- Multi-region deployment
- CDN for static assets
- Advanced caching strategies
- Custom ML models for better language support

## Success Metrics

### Technical Metrics
- Response time < 2 seconds (p95)
- Availability > 99.5%
- Error rate < 1%
- Free tier compliance > 95%

### User Metrics
- Language detection accuracy > 95%
- User satisfaction score > 4/5
- Task completion rate > 80%
- Return user rate > 40%

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Input Validation Boundary

*For any* text input, the system should reject empty strings and strings with 1000 or more characters, while accepting all strings with length between 1 and 999 characters.

**Validates: Requirements 1.2**

### Property 2: Language Detection Accuracy

*For any* text query in a supported language (Hausa, Yoruba, Igbo, Fulfulde, English), the language detection should correctly identify the language.

**Validates: Requirements 1.3, 3.1**

### Property 3: Service Category Classification

*For any* valid query, the classification result should be exactly one of the four service categories: Government, Health, Education, or Emergency.

**Validates: Requirements 1.4, 8.1**

### Property 4: Response Language Matching

*For any* query in a supported language, the generated response should be in the same language as the input query.

**Validates: Requirements 1.5, 4.4**

### Property 5: Audio Format Acceptance

*For any* audio input, the system should accept WAV, MP3, and M4A formats and reject all other formats.

**Validates: Requirements 2.1**

### Property 6: Audio Storage Uniqueness

*For any* audio file stored in S3, the file identifier should be unique across all stored files.

**Validates: Requirements 2.2**

### Property 7: Transcription Cleanup

*For any* completed transcription, the temporary audio file should no longer exist in S3 after the transcription text is retrieved.

**Validates: Requirements 2.5, 13.3**

### Property 8: Language Support Identification

*For any* detected language, the system should correctly identify whether it is one of the five supported languages or requires fallback handling.

**Validates: Requirements 3.2**

### Property 9: Session Language Consistency

*For any* session where a user specifies a preferred language, all responses within that session should be in the specified language.

**Validates: Requirements 3.4**

### Property 10: Session Language Persistence

*For any* language detection or specification event, reading the session record should return the same language value that was stored.

**Validates: Requirements 3.5**

### Property 11: Service-Specific Prompt Selection

*For any* service category, the system prompt should contain keywords and guidance appropriate to that category (documents/procedures for Government, facilities/education for Health, enrollment/scholarships for Education, contact/first-aid for Emergency).

**Validates: Requirements 4.2**

### Property 12: Voice Selection by Language

*For any* text-to-speech conversion, the selected Polly voice should be appropriate for the response language.

**Validates: Requirements 5.2**

### Property 13: Audio URL Expiration

*For any* generated audio file, the presigned URL should have an expiration time of exactly 1 hour from generation.

**Validates: Requirements 5.4, 13.4**

### Property 14: Neural Engine for Supported Languages

*For any* text-to-speech request in a supported language where neural voices are available, the system should use the neural engine unless high-volume cost optimization is triggered.

**Validates: Requirements 5.5**

### Property 15: Session Creation Uniqueness

*For any* new conversation, the created session should have a unique session identifier that doesn't conflict with any existing session.

**Validates: Requirements 6.1**

### Property 16: Session Context Window

*For any* session with more than 5 conversation turns, only the most recent 5 turns should be included in the context for response generation.

**Validates: Requirements 6.3**

### Property 17: Session Update Incrementality

*For any* response generation, the session conversation history should grow by exactly one turn containing the query and response.

**Validates: Requirements 6.4**

### Property 18: Session TTL Update

*For any* session update, the TTL should be set to exactly 30 minutes from the current time.

**Validates: Requirements 6.5**

### Property 19: Cache Key Determinism

*For any* query and language combination, computing the hash multiple times should always produce the same cache key.

**Validates: Requirements 7.1**

### Property 20: Cache Hit Behavior

*For any* query with a valid cached response, the system should return the cached response without invoking Bedrock, and the cache hit count should increase by 1.

**Validates: Requirements 7.3, 7.4**

### Property 21: Cache Storage with TTL

*For any* newly generated response, the cache entry should be stored with a TTL of exactly 24 hours from creation time.

**Validates: Requirements 7.5**

### Property 22: Error Response Completeness

*For any* error condition, the error response should contain all required fields: error flag (true), errorCode, message in user's language, fallbackMessage in English, and retryable boolean.

**Validates: Requirements 9.5, 9.6, 15.2**

### Property 23: Input Sanitization

*For any* text input containing potentially harmful content (script tags, SQL injection patterns, command injection patterns), the sanitized input should have those patterns removed or escaped.

**Validates: Requirements 10.2**

### Property 24: Voice Type Cost Optimization

*For any* high-volume text-to-speech request, the system should select standard voices over neural voices to optimize costs while staying within free tier limits.

**Validates: Requirements 11.5**

### Property 25: Structured Logging Format

*For any* log entry, the log should be valid JSON and include a correlation identifier that links related operations.

**Validates: Requirements 12.1, 12.2**

### Property 26: Metrics Emission with Dimensions

*For any* completed request, the emitted CloudWatch metrics should include dimensions for language and service category.

**Validates: Requirements 12.3, 12.4**

### Property 27: Session PII Exclusion

*For any* session record, the stored data should not contain personally identifiable information patterns (email addresses, phone numbers, national IDs, physical addresses).

**Validates: Requirements 13.1**

### Property 28: Cache Privacy

*For any* cached response entry, the cached data should not contain user-specific information (user IDs, session IDs, personal details).

**Validates: Requirements 13.5**

### Property 29: Session Terminology Consistency

*For any* session with multiple turns, technical terms and service names should be used consistently across all responses in that session.

**Validates: Requirements 14.5**

### Property 30: Success Response Structure

*For any* successful query, the JSON response should contain all required fields: responseText, detectedLanguage, intent, and optionally audioUrl with expiration timestamp when audio is generated.

**Validates: Requirements 15.1, 15.3**

### Property 31: HTTP Status Code Correctness

*For any* response, the HTTP status code should be 200 for success, 4xx for client errors (invalid input, authentication failure), and 5xx for server errors (service unavailable, internal errors).

**Validates: Requirements 15.4**

### Property 32: CORS Header Presence

*For any* API response, the response headers should include appropriate CORS headers to allow web client access.

**Validates: Requirements 15.5**

## Glossary

- **STT**: Speech-to-Text
- **TTS**: Text-to-Speech
- **GSI**: Global Secondary Index (DynamoDB)
- **TTL**: Time To Live
- **SSML**: Speech Synthesis Markup Language
- **PII**: Personally Identifiable Information
