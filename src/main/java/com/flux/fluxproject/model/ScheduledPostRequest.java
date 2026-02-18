package com.flux.fluxproject.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;

@Data
public class ScheduledPostRequest {

    @JsonProperty("platform")
    private String platform;

    @JsonProperty("text")
    private String text;

    @JsonProperty("scheduled_at_utc")
    private Instant scheduledAtUtc;

    @JsonProperty("user_time_zone")
    private String userTimeZone;

}
