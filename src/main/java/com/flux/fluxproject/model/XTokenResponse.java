package com.flux.fluxproject.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class XTokenResponse {
    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("expires_in")
    private Long expiresIn;

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("scope")
    private String scope;

    @JsonProperty("refresh_token")
    private String refreshToken;
}
