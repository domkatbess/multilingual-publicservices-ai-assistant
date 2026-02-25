package com.africanservices.assistant.service;

import com.africanservices.assistant.util.ErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Service for handling graceful degradation when AWS services are unavailable.
 * Provides fallback responses and alternative processing paths.
 */
@Service
public class GracefulDegradationService {

    private static final Logger logger = LoggerFactory.getLogger(GracefulDegradationService.class);
    private static final String DEFAULT_LANGUAGE = "en";

    private final CacheManagerService cacheManagerService;
    private final ErrorHandler errorHandler;

    // Fallback responses by service category and language
    private static final Map<String, Map<String, String>> FALLBACK_RESPONSES = new HashMap<>();

    static {
        // English fallback responses
        Map<String, String> englishFallbacks = new HashMap<>();
        englishFallbacks.put("Government", 
            "I'm currently unable to access the AI service. For government services, please visit your local government office or check their official website for information about documents, procedures, and eligibility requirements.");
        englishFallbacks.put("Health", 
            "I'm currently unable to access the AI service. For health services, please contact your nearest health facility or call the health hotline. In case of emergency, dial emergency services immediately.");
        englishFallbacks.put("Education", 
            "I'm currently unable to access the AI service. For education services, please visit your local education office or school for information about enrollment, scholarships, and educational resources.");
        englishFallbacks.put("Emergency", 
            "I'm currently unable to access the AI service. For emergencies, please dial your local emergency number immediately. Stay calm and follow emergency procedures.");
        FALLBACK_RESPONSES.put("en", englishFallbacks);

        // Hausa fallback responses
        Map<String, String> hausaFallbacks = new HashMap<>();
        hausaFallbacks.put("Government", 
            "A halin yanzu ba zan iya samun damar sabis na AI ba. Don ayyukan gwamnati, da fatan za a ziyarci ofishin gwamnatin ku na gida ko duba gidan yanar gizon su don bayani game da takardun shaida, hanyoyin aiki, da bukatun cancanta.");
        hausaFallbacks.put("Health", 
            "A halin yanzu ba zan iya samun damar sabis na AI ba. Don ayyukan lafiya, da fatan za a tuntubi cibiyar kiwon lafiya mafi kusa ko kira layin waya na lafiya. Idan akwai gaggawa, kira sabis na gaggawa nan da nan.");
        hausaFallbacks.put("Education", 
            "A halin yanzu ba zan iya samun damar sabis na AI ba. Don ayyukan ilimi, da fatan za a ziyarci ofishin ilimi na gida ko makaranta don bayani game da rajista, tallafin karatu, da kayan aikin ilimi.");
        hausaFallbacks.put("Emergency", 
            "A halin yanzu ba zan iya samun damar sabis na AI ba. Don gaggawa, da fatan za a kira lambar gaggawa na gida nan da nan. Ku natsu kuma ku bi hanyoyin gaggawa.");
        FALLBACK_RESPONSES.put("ha", hausaFallbacks);

        // Yoruba fallback responses
        Map<String, String> yorubaFallbacks = new HashMap<>();
        yorubaFallbacks.put("Government", 
            "Mo ko le wọle si iṣẹ AI lọwọlọwọ. Fun awọn iṣẹ ijọba, jọwọ ṣabẹwo si ọfiisi ijọba agbegbe rẹ tabi ṣayẹwo oju opo wẹẹbu osise wọn fun alaye nipa awọn iwe, ilana, ati awọn ibeere tọ.");
        yorubaFallbacks.put("Health", 
            "Mo ko le wọle si iṣẹ AI lọwọlọwọ. Fun awọn iṣẹ ilera, jọwọ kan si ile-iwosan ti o sunmọ julọ tabi pe laini igbesi aye ilera. Ni ipo pajawiri, pe awọn iṣẹ pajawiri lẹsẹkẹsẹ.");
        yorubaFallbacks.put("Education", 
            "Mo ko le wọle si iṣẹ AI lọwọlọwọ. Fun awọn iṣẹ ẹkọ, jọwọ ṣabẹwo si ọfiisi ẹkọ agbegbe rẹ tabi ile-iwe fun alaye nipa iforukọsilẹ, awọn ẹbun ẹkọ, ati awọn ohun elo ẹkọ.");
        yorubaFallbacks.put("Emergency", 
            "Mo ko le wọle si iṣẹ AI lọwọlọwọ. Fun awọn pajawiri, jọwọ pe nọmba pajawiri agbegbe rẹ lẹsẹkẹsẹ. Duro ni ifọkanbalẹ ki o tẹle awọn ilana pajawiri.");
        FALLBACK_RESPONSES.put("yo", yorubaFallbacks);

        // Igbo fallback responses
        Map<String, String> igboFallbacks = new HashMap<>();
        igboFallbacks.put("Government", 
            "Enweghị m ike ịnweta ọrụ AI ugbu a. Maka ọrụ gọọmentị, biko gaa n'ọfịs gọọmentị mpaghara gị ma ọ bụ lelee webụsaịtị ha maka ozi gbasara akwụkwọ, usoro, na ihe achọrọ.");
        igboFallbacks.put("Health", 
            "Enweghị m ike ịnweta ọrụ AI ugbu a. Maka ọrụ ahụike, biko kpọtụrụ ụlọ ọgwụ dị nso ma ọ bụ kpọọ ahịrị ekwentị ahụike. Ọ bụrụ na ọ bụ ihe mberede, kpọọ ọrụ mberede ozugbo.");
        igboFallbacks.put("Education", 
            "Enweghị m ike ịnweta ọrụ AI ugbu a. Maka ọrụ agụmakwụkwọ, biko gaa n'ọfịs agụmakwụkwọ mpaghara gị ma ọ bụ ụlọ akwụkwọ maka ozi gbasara ndebanye aha, agụmakwụkwọ, na akụrụngwa agụmakwụkwọ.");
        igboFallbacks.put("Emergency", 
            "Enweghị m ike ịnweta ọrụ AI ugbu a. Maka ihe mberede, biko kpọọ nọmba mberede mpaghara gị ozugbo. Nọrọ jụụ ma soro usoro mberede.");
        FALLBACK_RESPONSES.put("ig", igboFallbacks);

        // Fulfulde fallback responses
        Map<String, String> fulfuldeFallbacks = new HashMap<>();
        fulfuldeFallbacks.put("Government", 
            "Miɗo waawaa heɓde kuutoragol AI jooni. Ngam kuutorɗe dowla, tiiɗno yillo ofis dowla leydi maa walla ƴeew lowre makko internet ngam humpito baɗte konngol, toɗɗe, e ceeɓndam.");
        fulfuldeFallbacks.put("Health", 
            "Miɗo waawaa heɓde kuutoragol AI jooni. Ngam kuutorɗe cellal, tiiɗno jokkito nokkuuji cellal ɓurɗi walla noddu laaɓi cellal. Si wonti ko caɗeele, noddu kuutorɗe caɗeele ɗoo sahaa.");
        fulfuldeFallbacks.put("Education", 
            "Miɗo waawaa heɓde kuutoragol AI jooni. Ngam kuutorɗe jaŋde, tiiɗno yillo ofis jaŋde leydi maa walla duɗal ngam humpito winnditagol, ballal jaŋde, e kuutorɗe jaŋde.");
        fulfuldeFallbacks.put("Emergency", 
            "Miɗo waawaa heɓde kuutoragol AI jooni. Ngam caɗeele, tiiɗno noddu limoore caɗeele leydi maa ɗoo sahaa. Hoolno e nder e toɗɗe caɗeele.");
        FALLBACK_RESPONSES.put("ff", fulfuldeFallbacks);
    }

