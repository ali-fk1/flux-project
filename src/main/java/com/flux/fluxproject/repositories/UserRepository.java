    package com.flux.fluxproject.repositories;

    import com.flux.fluxproject.domain.User;
    import org.springframework.data.r2dbc.repository.Query;
    import org.springframework.data.repository.reactive.ReactiveCrudRepository;
    import reactor.core.publisher.Mono;

    import java.util.UUID;

    public interface UserRepository extends ReactiveCrudRepository<User, UUID> {
        Mono<User> findByEmail(String email);



        // simple update query to set enabled (verified)
        @Query("UPDATE users SET enabled = true, updated_at = now() WHERE id = :id")
        Mono<Void> setEnabledTrue(UUID id);
    }
