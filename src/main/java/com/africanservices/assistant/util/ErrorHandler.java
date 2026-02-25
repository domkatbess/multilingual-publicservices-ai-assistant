package com.africanservices.assistant.util;

import com.africanservices.assistant.model.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility for handling errors and building error responses.
 * Provides error code mapping, localized messages, and retry determination.
 */
@Component
public class ErrorHandler {

    private static final Logger logger = LoggerFactory.getLogger(ErrorHandler.class);

    // Error codes
    public static final String ERROR_INVALID_INPUT = "INVALID_INPUT";
    public static final String ERROR_LANGUAGE_DETECTION_FAILED = "LANGUAGE_DETECTION_FAILED";
    public static final String ERROR_BEDROCK_UNAVAILABLE = "BEDROCK_UNAVAILABLE";
    public static final String ERROR_POLLY_UNAVAILABLE = "POLLY_UNAVAILABLE";
    public static final String ERROR_TRANSCRIBE_FAILED = "TRANSCRIBE_FAILED";
    public static final String ERROR_AUDIO_FORMAT_INVALID = "AUDIO_FORMAT_INVALID";
    public static final String ERROR_SESSION_NOT_FOUND = "SESSION_NOT_FOUND";
    public static final String ERROR_CACHE_FAILURE = "CACHE_FAILURE";
    public static final String ERROR_S3_FAILURE = "S3_FAILURE";
    public static final String ERROR_INTERNAL = "INTERNAL_ERROR";
    public static final String ERROR_RATE_LIMIT = "RATE_LIMIT_EXCEEDED";
    public static final String ERROR_AUTHENTICATION = "AUTHENTICATION_FAILED";

    // Fallback messages in English
    private static final Map<String, String> FALLBACK_MESSAGES = new HashMap<>();
    
    static {
        FALLBACK_MESSAGES.put(ERROR_INVALID_INPUT, "Invalid input provided. Please check your request and try again.");
        FALLBACK_MESSAGES.put(ERROR_LANGUAGE_DETECTION_FAILED, "Unable to detect language. Please specify your preferred language.");
        FALLBACK_MESSAGES.put(ERROR_BEDROCK_UNAVAILABLE, "AI service is temporarily unavailable. Please try again later.");
        FALLBACK_MESSAGES.put(ERROR_POLLY_UNAVAILABLE, "Text-to-speech service is unavailable. Response provided as text only.");
        FALLBACK_MESSAGES.put(ERROR_TRANSCRIBE_FAILED, "Speech recognition failed. Please type your question instead.");
        FALLBACK_MESSAGES.put(ERROR_AUDIO_FORMAT_INVALID, "Invalid audio format. Please use WAV, MP3, or M4A format.");
        FALLBACK_MESSAGES.put(ERROR_SESSION_NOT_FOUND, "Session not found or expired. Please start a new conversation.");
        FALLBACK_MESSAGES.put(ERROR_CACHE_FAILURE, "Cache service error. Your request will be processed normally.");
        FALLBACK_MESSAGES.put(ERROR_S3_FAILURE, "Storage service error. Please try again.");
        FALLBACK_MESSAGES.put(ERROR_INTERNAL, "An internal error occurred. Please try again later.");
        FALLBACK_MESSAGES.put(ERROR_RATE_LIMIT, "Too many requests. Please wait a moment and try again.");
        FALLBACK_MESSAGES.put(ERROR_AUTHENTICATION, "Authentication failed. Please check your credentials.");
    }

    // Localized messages by language
    private static final Map<String, Map<String, String>> LOCALIZED_MESSAGES = new HashMap<>();
    
