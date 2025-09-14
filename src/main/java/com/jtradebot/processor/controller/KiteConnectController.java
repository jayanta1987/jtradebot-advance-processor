package com.jtradebot.processor.controller;

import com.jtradebot.processor.config.AwsSecretHandler;
import com.jtradebot.processor.connector.KiteSetupHandler;
import com.jtradebot.processor.repository.InstrumentRepository;
import com.jtradebot.processor.service.TickSetupService;
import com.jtradebot.processor.service.scheduler.InstrumentFreshnessCheckerService;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;

@CrossOrigin(origins = {"http://localhost:5173", "https://jtradebot.com", "https://www.jtradebot.com"})
@RestController
@RequestMapping("/connection")
@RequiredArgsConstructor
@Slf4j
public class KiteConnectController {

    private final KiteConnect kiteConnect;
    private final KiteSetupHandler kiteSetupHandler;
    private final AwsSecretHandler awsSecretHandler;
    private final TickSetupService tickSetupService;
    private final InstrumentRepository instrumentRepository;
    private final InstrumentFreshnessCheckerService instrumentFreshnessCheckerService;

    // Cache to track processed request tokens with timestamps to prevent duplicate processing
    private final Map<String, Instant> processedRequestTokens = new ConcurrentHashMap<>();
    
    // Cache expiration time in minutes (tokens expire after 5 minutes)
    private static final int CACHE_EXPIRATION_MINUTES = 5;

    @Value("${aws.kite.api-secret}")
    private String kiteApiSecret;

    @Value("${aws.kite.secret-name}")
    private String kiteConnectAwsSecretName;
    
    /**
     * Check if a request token is still valid (not expired)
     */
    private boolean isRequestTokenValid(String requestToken) {
        Instant cachedTime = processedRequestTokens.get(requestToken);
        if (cachedTime == null) {
            return false; // Token not in cache
        }
        
        Instant expirationTime = cachedTime.plusSeconds(CACHE_EXPIRATION_MINUTES * 60);
        boolean isValid = Instant.now().isBefore(expirationTime);
        
        // Remove expired token from cache
        if (!isValid) {
            processedRequestTokens.remove(requestToken);
            log.debug("Removed expired request token from cache: {}", requestToken);
        }
        
        return isValid;
    }


