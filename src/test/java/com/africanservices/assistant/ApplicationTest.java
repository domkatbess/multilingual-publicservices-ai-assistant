package com.africanservices.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.transcribe.TranscribeClient;

/**
 * Basic application context test to verify Spring Boot configuration.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationTest {

    @MockBean
    private DynamoDbClient dynamoDbClient;

    @MockBean
    private S3Client s3Client;

    @MockBean
    private S3Presigner s3Presigner;

    @MockBean
    private BedrockRuntimeClient bedrockRuntimeClient;

    @MockBean
    private PollyClient pollyClient;

    @MockBean
    private TranscribeClient transcribeClient;

    @MockBean
    private CloudWatchClient cloudWatchClient;

    @Test
    void contextLoads() {
        // Verifies that the Spring application context loads successfully
    }
}
