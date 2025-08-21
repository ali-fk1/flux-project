package com.flux.fluxproject.model;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class UserDTO {
    private UUID id;
    private String email;
    private String passwordHash;
    private String name;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
