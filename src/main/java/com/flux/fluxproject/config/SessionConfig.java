package com.flux.fluxproject.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.server.session.CookieWebSessionIdResolver;
import org.springframework.web.server.session.DefaultWebSessionManager;
import org.springframework.web.server.session.InMemoryWebSessionStore;
import org.springframework.web.server.session.WebSessionIdResolver;
import org.springframework.web.server.session.WebSessionManager;

import java.time.Duration;

@Configuration
public class SessionConfig {

    private final Environment environment;

    public SessionConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public WebSessionManager webSessionManager() {
        DefaultWebSessionManager manager = new DefaultWebSessionManager();
        manager.setSessionStore(new InMemoryWebSessionStore());
        manager.setSessionIdResolver(webSessionIdResolver());
        return manager;
    }

    @Bean
    public WebSessionIdResolver webSessionIdResolver() {
        CookieWebSessionIdResolver resolver = new CookieWebSessionIdResolver();
        resolver.setCookieName("JSESSIONID");

        // Determine if we're in development (using tunneling services)
        boolean isDevelopment = isDevelopmentEnvironment();

        resolver.addCookieInitializer(builder -> {
            builder.path("/");
            builder.maxAge(Duration.ofMinutes(30));
            builder.httpOnly(true);

            if (isDevelopment) {
                builder.secure(false);       // ok since loca.lt/ngrok are http/https tunnels
                builder.sameSite("None");    // allow cross-site redirect
            } else {
                builder.secure(true);
                builder.sameSite("None");
            }

        });

        return resolver;
    }

    private boolean isDevelopmentEnvironment() {
        // Check if we're using tunneling services or localhost
        String callbackUrl = environment.getProperty("x.callback-url", "");
        return callbackUrl.contains("loca.lt") ||
                callbackUrl.contains("ngrok") ||
                callbackUrl.contains("localhost") ||
                environment.matchesProfiles("dev", "development");
    }
}