    @GetMapping("/check")
    public Map<String, String> checkConnection() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        return response;
    }

    @GetMapping("/login")
    public Map<String, String> login() {
        String loginUrl = kiteConnect.getLoginURL();
        Map<String, String> response = new HashMap<>();
        response.put("loginUrl", loginUrl);
        return response;
    }

    @PostMapping(value = "/generateAccessToken")
    public ResponseEntity<Map<String, String>> generateAccessToken(@RequestBody Map<String, String> request) {

        String status = request.get("status");
        String requestToken = request.get("request_token");
        log.info("Received POST /generateAccessToken with status: {} and request_token: {} ", status, requestToken);

        Map<String, String> response = new HashMap<>();
        String errorMessage = "Failed to obtain access token";
        try {
            if (requestToken == null || requestToken.isEmpty()) {
                response.put("message", errorMessage + " : Missing request_token");
                log.error(errorMessage + " : Missing request_token");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            if (!"success".equals(status)) {
                response.put("message", errorMessage + " : Status is not success");
                log.error(errorMessage + " : Status is not success");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            // Check if this request_token has already been processed recently
            if (isRequestTokenValid(requestToken)) {
                log.warn("Duplicate request detected for request_token: {}. Returning cached response.", requestToken);
                response.put("message", "Access token already processed for this request.");
                return ResponseEntity.ok(response);
            }

            // Mark this request_token as being processed with current timestamp
            processedRequestTokens.put(requestToken, Instant.now());

            User user = kiteConnect.generateSession(requestToken,
                    awsSecretHandler.getSecret(kiteConnectAwsSecretName, kiteApiSecret));
            tickSetupService.saveDefaultTradeConfig(user);

            response.put("message", "Access token successfully obtained.");
            log.info("Access token successfully obtained");
            return ResponseEntity.ok(response);
            
        } catch (KiteException e) {
            // Remove from cache on error so it can be retried
            processedRequestTokens.remove(requestToken);
            response.put("message", errorMessage + " : " + e.getMessage());
            log.error(errorMessage, e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        } catch (IOException e) {
            // Remove from cache on error so it can be retried
            processedRequestTokens.remove(requestToken);
            response.put("message", errorMessage + " : " + e.getMessage());
            log.error(errorMessage, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/init")
    public Map<String, String> init() {
        Map<String, String> response = new HashMap<>();
        try {
            kiteSetupHandler.init();
            response.put("message", "KiteMarketDataHandler initialized successfully");
        } catch (KiteException | IOException e) {
            response.put("message", "Failed to initialize KiteMarketDataHandler: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/generateInstruments")
    public Map<String, String> generateInstruments() {
        Map<String, String> response = new HashMap<>();
        try {
            kiteSetupHandler.generateInstruments();
            response.put("message", "Instruments generated successfully");
        } catch (KiteException | IOException e) {
            response.put("message", "Failed to generate instruments: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/subscribeToken")
    public Map<String, String> subscribeToken(@RequestBody Map<String, String> request) {
        Map<String, String> response = new HashMap<>();
        try {
            String token = request.get("token");
            kiteSetupHandler.subscribeToken(token);
            response.put("message", "Token subscribed successfully");
        } catch (Exception e) {
            response.put("message", "Failed to subscribe token: " + e.getMessage());
        }
        return response;
    }

    @GetMapping("/checkOptionInstruments")
    public Map<String, Object> checkOptionInstruments() {
        Map<String, Object> response = new HashMap<>();
        try {
            // Find Nifty option instruments (sorted by expiry ascending)
            List<com.jtradebot.processor.repository.document.Instrument> niftyOptions =
                    instrumentRepository.findByNameAndInstrumentTypeAndSegmentOrderByExpiryAsc("NIFTY", "OPT", "NFO-OPT");

            // Get unique expiry dates sorted by actual date (not string)
            List<String> uniqueExpiries = niftyOptions.stream()
                    .map(com.jtradebot.processor.repository.document.Instrument::getExpiry)
                    .distinct()
                    .sorted((expiry1, expiry2) -> {
                        try {
                            java.time.LocalDate date1 = java.time.LocalDate.parse(expiry1, java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
                            java.time.LocalDate date2 = java.time.LocalDate.parse(expiry2, java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
                            return date1.compareTo(date2);
                        } catch (Exception e) {
                            return expiry1.compareTo(expiry2); // fallback to string comparison
                        }
                    })
                    .collect(java.util.stream.Collectors.toList());

            response.put("totalNiftyOptions", niftyOptions.size());
            response.put("uniqueExpiries", uniqueExpiries);
            response.put("nextAvailableExpiry", uniqueExpiries.isEmpty() ? "None" : uniqueExpiries.get(0));

            // Show first 10 options from next available expiry
            List<Map<String, Object>> sampleOptions = niftyOptions.stream()
                    .limit(10)
                    .map(instrument -> {
                        Map<String, Object> option = new HashMap<>();
                        option.put("tradingSymbol", instrument.getTradingSymbol());
                        option.put("instrumentToken", instrument.getInstrumentToken());
                        option.put("strike", instrument.getStrike());
                        option.put("expiry", instrument.getExpiry());
                        return option;
                    })
                    .collect(java.util.stream.Collectors.toList());

            response.put("sampleOptions", sampleOptions);
            response.put("message", "Option instruments check completed");

        } catch (Exception e) {
            response.put("message", "Failed to check option instruments: " + e.getMessage());
            response.put("error", e.toString());
        }
        return response;
    }

    @GetMapping("/checkNextAvailableExpiry")
    public Map<String, Object> checkNextAvailableExpiry() {
        Map<String, Object> response = new HashMap<>();
        try {
            // Get only Nifty option instruments with minimal processing
            List<com.jtradebot.processor.repository.document.Instrument> niftyOptions = instrumentRepository.findAll().stream()
                    .filter(instrument -> "NIFTY".equals(instrument.getName()) &&
                            ("CE".equals(instrument.getInstrumentType()) || "PE".equals(instrument.getInstrumentType())))
                    .toList();

            // Find next available expiry (earliest date)
            String nextExpiry = niftyOptions.stream()
                    .map(com.jtradebot.processor.repository.document.Instrument::getExpiry)
                    .distinct()
                    .min((expiry1, expiry2) -> {
                        try {
                            java.time.LocalDate date1 = java.time.LocalDate.parse(expiry1, java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
                            java.time.LocalDate date2 = java.time.LocalDate.parse(expiry2, java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
                            return date1.compareTo(date2);
                        } catch (Exception e) {
                            return expiry1.compareTo(expiry2);
                        }
                    })
                    .orElse("None");

            response.put("nextAvailableExpiry", nextExpiry);

            // Get only sample options for the next expiry (limit 5 for speed)
            List<Map<String, Object>> sampleOptions = new ArrayList<>();
            if (!"None".equals(nextExpiry)) {
                sampleOptions = niftyOptions.stream()
                        .filter(instrument -> nextExpiry.equals(instrument.getExpiry()))
                        .limit(5) // Only process 5 options for speed
                        .map(instrument -> {
                            Map<String, Object> option = new HashMap<>();
                            option.put("tradingSymbol", instrument.getTradingSymbol());
                            option.put("instrumentToken", instrument.getInstrumentToken());
                            option.put("strike", instrument.getStrike());
                            option.put("expiry", instrument.getExpiry());
                            option.put("instrumentType", instrument.getInstrumentType());

                            // Get LTP for this option using Kite Connect API
                            try {
                                String instrumentToken = String.valueOf(instrument.getInstrumentToken());
                                double ltp = kiteConnect.getLTP(new String[]{instrumentToken}).get(instrumentToken).lastPrice;
                                option.put("ltp", ltp);
                            } catch (KiteException | Exception e) {
                                option.put("ltp", "Error: " + e.getMessage());
                            }

                            return option;
                        })
                        .collect(java.util.stream.Collectors.toList());
            }

            response.put("sampleOptions", sampleOptions);
            response.put("sampleOptionsCount", sampleOptions.size());
            response.put("message", "Next available expiry found: " + nextExpiry);

        } catch (Exception e) {
            response.put("message", "Failed to check next available expiry: " + e.getMessage());
            response.put("error", e.toString());
        }
        return response;
    }

    @GetMapping("/checkInstrumentFreshness")
    public Map<String, Object> checkInstrumentFreshness() {
        Map<String, Object> response = new HashMap<>();
        try {
            InstrumentFreshnessCheckerService.InstrumentFreshnessResult result =
                    instrumentFreshnessCheckerService.checkInstrumentFreshnessWithResults();

            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());
            response.put("todayDate", result.getTodayDate());
            response.put("currentInstrumentCount", result.getCurrentInstrumentCount());
            response.put("isCurrent", result.isCurrent());
            response.put("needsRefresh", result.isNeedsRefresh());

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to check instrument freshness: " + e.getMessage());
            response.put("error", e.toString());
        }
        return response;
    }

}
