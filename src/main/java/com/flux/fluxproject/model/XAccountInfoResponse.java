package com.flux.fluxproject.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XAccountInfoResponse {
    private boolean connected;
    private String username;
    private String profileImageUrl;
}