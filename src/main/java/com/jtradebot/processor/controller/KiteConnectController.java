package com.jtradebot.processor.controller;

import com.jtradebot.processor.aws.AwsSecretHandler;
import com.jtradebot.processor.connector.KiteSetupHandler;
import com.jtradebot.processor.service.TickSetupService;
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
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/connection")
@RequiredArgsConstructor
@Slf4j
public class KiteConnectController {

    private final KiteConnect kiteConnect;
    private final KiteSetupHandler kiteSetupHandler;
    private final AwsSecretHandler awsSecretHandler;
    private final TickSetupService tickSetupService;

    @Value("${aws.kite.api-secret}")
    private String kiteApiSecret;

    @Value("${aws.kite.secret-name}")
    private String kiteConnectAwsSecretName;


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

    @GetMapping("/callback")
    public ResponseEntity<Map<String, String>> callback(
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "request_token", required = false) String requestToken) {
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
            
            User user = kiteConnect.generateSession(requestToken,
                    awsSecretHandler.getSecret(kiteConnectAwsSecretName, kiteApiSecret));
            tickSetupService.saveDefaultTradeConfig(user);
            response.put("message", "Access token successfully obtained.");
            log.info("Access token successfully obtained for action: {}, type: {}", action, type);
            return ResponseEntity.ok(response);
        } catch (KiteException e) {
            response.put("message", errorMessage + " : " + e.getMessage());
            log.error(errorMessage, e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        } catch (IOException e) {
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
    public Map<String, String> subscribeToken(@RequestParam String token) {
        Map<String, String> response = new HashMap<>();
        kiteSetupHandler.subscribeToken(token);
        response.put("message", "Token subscribed successfully");
        return response;
    }

}
