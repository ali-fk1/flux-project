package com.flux.fluxproject.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.session.CookieWebSessionIdResolver;
import org.springframework.web.server.session.DefaultWebSessionManager;
import org.springframework.web.server.session.InMemoryWebSessionStore;
import org.springframework.web.server.session.WebSessionIdResolver;
import org.springframework.web.server.session.WebSessionManager;

import java.time.Duration;

@Configuration
public class SessionConfig {

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
        resolver.addCookieInitializer(builder -> {
            builder.path("/");
            builder.maxAge(Duration.ofMinutes(30));
            builder.httpOnly(true);
            builder.secure(false); // Set to true in production with HTTPS
            builder.sameSite("Lax");
        });
        return resolver;
    }
}