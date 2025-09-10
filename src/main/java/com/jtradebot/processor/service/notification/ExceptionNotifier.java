package com.jtradebot.processor.service.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExceptionNotifier {

    private final SnsEmailService snsEmailService;

    /**
     * Send notification for instrument generation failure
     */
    public void sendInstrumentGenerationFailureNotification(String errorMessage, Exception exception) {
        try {
            String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss"));
            
            String subject = "❌ INSTRUMENT GENERATION FAILURE";
            
            String message = String.format("""
                🚨 INSTRUMENT GENERATION FAILED
                
                ⏰ Time: %s IST
                
                📊 ERROR DETAILS:
                • Error Message: %s
                • Exception Type: %s
                • Exception Details: %s
                
                🔗 Manual Trigger: POST /connection/generateInstruments
                """, 
                currentTime,
                errorMessage,
                exception.getClass().getSimpleName(),
                exception.getMessage()
            );
            
            snsEmailService.sendEmail(subject, message);
            log.info("📧 Instrument generation failure notification sent");
            
        } catch (Exception e) {
            log.error("❌ Failed to send instrument generation failure notification: {}", e.getMessage(), e);
        }
    }

    /**
     * Send notification for general system exceptions
     */
    public void sendSystemExceptionNotification(String component, String operation, Exception exception) {
        try {
            String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss"));
            
            String subject = "⚠️ SYSTEM EXCEPTION - " + component;
            
            String message = String.format("""
                🚨 SYSTEM EXCEPTION DETECTED
                
                ⏰ Time: %s IST
                
                📊 EXCEPTION DETAILS:
                • Component: %s
                • Operation: %s
                • Exception Type: %s
                • Error Message: %s
                
                🔧 STACK TRACE:
                %s
                """, 
                currentTime,
                component,
                operation,
                exception.getClass().getSimpleName(),
                exception.getMessage(),
                getStackTraceString(exception)
            );
            
            snsEmailService.sendEmail(subject, message);
            log.info("📧 System exception notification sent for component: {}", component);
            
        } catch (Exception e) {
            log.error("❌ Failed to send system exception notification: {}", e.getMessage(), e);
        }
    }

    /**
     * Send notification for successful instrument generation
     */
    public void sendInstrumentGenerationSuccessNotification(int instrumentsGenerated) {
        try {
            String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss"));
            
            String subject = "✅ INSTRUMENT GENERATION SUCCESS";
            
            String message = String.format("""
                ✅ INSTRUMENTS GENERATED SUCCESSFULLY
                
                ⏰ Time: %s IST
                
                📊 GENERATION DETAILS:
                • Instruments Generated: %d
                • Status: SUCCESS
                • Source: Kite Connect API
                
                🔗 Check instruments: GET /connection/checkOptionInstruments
                """, 
                currentTime,
                instrumentsGenerated
            );
            
            snsEmailService.sendEmail(subject, message);
            log.info("📧 Instrument generation success notification sent for {} instruments", instrumentsGenerated);
            
        } catch (Exception e) {
            log.error("❌ Failed to send instrument generation success notification: {}", e.getMessage(), e);
        }
    }

    /**
     * Helper method to get stack trace as string
     */
    private String getStackTraceString(Exception exception) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        exception.printStackTrace(pw);
        return sw.toString();
    }
}
