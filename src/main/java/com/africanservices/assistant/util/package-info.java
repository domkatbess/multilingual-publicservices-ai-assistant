/**
 * Utility classes for the African Language Assistant.
 * 
 * <p>This package contains utility classes for structured logging, metrics, and other
 * cross-cutting concerns.</p>
 * 
 * <h2>StructuredLogger Usage Example</h2>
 * <pre>{@code
 * public class MyLambdaFunction {
 *     private static final StructuredLogger logger = new StructuredLogger(MyLambdaFunction.class);
 *     
 *     public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input) {
 *         // Generate and set correlation ID for the entire request
 *         String correlationId = StructuredLogger.generateCorrelationId();
 *         StructuredLogger.setCorrelationId(correlationId);
 *         
 *         long startTime = System.currentTimeMillis();
 *         String requestId = input.getRequestContext().getRequestId();
 *         
 *         try {
 *             // Log incoming request
 *             logger.logRequest(requestId, userId, sessionId, "Processing text query");
 *             
 *             // Process request...
 *             String response = processRequest(input);
 *             
 *             // Log successful response
 *             long duration = System.currentTimeMillis() - startTime;
 *             logger.logResponse(requestId, 200, "Request processed successfully", 
 *                              duration, "ha", "Health");
 *             
 *             return createResponse(200, response);
 *             
 *         } catch (Exception e) {
 *             // Log error
 *             logger.logError(requestId, "PROCESSING_ERROR", "Failed to process request", e);
 *             return createErrorResponse(500, "Internal server error");
 *             
 *         } finally {
 *             // Clean up MDC context
 *             StructuredLogger.clearContext();
 *         }
 *     }
 * }
 * }</pre>
 * 
 * <p>The structured logger automatically includes correlation IDs in all log entries,
 * making it easy to trace requests across different Lambda functions and AWS services.</p>
 * 
 * @see com.africanservices.assistant.util.StructuredLogger
 */
package com.africanservices.assistant.util;
