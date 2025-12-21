package com.flux.fluxproject.repositories;

import com.flux.fluxproject.domain.OAuth2AuthRequest;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import java.util.UUID;

public interface OAuth2AuthRequestRepository extends ReactiveCrudRepository<OAuth2AuthRequest, UUID> {
}
