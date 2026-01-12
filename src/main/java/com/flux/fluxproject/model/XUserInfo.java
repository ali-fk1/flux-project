package com.flux.fluxproject.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class XUserInfo {

    private String id;
    private String username;
    private String name;
//    private String profile_image_url;
//    private String description;
//    private Boolean verified;

    // This unwraps the "data" object from X API response
    @JsonProperty("data")
    private void unpackData(XUserData data) {
        if (data != null) {
            this.id = data.id;
            this.username = data.username;
            this.name = data.name;
//            this.profile_image_url = data.profile_image_url;
//            this.description = data.description;
//            this.verified = data.verified;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class XUserData {
        private String id;
        private String username;
        private String name;
//        private String profile_image_url;
//        private String description;
//        private Boolean verified;
    }
}