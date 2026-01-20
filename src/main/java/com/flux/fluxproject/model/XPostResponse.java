package com.flux.fluxproject.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class XPostResponse {

    @JsonProperty("data")
    private XTweetData data;

    // Convenience methods to access nested fields
    public String getTweetId() {
        return data != null ? data.getId() : null;
    }

    public String getTweetText() {
        return data != null ? data.getText() : null;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class XTweetData {
        private String id;
        private String text;

        @JsonProperty("created_at")
        private String createdAt;
    }
}