package com.jtradebot.processor.authentication;

import com.jtradebot.processor.aws.AwsSecretHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final AwsSecretHandler awsSecretHandler;

    @Value("${aws.kite.secret-name}")
    private String kiteConnectAwsSecretName;

    @Value("${aws.kite.basic-auth-username}")
    private String appUsername;

    @Value("${aws.kite.basic-auth-password}")
    private String appPassword;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated()
                )
                .httpBasic();
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow specific origins for development and production
        configuration.addAllowedOrigin("http://localhost:5173");
        configuration.addAllowedOrigin("https://jtradebot.com");
        configuration.addAllowedOrigin("https://www.jtradebot.com");
        // Allow all HTTP methods
        configuration.addAllowedMethod("*");
        // Allow all headers
        configuration.addAllowedHeader("*");
        // Allow credentials (cookies, authorization headers, etc.)
        configuration.setAllowCredentials(true);
        // Allow exposed headers
        configuration.addExposedHeader("Authorization");
        configuration.addExposedHeader("Content-Type");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public UserDetailsService userDetailsService() {
        User.UserBuilder users = User.builder();
        return new InMemoryUserDetailsManager(
                users.username(awsSecretHandler.getSecretMap(kiteConnectAwsSecretName).get(appUsername))
                        .password(passwordEncoder().encode(awsSecretHandler.getSecretMap(kiteConnectAwsSecretName).get(appPassword)))
                        .roles("USER").build());
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}