package com.africanservices.assistant.util;

import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * CloudWatch metrics utility for emitting custom metrics with dimensions.
 * Supports language and service category dimensions for tracking system performance.
 * 
 * Requirements: 12.3, 12.4
 */
public class MetricsEmitter {
    
    private static final String NAMESPACE = "AfricanLanguageAssistant";
    
    private final CloudWatchClient cloudWatchClient;
    private final StructuredLogger logger;
    
    /**
     * Creates a metrics emitter with the specified CloudWatch client.
     * 
     * @param cloudWatchClient The CloudWatch client to use for emitting metrics
     */
    public MetricsEmitter(CloudWatchClient cloudWatchClient) {
        this.cloudWatchClient = cloudWatchClient;
        this.logger = new StructuredLogger(MetricsEmitter.class);
    }
    
    /**
     * Emits a request count metric with language and service category dimensions.
     * 
     * @param language The detected or preferred language
     * @param serviceCategory The service category (Government, Health, Education, Emergency)
     */
    public void emitRequestCount(String language, String serviceCategory) {
        emitMetric("RequestCount", 1.0, StandardUnit.COUNT, language, serviceCategory);
    }
    
    /**
     * Emits a latency metric with language and service category dimensions.
     * 
     * @param latencyMs The request latency in milliseconds
     * @param language The detected or preferred language
     * @param serviceCategory The service category
     */
    public void emitLatency(double latencyMs, String language, String serviceCategory) {
        emitMetric("Latency", latencyMs, StandardUnit.MILLISECONDS, language, serviceCategory);
    }
    
    /**
     * Emits an error count metric with language and service category dimensions.
     * 
     * @param language The detected or preferred language
     * @param serviceCategory The service category
     */
    public void emitErrorCount(String language, String serviceCategory) {
        emitMetric("ErrorCount", 1.0, StandardUnit.COUNT, language, serviceCategory);
    }
    
    /**
     * Emits a cache hit metric with language dimension.
     * 
     * @param language The detected or preferred language
     */
    public void emitCacheHit(String language) {
        emitMetric("CacheHit", 1.0, StandardUnit.COUNT, language, null);
    }
    
    /**
     * Emits a cache miss metric with language dimension.
     * 
     * @param language The detected or preferred language
     */
    public void emitCacheMiss(String language) {
        emitMetric("CacheMiss", 1.0, StandardUnit.COUNT, language, null);
    }
    
    /**
     * Emits a custom metric with language and service category dimensions.
     * 
     * @param metricName The name of the metric
     * @param value The metric value
     * @param unit The unit of measurement
     * @param language The detected or preferred language (optional)
     * @param serviceCategory The service category (optional)
     */
    public void emitMetric(String metricName, double value, StandardUnit unit, 
                          String language, String serviceCategory) {
        try {
            List<Dimension> dimensions = new ArrayList<>();
            
            if (language != null && !language.isEmpty()) {
                dimensions.add(Dimension.builder()
                        .name("Language")
                        .value(language)
                        .build());
            }
            
            if (serviceCategory != null && !serviceCategory.isEmpty()) {
                dimensions.add(Dimension.builder()
                        .name("ServiceCategory")
                        .value(serviceCategory)
                        .build());
            }
            
            MetricDatum metricDatum = MetricDatum.builder()
                    .metricName(metricName)
                    .value(value)
                    .unit(unit)
                    .timestamp(Instant.now())
                    .dimensions(dimensions)
                    .build();
            
            PutMetricDataRequest request = PutMetricDataRequest.builder()
                    .namespace(NAMESPACE)
                    .metricData(metricDatum)
                    .build();
            
            cloudWatchClient.putMetricData(request);
            
            logger.debug("Emitted metric: {} = {} {} (language={}, serviceCategory={})", 
                    metricName, value, unit, language, serviceCategory);
            
        } catch (Exception e) {
            logger.warn("Failed to emit metric {}: {}", metricName, e.getMessage());
        }
    }
    
    /**
     * Emits a custom metric without dimensions.
     * 
     * @param metricName The name of the metric
     * @param value The metric value
     * @param unit The unit of measurement
     */
    public void emitMetric(String metricName, double value, StandardUnit unit) {
        emitMetric(metricName, value, unit, null, null);
    }
    
    /**
     * Emits multiple metrics in a single request for better performance.
     * 
     * @param metricData List of metric data to emit
     */
    public void emitMetrics(List<MetricDatum> metricData) {
        try {
            PutMetricDataRequest request = PutMetricDataRequest.builder()
                    .namespace(NAMESPACE)
                    .metricData(metricData)
                    .build();
            
            cloudWatchClient.putMetricData(request);
            
            logger.debug("Emitted {} metrics", metricData.size());
            
        } catch (Exception e) {
            logger.warn("Failed to emit metrics: {}", e.getMessage());
        }
    }
    
    /**
     * Creates a metric datum builder with language and service category dimensions.
     * Useful for batch metric emission.
     * 
     * @param metricName The name of the metric
     * @param value The metric value
     * @param unit The unit of measurement
     * @param language The detected or preferred language (optional)
     * @param serviceCategory The service category (optional)
     * @return A MetricDatum object ready to be emitted
     */
    public MetricDatum createMetricDatum(String metricName, double value, StandardUnit unit,
                                         String language, String serviceCategory) {
        List<Dimension> dimensions = new ArrayList<>();
        
        if (language != null && !language.isEmpty()) {
            dimensions.add(Dimension.builder()
                    .name("Language")
                    .value(language)
                    .build());
        }
        
        if (serviceCategory != null && !serviceCategory.isEmpty()) {
            dimensions.add(Dimension.builder()
                    .name("ServiceCategory")
                    .value(serviceCategory)
                    .build());
        }
        
        return MetricDatum.builder()
                .metricName(metricName)
                .value(value)
                .unit(unit)
                .timestamp(Instant.now())
                .dimensions(dimensions)
                .build();
    }
}