    @Autowired
    public GracefulDegradationService(
            CacheManagerService cacheManagerService,
            ErrorHandler errorHandler) {
        this.cacheManagerService = cacheManagerService;
        this.errorHandler = errorHandler;
    }

    /**
     * Handles Bedrock unavailability by attempting to use cached response or fallback.
     *
     * @param query The user's query
     * @param language The detected or preferred language
     * @param serviceCategory The service category
     * @return Response text (cached or fallback)
     */
    public String handleBedrockUnavailable(String query, String language, String serviceCategory) {
        logger.warn("Bedrock unavailable, attempting graceful degradation for query in language: {}", language);

        // Try to get cached response first
        Optional<String> cachedResponse = attemptCacheRetrieval(query, language);
        if (cachedResponse.isPresent()) {
            logger.info("Using cached response for Bedrock unavailability");
            return cachedResponse.get();
        }

        // Fall back to predefined response
        logger.info("Using fallback response for service category: {}", serviceCategory);
        return getFallbackResponse(serviceCategory, language);
    }

    /**
     * Handles Polly unavailability by returning text-only response.
     * This is not an error condition - the system continues with text response.
     *
     * @param responseText The text response to return
     * @param language The user's language
     * @return The same response text (no audio URL will be included)
     */
    public String handlePollyUnavailable(String responseText, String language) {
        logger.warn("Polly unavailable, returning text-only response in language: {}", language);
        // Simply return the text response without audio
        // The caller should not include audioUrl in the response
        return responseText;
    }

