package com.africanservices.assistant.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MetricsEmitter.
 * Validates CloudWatch metrics emission with language and service category dimensions.
 * 
 * Requirements: 12.3, 12.4
 */
class MetricsEmitterTest {

    private CloudWatchClient mockCloudWatchClient;
    private MetricsEmitter metricsEmitter;

    @BeforeEach
    void setUp() {
        mockCloudWatchClient = mock(CloudWatchClient.class);
        metricsEmitter = new MetricsEmitter(mockCloudWatchClient);
    }

    @Test
    void testEmitRequestCount_WithLanguageAndServiceCategory() {
        // Arrange
        String language = "ha";
        String serviceCategory = "Government";
        
        // Act
        metricsEmitter.emitRequestCount(language, serviceCategory);
        
        // Assert
        ArgumentCaptor<PutMetricDataRequest> requestCaptor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(mockCloudWatchClient).putMetricData(requestCaptor.capture());
        
        PutMetricDataRequest request = requestCaptor.getValue();
        assertEquals("AfricanLanguageAssistant", request.namespace());
        assertEquals(1, request.metricData().size());
        
        MetricDatum metric = request.metricData().get(0);
        assertEquals("RequestCount", metric.metricName());
        assertEquals(1.0, metric.value());
        assertEquals(StandardUnit.COUNT, metric.unit());
        assertEquals(2, metric.dimensions().size());
        
        assertTrue(metric.dimensions().stream()
                .anyMatch(d -> d.name().equals("Language") && d.value().equals(language)));
        assertTrue(metric.dimensions().stream()
                .anyMatch(d -> d.name().equals("ServiceCategory") && d.value().equals(serviceCategory)));
    }

    @Test
    void testEmitLatency_WithLanguageAndServiceCategory() {
        // Arrange
        double latencyMs = 250.5;
        String language = "yo";
        String serviceCategory = "Health";
        
        // Act
        metricsEmitter.emitLatency(latencyMs, language, serviceCategory);
        
        // Assert
        ArgumentCaptor<PutMetricDataRequest> requestCaptor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(mockCloudWatchClient).putMetricData(requestCaptor.capture());
        
        PutMetricDataRequest request = requestCaptor.getValue();
        MetricDatum metric = request.metricData().get(0);
        
        assertEquals("Latency", metric.metricName());
        assertEquals(latencyMs, metric.value());
        assertEquals(StandardUnit.MILLISECONDS, metric.unit());
        assertEquals(2, metric.dimensions().size());
    }

    @Test
    void testEmitErrorCount_WithLanguageAndServiceCategory() {
        // Arrange
        String language = "ig";
        String serviceCategory = "Education";
        
        // Act
        metricsEmitter.emitErrorCount(language, serviceCategory);
        
        // Assert
        ArgumentCaptor<PutMetricDataRequest> requestCaptor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(mockCloudWatchClient).putMetricData(requestCaptor.capture());
        
        PutMetricDataRequest request = requestCaptor.getValue();
        MetricDatum metric = request.metricData().get(0);
        
        assertEquals("ErrorCount", metric.metricName());
        assertEquals(1.0, metric.value());
        assertEquals(StandardUnit.COUNT, metric.unit());
    }

    @Test
    void testEmitCacheHit_WithLanguageOnly() {
        // Arrange
        String language = "ff";
        
        // Act
        metricsEmitter.emitCacheHit(language);
        
        // Assert
        ArgumentCaptor<PutMetricDataRequest> requestCaptor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(mockCloudWatchClient).putMetricData(requestCaptor.capture());
        
        PutMetricDataRequest request = requestCaptor.getValue();
        MetricDatum metric = request.metricData().get(0);
        
        assertEquals("CacheHit", metric.metricName());
        assertEquals(1.0, metric.value());
        assertEquals(StandardUnit.COUNT, metric.unit());
        assertEquals(1, metric.dimensions().size());
        assertEquals("Language", metric.dimensions().get(0).name());
        assertEquals(language, metric.dimensions().get(0).value());
    }

    @Test
    void testEmitCacheMiss_WithLanguageOnly() {
        // Arrange
        String language = "en";
        
        // Act
        metricsEmitter.emitCacheMiss(language);
        
        // Assert
        ArgumentCaptor<PutMetricDataRequest> requestCaptor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(mockCloudWatchClient).putMetricData(requestCaptor.capture());
        
        PutMetricDataRequest request = requestCaptor.getValue();
        MetricDatum metric = request.metricData().get(0);
        
        assertEquals("CacheMiss", metric.metricName());
        assertEquals(1, metric.dimensions().size());
    }

    @Test
    void testEmitMetric_WithoutDimensions() {
        // Arrange
        String metricName = "CustomMetric";
        double value = 42.0;
        
        // Act
        metricsEmitter.emitMetric(metricName, value, StandardUnit.COUNT);
        
        // Assert
        ArgumentCaptor<PutMetricDataRequest> requestCaptor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(mockCloudWatchClient).putMetricData(requestCaptor.capture());
        
        PutMetricDataRequest request = requestCaptor.getValue();
        MetricDatum metric = request.metricData().get(0);
        
        assertEquals(metricName, metric.metricName());
        assertEquals(value, metric.value());
        assertEquals(StandardUnit.COUNT, metric.unit());
        assertEquals(0, metric.dimensions().size());
    }

