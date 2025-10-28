package com.jtradebot.processor.service.price;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Option Greeks Calculator using Black-Scholes model
 * Calculates Delta, Theta, Gamma, Vega for option pricing and risk analysis
 */
@Service
@Slf4j
public class OptionGreeksCalculator {

    // Risk-free rate for Indian market (approximate)
    private static final double RISK_FREE_RATE = 0.065; // 6.5% annual
    
    /**
     * Calculate all Greeks for an option
     */
    public OptionGreeks calculateGreeks(double spotPrice, double strikePrice, double timeToExpiry, 
                                      double impliedVolatility, String optionType, double optionPrice) {
        try {
            // Calculate time to expiry in years
            double timeInYears = Math.max(0.001, timeToExpiry / 365.0); // Minimum 1 day to avoid division by zero
            
            // Calculate d1 and d2 for Black-Scholes
            double d1 = calculateD1(spotPrice, strikePrice, timeInYears, impliedVolatility, RISK_FREE_RATE);
            double d2 = calculateD2(d1, impliedVolatility, timeInYears);
            
            // Calculate Greeks
            double delta = calculateDelta(d1, optionType);
            double gamma = calculateGamma(spotPrice, d1, impliedVolatility, timeInYears);
            double theta = calculateTheta(spotPrice, strikePrice, d1, d2, timeInYears, impliedVolatility, optionType);
            double vega = calculateVega(spotPrice, d1, timeInYears);
            
            // Calculate additional metrics
            double intrinsicValue = calculateIntrinsicValue(spotPrice, strikePrice, optionType);
            double timeValue = Math.max(0, optionPrice - intrinsicValue);
            double moneyness = calculateMoneyness(spotPrice, strikePrice, optionType);
            
            return OptionGreeks.builder()
                    .delta(delta)
                    .gamma(gamma)
                    .theta(theta)
                    .vega(vega)
                    .intrinsicValue(intrinsicValue)
                    .timeValue(timeValue)
                    .moneyness(moneyness)
                    .impliedVolatility(impliedVolatility)
                    .timeToExpiry(timeInYears)
                    .build();
                    
        } catch (Exception e) {
            log.warn("Error calculating Greeks for strike: {}, spot: {}", strikePrice, spotPrice, e);
            return OptionGreeks.builder()
                    .delta(0.0)
                    .gamma(0.0)
                    .theta(0.0)
                    .vega(0.0)
                    .intrinsicValue(0.0)
                    .timeValue(0.0)
                    .moneyness(0.0)
                    .impliedVolatility(impliedVolatility)
                    .timeToExpiry(timeToExpiry / 365.0)
                    .build();
        }
    }
    
