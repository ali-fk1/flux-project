package com.flux.fluxproject.repositories;

import com.flux.fluxproject.domain.Post;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import java.util.UUID;

public interface PostRepository extends ReactiveCrudRepository<Post, UUID> {

}
