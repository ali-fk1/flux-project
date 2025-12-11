package com.flux.fluxproject.config;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.ReactiveTransactionManager;

@Configuration
public class R2dbcConfig {
    @Bean
    public ReactiveTransactionManager transactionManager(ConnectionFactory cf) {
        return new R2dbcTransactionManager(cf);
    }
}