    static {
        // Hausa messages
        Map<String, String> hausaMessages = new HashMap<>();
        hausaMessages.put(ERROR_INVALID_INPUT, "Bayanin da aka bayar ba daidai ba ne. Da fatan za a duba bukatarka kuma a sake gwadawa.");
        hausaMessages.put(ERROR_LANGUAGE_DETECTION_FAILED, "Ba za a iya gano yaren ba. Da fatan za a bayyana yaren da kake so.");
        hausaMessages.put(ERROR_BEDROCK_UNAVAILABLE, "Sabis na AI ba ya samuwa a yanzu. Da fatan za a sake gwadawa daga baya.");
        hausaMessages.put(ERROR_POLLY_UNAVAILABLE, "Sabis na magana ba ya samuwa. An bayar da amsa a matsayin rubutu kawai.");
        hausaMessages.put(ERROR_TRANSCRIBE_FAILED, "Gane magana ya kasa. Da fatan za a rubuta tambayarka a maimakon haka.");
        hausaMessages.put(ERROR_AUDIO_FORMAT_INVALID, "Tsarin sauti ba daidai ba ne. Da fatan za a yi amfani da WAV, MP3, ko M4A.");
        LOCALIZED_MESSAGES.put("ha", hausaMessages);

        // Yoruba messages
        Map<String, String> yorubaMessages = new HashMap<>();
        yorubaMessages.put(ERROR_INVALID_INPUT, "Alaye ti ko tọ. Jọwọ ṣayẹwo ibeere rẹ ki o gbiyanju lẹẹkansi.");
        yorubaMessages.put(ERROR_LANGUAGE_DETECTION_FAILED, "Ko le ṣe idanimọ ede. Jọwọ sọ ede ti o fẹ.");
        yorubaMessages.put(ERROR_BEDROCK_UNAVAILABLE, "Iṣẹ AI ko wa lọwọlọwọ. Jọwọ gbiyanju lẹẹkansi nigbamii.");
        yorubaMessages.put(ERROR_POLLY_UNAVAILABLE, "Iṣẹ ọrọ-si-ohun ko wa. Idahun fun ni bi ọrọ nikan.");
        yorubaMessages.put(ERROR_TRANSCRIBE_FAILED, "Idanimọ ọrọ kuna. Jọwọ tẹ ibeere rẹ dipo.");
        yorubaMessages.put(ERROR_AUDIO_FORMAT_INVALID, "Ọna ohun ko tọ. Jọwọ lo WAV, MP3, tabi M4A.");
        LOCALIZED_MESSAGES.put("yo", yorubaMessages);

        // Igbo messages
        Map<String, String> igboMessages = new HashMap<>();
        igboMessages.put(ERROR_INVALID_INPUT, "Ozi ezighi ezi enyere. Biko lelee arịrịọ gị wee nwaa ọzọ.");
        igboMessages.put(ERROR_LANGUAGE_DETECTION_FAILED, "Enweghị ike ịchọpụta asụsụ. Biko kọwaa asụsụ ị chọrọ.");
        igboMessages.put(ERROR_BEDROCK_UNAVAILABLE, "Ọrụ AI adịghị ugbu a. Biko nwaa ọzọ mgbe e mesịrị.");
        igboMessages.put(ERROR_POLLY_UNAVAILABLE, "Ọrụ okwu-na-olu adịghị. Enyere azịza dị ka ederede naanị.");
        igboMessages.put(ERROR_TRANSCRIBE_FAILED, "Nchọpụta okwu dara ada. Biko dee ajụjụ gị kama.");
        igboMessages.put(ERROR_AUDIO_FORMAT_INVALID, "Ụdị olu ezighi ezi. Biko jiri WAV, MP3, ma ọ bụ M4A.");
        LOCALIZED_MESSAGES.put("ig", igboMessages);

        // Fulfulde messages
        Map<String, String> fulfuldeMessages = new HashMap<>();
        fulfuldeMessages.put(ERROR_INVALID_INPUT, "Humpito waɗi ko moƴƴaani. Tiiɗno ƴeew naamndal maa tee fuɗɗito gooto.");
        fulfuldeMessages.put(ERROR_LANGUAGE_DETECTION_FAILED, "Horiima ɓeydude ɗemngal. Tiiɗno haalan ɗemngal mo a yiɗi.");
        fulfuldeMessages.put(ERROR_BEDROCK_UNAVAILABLE, "Kuutoragol AI woodaani jooni. Tiiɗno fuɗɗito gooto caggal nde.");
        fulfuldeMessages.put(ERROR_POLLY_UNAVAILABLE, "Kuutoragol haala-to-daande woodaani. Jaabawol njaltini ko e binndol tan.");
        fulfuldeMessages.put(ERROR_TRANSCRIBE_FAILED, "Ɓeydagol haala gasii. Tiiɗno winndito naamndal maa e ɓooyɗo.");
        fulfuldeMessages.put(ERROR_AUDIO_FORMAT_INVALID, "Mbaydi ojoo moƴƴaani. Tiiɗno huutoro WAV, MP3, walla M4A.");
        LOCALIZED_MESSAGES.put("ff", fulfuldeMessages);
    }

