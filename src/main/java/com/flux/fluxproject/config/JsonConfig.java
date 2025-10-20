//package com.flux.fluxproject.config;
//
//import com.fasterxml.jackson.core.JsonProcessingException;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import io.r2dbc.postgresql.codec.Json;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.core.convert.converter.Converter;
//import org.springframework.data.convert.ReadingConverter;
//import org.springframework.data.convert.WritingConverter;
//import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
//import org.springframework.data.r2dbc.dialect.PostgresDialect;
//
//import java.io.IOException;
//import java.util.Arrays;
//import java.util.List;
//import java.util.Map;
//
//@Configuration
//public class JsonConfig {
//
//    @Bean
//    public R2dbcCustomConversions r2dbcCustomConversions() {
//        List<Converter<?, ?>> converters = Arrays.asList(
//                SocialAccountWriteConverter.INSTANCE,
//                SocialAccountReadConverter.INSTANCE
//        );
//        return R2dbcCustomConversions.of(PostgresDialect.INSTANCE, converters);
//    }
//}
//
//@WritingConverter
//enum SocialAccountWriteConverter implements Converter<Map<String, Object>, Json> {
//    INSTANCE;
//
//    private final ObjectMapper mapper = new ObjectMapper();
//
//    @Override
//    public Json convert(Map<String, Object> source) {
//        try {
//            return Json.of(mapper.writeValueAsString(source));
//        } catch (JsonProcessingException e) {
//            throw new IllegalArgumentException("Failed to serialize authData to JSON", e);
//        }
//    }
//}
//
//@ReadingConverter
//enum SocialAccountReadConverter implements Converter<Json, Map<String, Object>> {
//    INSTANCE;
//
//    private final ObjectMapper mapper = new ObjectMapper();
//
//    @Override
//    public Map<String, Object> convert(Json source) {
//        try {
//            return mapper.readValue(source.asString(), Map.class);
//        } catch (IOException e) {
//            throw new IllegalArgumentException("Failed to deserialize authData from JSON", e);
//        }
//    }
//}