    @Test
    void testEmitMetric_WithNullLanguage() {
        // Arrange
        String serviceCategory = "Emergency";
        
        // Act
        metricsEmitter.emitMetric("TestMetric", 1.0, StandardUnit.COUNT, null, serviceCategory);
        
        // Assert
        ArgumentCaptor<PutMetricDataRequest> requestCaptor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(mockCloudWatchClient).putMetricData(requestCaptor.capture());
        
        PutMetricDataRequest request = requestCaptor.getValue();
        MetricDatum metric = request.metricData().get(0);
        
        assertEquals(1, metric.dimensions().size());
        assertEquals("ServiceCategory", metric.dimensions().get(0).name());
    }

    @Test
    void testEmitMetric_WithEmptyLanguage() {
        // Arrange
        String serviceCategory = "Government";
        
        // Act
        metricsEmitter.emitMetric("TestMetric", 1.0, StandardUnit.COUNT, "", serviceCategory);
        
        // Assert
        ArgumentCaptor<PutMetricDataRequest> requestCaptor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(mockCloudWatchClient).putMetricData(requestCaptor.capture());
        
        PutMetricDataRequest request = requestCaptor.getValue();
        MetricDatum metric = request.metricData().get(0);
        
        assertEquals(1, metric.dimensions().size());
        assertEquals("ServiceCategory", metric.dimensions().get(0).name());
    }

    @Test
    void testEmitMetrics_BatchEmission() {
        // Arrange
        MetricDatum metric1 = metricsEmitter.createMetricDatum(
                "Metric1", 1.0, StandardUnit.COUNT, "ha", "Government");
        MetricDatum metric2 = metricsEmitter.createMetricDatum(
                "Metric2", 2.0, StandardUnit.COUNT, "yo", "Health");
        List<MetricDatum> metrics = Arrays.asList(metric1, metric2);
        
        // Act
        metricsEmitter.emitMetrics(metrics);
        
        // Assert
        ArgumentCaptor<PutMetricDataRequest> requestCaptor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(mockCloudWatchClient).putMetricData(requestCaptor.capture());
        
        PutMetricDataRequest request = requestCaptor.getValue();
        assertEquals("AfricanLanguageAssistant", request.namespace());
        assertEquals(2, request.metricData().size());
    }

    @Test
    void testCreateMetricDatum_WithAllDimensions() {
        // Arrange
        String metricName = "TestMetric";
        double value = 100.0;
        String language = "ha";
        String serviceCategory = "Education";
        
        // Act
        MetricDatum metric = metricsEmitter.createMetricDatum(
                metricName, value, StandardUnit.COUNT, language, serviceCategory);
        
        // Assert
        assertNotNull(metric);
        assertEquals(metricName, metric.metricName());
        assertEquals(value, metric.value());
        assertEquals(StandardUnit.COUNT, metric.unit());
        assertEquals(2, metric.dimensions().size());
        assertNotNull(metric.timestamp());
    }

    @Test
    void testCreateMetricDatum_WithNoDimensions() {
        // Arrange
        String metricName = "TestMetric";
        double value = 50.0;
        
        // Act
        MetricDatum metric = metricsEmitter.createMetricDatum(
                metricName, value, StandardUnit.COUNT, null, null);
        
        // Assert
        assertNotNull(metric);
        assertEquals(metricName, metric.metricName());
        assertEquals(value, metric.value());
        assertEquals(0, metric.dimensions().size());
    }

    @Test
    void testEmitMetric_HandlesCloudWatchException() {
        // Arrange
        when(mockCloudWatchClient.putMetricData(any(PutMetricDataRequest.class)))
                .thenThrow(new RuntimeException("CloudWatch error"));
        
        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> 
                metricsEmitter.emitRequestCount("ha", "Government"));
        
        verify(mockCloudWatchClient).putMetricData(any(PutMetricDataRequest.class));
    }

    @Test
    void testEmitMetrics_HandlesCloudWatchException() {
        // Arrange
        when(mockCloudWatchClient.putMetricData(any(PutMetricDataRequest.class)))
                .thenThrow(new RuntimeException("CloudWatch error"));
        
        MetricDatum metric = metricsEmitter.createMetricDatum(
                "TestMetric", 1.0, StandardUnit.COUNT, "ha", "Government");
        
        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> 
                metricsEmitter.emitMetrics(Arrays.asList(metric)));
        
        verify(mockCloudWatchClient).putMetricData(any(PutMetricDataRequest.class));
    }

    @Test
    void testMetricTimestamp_IsPresent() {
        // Arrange & Act
        metricsEmitter.emitRequestCount("ha", "Government");
        
        // Assert
        ArgumentCaptor<PutMetricDataRequest> requestCaptor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(mockCloudWatchClient).putMetricData(requestCaptor.capture());
        
        MetricDatum metric = requestCaptor.getValue().metricData().get(0);
        assertNotNull(metric.timestamp());
    }

    @Test
    void testNamespace_IsCorrect() {
        // Arrange & Act
        metricsEmitter.emitRequestCount("ha", "Government");
        
        // Assert
        ArgumentCaptor<PutMetricDataRequest> requestCaptor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(mockCloudWatchClient).putMetricData(requestCaptor.capture());
        
        assertEquals("AfricanLanguageAssistant", requestCaptor.getValue().namespace());
    }

    @Test
    void testDimensionNames_AreCorrect() {
        // Arrange & Act
        metricsEmitter.emitRequestCount("ha", "Government");
        
        // Assert
        ArgumentCaptor<PutMetricDataRequest> requestCaptor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(mockCloudWatchClient).putMetricData(requestCaptor.capture());
        
        MetricDatum metric = requestCaptor.getValue().metricData().get(0);
        List<Dimension> dimensions = metric.dimensions();
        
        assertTrue(dimensions.stream().anyMatch(d -> d.name().equals("Language")));
        assertTrue(dimensions.stream().anyMatch(d -> d.name().equals("ServiceCategory")));
    }
}