    /**
     * Handles Transcribe failure by providing guidance to user.
     *
     * @param language The user's language
     * @return Message prompting user to type instead
     */
    public String handleTranscribeFailure(String language) {
        logger.warn("Transcribe failed, prompting user to type instead");
        
        return switch (language) {
            case "ha" -> "Ba za a iya gano maganar ku ba. Da fatan za a rubuta tambayar ku a maimakon haka.";
            case "yo" -> "Ko le ṣe idanimọ ọrọ rẹ. Jọwọ tẹ ibeere rẹ dipo.";
            case "ig" -> "Enweghị ike ịghọta okwu gị. Biko dee ajụjụ gị kama.";
            case "ff" -> "Horiima ɓeydude haala maa. Tiiɗno winndito naamndal maa e ɓooyɗo.";
            default -> "Unable to recognize your speech. Please type your question instead.";
        };
    }

    /**
     * Handles language detection failure by defaulting to English.
     *
     * @return Default language code (English)
     */
    public String handleLanguageDetectionFailure() {
        logger.warn("Language detection failed, defaulting to English");
        return DEFAULT_LANGUAGE;
    }

    /**
     * Gets a prompt message asking user to specify their language.
     *
     * @return Message in English asking for language preference
     */
    public String getLanguageSelectionPrompt() {
        return "I couldn't detect your language. Please specify your preferred language: " +
               "Hausa (ha), Yoruba (yo), Igbo (ig), Fulfulde (ff), or English (en).";
    }

    /**
     * Attempts to retrieve a cached response.
     *
     * @param query The user's query
     * @param language The language
     * @return Optional containing cached response if found
     */
    private Optional<String> attemptCacheRetrieval(String query, String language) {
        try {
            return cacheManagerService.getCachedResponse(query, language);
        } catch (Exception e) {
            logger.error("Failed to retrieve cached response during degradation: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Gets a fallback response for a service category and language.
     *
     * @param serviceCategory The service category
     * @param language The language
     * @return Fallback response text
     */
    private String getFallbackResponse(String serviceCategory, String language) {
        Map<String, String> languageFallbacks = FALLBACK_RESPONSES.get(language);
        
        if (languageFallbacks == null) {
            // Fall back to English if language not supported
            languageFallbacks = FALLBACK_RESPONSES.get(DEFAULT_LANGUAGE);
        }

        String fallback = languageFallbacks.get(serviceCategory);
        
        if (fallback == null) {
            // Generic fallback if category not found
            return languageFallbacks.getOrDefault("Government", 
                "Service temporarily unavailable. Please try again later or contact your local office.");
        }

        return fallback;
    }

    /**
     * Checks if a service degradation should be logged as an error or warning.
     *
     * @param serviceType The type of service that failed
     * @return true if it's a critical failure, false if it's acceptable degradation
     */
    public boolean isCriticalFailure(String serviceType) {
        // Bedrock and Transcribe failures are critical
        // Polly failures are acceptable (text-only response)
        return "Bedrock".equals(serviceType) || "Transcribe".equals(serviceType);
    }
}