    /**
     * Calculate time to expiry from expiry date string
     */
    public double calculateTimeToExpiry(String expiryDateStr) {
        try {
            LocalDate expiryDate = LocalDate.parse(expiryDateStr, DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
            LocalDate currentDate = LocalDate.now();
            long daysToExpiry = ChronoUnit.DAYS.between(currentDate, expiryDate);
            return Math.max(0, daysToExpiry);
        } catch (Exception e) {
            log.warn("Error parsing expiry date: {}", expiryDateStr, e);
            return 1; // Default to 1 day
        }
    }
    
    /**
     * Calculate implied volatility using Newton-Raphson method
     * This is a production-ready implementation using proper numerical methods
     */
    public double estimateImpliedVolatility(double spotPrice, double strikePrice, double optionPrice, 
                                          double timeToExpiry, String optionType) {
        try {
            double timeInYears = Math.max(0.001, timeToExpiry / 365.0);
            
            // Input validation
            if (spotPrice <= 0 || strikePrice <= 0 || optionPrice <= 0 || timeInYears <= 0) {
                log.warn("Invalid input parameters for IV calculation: spot={}, strike={}, price={}, time={}", 
                        spotPrice, strikePrice, optionPrice, timeInYears);
                return 0.20; // Default to 20% volatility
            }
            
            // Newton-Raphson parameters
            double tolerance = 1e-6; // High precision for production
            int maxIterations = 50;
            
            // Initial guess: Use Brenner-Subrahmanyam approximation for better starting point

            double vol = getInitialVolatilityGuess(spotPrice, strikePrice, optionPrice, timeInYears, optionType);
            double volLower = 0.001; // Minimum volatility (0.1%)
            double volUpper = 5.0;   // Maximum volatility (500%)
            
            for (int i = 0; i < maxIterations; i++) {
                // Calculate theoretical price and vega (derivative with respect to volatility)
                double theoreticalPrice = calculateBlackScholesPrice(spotPrice, strikePrice, timeInYears, vol, optionType);
                double vega = calculateVega(spotPrice, calculateD1(spotPrice, strikePrice, timeInYears, vol, RISK_FREE_RATE), timeInYears);
                
                // Calculate the difference between theoretical and market price
                double priceDifference = theoreticalPrice - optionPrice;
                
                // Check convergence
                if (Math.abs(priceDifference) < tolerance) {
                    break;
                }
                
                // Avoid division by zero or very small vega
                if (Math.abs(vega) < 1e-10) {
                    // Fall back to bisection method
                    vol = bisectionMethod(spotPrice, strikePrice, optionPrice, timeInYears, optionType, 
                                        volLower, volUpper, tolerance, maxIterations);
                    break;
                }
                
                // Newton-Raphson update: vol_new = vol_old - f(vol)/f'(vol)
                double volNew = vol - (priceDifference / vega);
                
                // Ensure the new volatility stays within bounds
                volNew = Math.max(volLower, Math.min(volUpper, volNew));
                
                // Check for convergence in volatility
                if (Math.abs(volNew - vol) < tolerance) {
                    vol = volNew;
                    log.debug("IV convergence achieved in {} iterations: vol={:.4f}", i + 1, vol);
                    break;
                }
                
                vol = volNew;
                
                // Safety check for oscillation or divergence
                if (i > 10 && Math.abs(priceDifference) > Math.abs(theoreticalPrice * 0.1)) {
                    log.warn("Newton-Raphson may be diverging, falling back to bisection. Iteration: {}, diff: {}", 
                            i + 1, String.format("%.6f", priceDifference));
                    vol = bisectionMethod(spotPrice, strikePrice, optionPrice, timeInYears, optionType, 
                                        volLower, volUpper, tolerance, maxIterations);
                    break;
                }
            }
            
            // Final validation
            if (vol < volLower || vol > volUpper) {
                log.warn("IV calculation resulted in out-of-bounds value: {:.4f}, using default", vol);
                return 0.20;
            }
            
            log.debug("Final IV calculation: vol={:.4f}% for {}({}) strike={}, price={}", 
                    vol * 100, optionType, spotPrice, strikePrice, optionPrice);
            
            return vol;
            
        } catch (Exception e) {
            log.error("Error in Newton-Raphson IV calculation", e);
            return 0.20; // Default to 20% volatility
        }
    }
    
    /**
     * Get initial volatility guess using Brenner-Subrahmanyam approximation
     * This provides a better starting point for Newton-Raphson
     */
    private double getInitialVolatilityGuess(double spotPrice, double strikePrice, double optionPrice, 
                                           double timeInYears, String optionType) {
        try {
            if (timeInYears <= 0) return 0.20;
            
            double sqrtTime = Math.sqrt(timeInYears);
            double intrinsicValue = calculateIntrinsicValue(spotPrice, strikePrice, optionType);
            
            // For ATM options (when spot ≈ strike)
            if (Math.abs(spotPrice - strikePrice) / strikePrice < 0.02) { // Within 2%
                // Brenner-Subrahmanyam approximation for ATM options
                double approximation = (optionPrice * Math.sqrt(2 * Math.PI)) / (spotPrice * sqrtTime);
                return Math.max(0.01, Math.min(2.0, approximation));
            } else {
                // For ITM/OTM options, use a more sophisticated initial guess
                double moneyness = spotPrice / strikePrice;
                double logMoneyness = Math.log(moneyness);
                
                // Corrado-Miller approximation
                double a = optionPrice - intrinsicValue;
                double b = 2 * Math.PI * timeInYears;
                double c = spotPrice - strikePrice * Math.exp(-RISK_FREE_RATE * timeInYears);
                
                if (a > 0 && b > 0) {
                    double discriminant = c * c + 4 * a * b;
                    if (discriminant >= 0) {
                        double approximation = Math.sqrt(discriminant) / (2 * sqrtTime);
                        return Math.max(0.01, Math.min(2.0, approximation));
                    }
                }
                
                // Fallback to simple estimate based on moneyness
                double simpleEstimate = Math.abs(logMoneyness) / sqrtTime + 0.2;
                return Math.max(0.01, Math.min(2.0, simpleEstimate));
            }
        } catch (Exception e) {
            log.warn("Error in initial volatility guess calculation", e);
            return 0.20;
        }
    }
    
    /**
     * Bisection method as fallback when Newton-Raphson fails
     * This ensures we always find a solution within the specified bounds
     */
    private double bisectionMethod(double spotPrice, double strikePrice, double optionPrice, 
                                 double timeInYears, String optionType, double volLower, double volUpper, 
                                 double tolerance, int maxIterations) {
        try {
            log.debug("Using bisection method for IV calculation");
            
            double volLow = volLower;
            double volHigh = volUpper;
            
            for (int i = 0; i < maxIterations; i++) {
                double volMid = (volLow + volHigh) / 2.0;
                
                double priceMid = calculateBlackScholesPrice(spotPrice, strikePrice, timeInYears, volMid, optionType);
                double difference = priceMid - optionPrice;
                
                if (Math.abs(difference) < tolerance) {
                    log.debug("Bisection convergence achieved in {} iterations: vol={:.4f}", i + 1, volMid);
                    return volMid;
                }
                
                // Determine which half to search
                double priceLow = calculateBlackScholesPrice(spotPrice, strikePrice, timeInYears, volLow, optionType);
                double differenceLow = priceLow - optionPrice;
                
                if ((differenceLow < 0 && difference > 0) || (differenceLow > 0 && difference < 0)) {
                    volHigh = volMid;
                } else {
                    volLow = volMid;
                }
                
                // Check for convergence in bounds
                if (volHigh - volLow < tolerance) {
                    log.debug("Bisection bounds convergence: vol={:.4f}", volMid);
                    return volMid;
                }
            }
            
            log.warn("Bisection method did not converge after {} iterations", maxIterations);
            return (volLow + volHigh) / 2.0; // Return midpoint as best estimate
            
        } catch (Exception e) {
            log.error("Error in bisection method", e);
            return 0.20;
        }
    }
    
    // Black-Scholes helper methods
    private double calculateD1(double spotPrice, double strikePrice, double timeInYears, 
                              double volatility, double riskFreeRate) {
        return (Math.log(spotPrice / strikePrice) + (riskFreeRate + 0.5 * volatility * volatility) * timeInYears) 
               / (volatility * Math.sqrt(timeInYears));
    }
    
    private double calculateD2(double d1, double volatility, double timeInYears) {
        return d1 - volatility * Math.sqrt(timeInYears);
    }
    
    private double calculateDelta(double d1, String optionType) {
        double delta = normalCDF(d1);
        return "CE".equals(optionType) ? delta : delta - 1.0;
    }
    
    private double calculateGamma(double spotPrice, double d1, double volatility, double timeInYears) {
        return normalPDF(d1) / (spotPrice * volatility * Math.sqrt(timeInYears));
    }
    
    private double calculateTheta(double spotPrice, double strikePrice, double d1, double d2, 
                                 double timeInYears, double volatility, String optionType) {
        double theta1 = -(spotPrice * normalPDF(d1) * volatility) / (2 * Math.sqrt(timeInYears));
        double theta2 = RISK_FREE_RATE * strikePrice * Math.exp(-RISK_FREE_RATE * timeInYears) * normalCDF(d2);
        
        if ("PE".equals(optionType)) {
            theta2 = -RISK_FREE_RATE * strikePrice * Math.exp(-RISK_FREE_RATE * timeInYears) * normalCDF(-d2);
        }
        
        return theta1 - theta2;
    }
    
    private double calculateVega(double spotPrice, double d1, double timeInYears) {
        return spotPrice * normalPDF(d1) * Math.sqrt(timeInYears) / 100; // Divided by 100 for 1% vol change
    }
    
    private double calculateBlackScholesPrice(double spotPrice, double strikePrice, double timeInYears, 
                                            double volatility, String optionType) {
        double d1 = calculateD1(spotPrice, strikePrice, timeInYears, volatility, RISK_FREE_RATE);
        double d2 = calculateD2(d1, volatility, timeInYears);
        
        double price;
        if ("CE".equals(optionType)) {
            price = spotPrice * normalCDF(d1) - strikePrice * Math.exp(-RISK_FREE_RATE * timeInYears) * normalCDF(d2);
        } else {
            price = strikePrice * Math.exp(-RISK_FREE_RATE * timeInYears) * normalCDF(-d2) - spotPrice * normalCDF(-d1);
        }
        
        return Math.max(0, price);
    }
    
    private double calculateIntrinsicValue(double spotPrice, double strikePrice, String optionType) {
        if ("CE".equals(optionType)) {
            return Math.max(0, spotPrice - strikePrice);
        } else {
            return Math.max(0, strikePrice - spotPrice);
        }
    }
    
    private double calculateMoneyness(double spotPrice, double strikePrice, String optionType) {
        if ("CE".equals(optionType)) {
            return spotPrice / strikePrice;
        } else {
            return strikePrice / spotPrice;
        }
    }
    
    // Normal distribution CDF approximation
    private double normalCDF(double x) {
        return 0.5 * (1 + erf(x / Math.sqrt(2)));
    }
    
    // Normal distribution PDF
    private double normalPDF(double x) {
        return Math.exp(-0.5 * x * x) / Math.sqrt(2 * Math.PI);
    }
    
    // Error function approximation
    private double erf(double x) {
        // Abramowitz and Stegun approximation
        double a1 = 0.254829592;
        double a2 = -0.284496736;
        double a3 = 1.421413741;
        double a4 = -1.453152027;
        double a5 = 1.061405429;
        double p = 0.3275911;
        
        int sign = x >= 0 ? 1 : -1;
        x = Math.abs(x);
        
        double t = 1.0 / (1.0 + p * x);
        double y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * Math.exp(-x * x);
        
        return sign * y;
    }
    
    /**
     * Data class for Option Greeks
     */
    @Data
    @lombok.Builder
    public static class OptionGreeks {
        private double delta;           // Price sensitivity to underlying
        private double gamma;           // Delta sensitivity to underlying
        private double theta;           // Time decay (negative for long positions)
        private double vega;            // Volatility sensitivity
        private double intrinsicValue;  // Intrinsic value of option
        private double timeValue;       // Time value of option
        private double moneyness;       // Moneyness ratio
        private double impliedVolatility; // Implied volatility used
        private double timeToExpiry;    // Time to expiry in years
    }
}
