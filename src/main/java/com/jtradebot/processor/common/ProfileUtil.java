package com.jtradebot.processor.common;

import org.springframework.core.env.Environment;

public class ProfileUtil {

    public static boolean isProfileActive(Environment environment, String... profiles) {
        if (environment.getActiveProfiles().length > 0) {
            for (String profile : profiles) {
                for (String activeProfile : environment.getActiveProfiles()) {
                    if (activeProfile.equals(profile)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}