    /**
     * Builds an error response with localized message.
     *
     * @param errorCode The error code
     * @param language The user's language (for localized message)
     * @return ErrorResponse object
     */
    public ErrorResponse buildErrorResponse(String errorCode, String language) {
        String localizedMessage = getLocalizedMessage(errorCode, language);
        String fallbackMessage = getFallbackMessage(errorCode);
        boolean retryable = isRetryable(errorCode);

        logger.error("Building error response: code={}, language={}, retryable={}", 
                     errorCode, language, retryable);

        return new ErrorResponse(errorCode, localizedMessage, fallbackMessage, retryable);
    }

    /**
     * Builds an error response with custom message.
     *
     * @param errorCode The error code
     * @param customMessage Custom message in user's language
     * @param language The user's language
     * @return ErrorResponse object
     */
    public ErrorResponse buildErrorResponse(String errorCode, String customMessage, String language) {
        String fallbackMessage = getFallbackMessage(errorCode);
        boolean retryable = isRetryable(errorCode);

        logger.error("Building error response with custom message: code={}, language={}, retryable={}", 
                     errorCode, language, retryable);

        return new ErrorResponse(errorCode, customMessage, fallbackMessage, retryable);
    }

    /**
     * Builds an error response from an exception.
     *
     * @param exception The exception that occurred
     * @param language The user's language
     * @return ErrorResponse object
     */
    public ErrorResponse buildErrorResponseFromException(Exception exception, String language) {
        String errorCode = mapExceptionToErrorCode(exception);
        return buildErrorResponse(errorCode, language);
    }

    /**
     * Gets a localized message for an error code.
     *
     * @param errorCode The error code
     * @param language The target language
     * @return Localized message, or fallback message if not available
     */
    private String getLocalizedMessage(String errorCode, String language) {
        if (language == null || language.equals("en")) {
            return getFallbackMessage(errorCode);
        }

        Map<String, String> languageMessages = LOCALIZED_MESSAGES.get(language);
        if (languageMessages != null && languageMessages.containsKey(errorCode)) {
            return languageMessages.get(errorCode);
        }

        // Fall back to English if localization not available
        return getFallbackMessage(errorCode);
    }

    /**
     * Gets the fallback (English) message for an error code.
     *
     * @param errorCode The error code
     * @return Fallback message
     */
    private String getFallbackMessage(String errorCode) {
        return FALLBACK_MESSAGES.getOrDefault(errorCode, 
                "An error occurred. Please try again later.");
    }

    /**
     * Determines if an error is retryable.
     *
     * @param errorCode The error code
     * @return true if the error is retryable, false otherwise
     */
    private boolean isRetryable(String errorCode) {
        return switch (errorCode) {
            case ERROR_BEDROCK_UNAVAILABLE,
                 ERROR_TRANSCRIBE_FAILED,
                 ERROR_CACHE_FAILURE,
                 ERROR_S3_FAILURE,
                 ERROR_INTERNAL,
                 ERROR_RATE_LIMIT -> true;
            case ERROR_INVALID_INPUT,
                 ERROR_AUDIO_FORMAT_INVALID,
                 ERROR_SESSION_NOT_FOUND,
                 ERROR_AUTHENTICATION,
                 ERROR_LANGUAGE_DETECTION_FAILED,
                 ERROR_POLLY_UNAVAILABLE -> false; // Polly unavailable is graceful degradation, not retryable
            default -> false;
        };
    }

    /**
     * Maps an exception to an error code.
     *
     * @param exception The exception
     * @return Error code
     */
    private String mapExceptionToErrorCode(Exception exception) {
        String exceptionName = exception.getClass().getSimpleName();
        String message = exception.getMessage() != null ? exception.getMessage().toLowerCase() : "";

        // Map based on exception type or message content
        if (exceptionName.contains("Bedrock") || message.contains("bedrock")) {
            return ERROR_BEDROCK_UNAVAILABLE;
        } else if (exceptionName.contains("Polly") || message.contains("polly")) {
            return ERROR_POLLY_UNAVAILABLE;
        } else if (exceptionName.contains("Transcribe") || message.contains("transcribe")) {
            return ERROR_TRANSCRIBE_FAILED;
        } else if (exceptionName.contains("S3") || message.contains("s3")) {
            return ERROR_S3_FAILURE;
        } else if (message.contains("rate limit") || message.contains("throttl")) {
            return ERROR_RATE_LIMIT;
        } else if (message.contains("auth")) {
            return ERROR_AUTHENTICATION;
        } else {
            return ERROR_INTERNAL;
        }
    }
}
