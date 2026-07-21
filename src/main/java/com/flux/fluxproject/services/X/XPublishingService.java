package com.flux.fluxproject.services.X;

import com.flux.fluxproject.domain.Post;
import com.flux.fluxproject.domain.PostStatus;
import com.flux.fluxproject.model.XPostResponse;
import com.flux.fluxproject.repositories.PostRepository;
import com.flux.fluxproject.repositories.SocialAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class XPublishingService {

    private final XPostService xPostService;
    private final PostRepository postRepository;
    private final SocialAccountRepository socialAccountRepository;

    /**
     * Used by the scheduler.
     * Only publishes to X.
     */
    public Mono<XPostResponse> publishText(UUID userId, String text) {
        return xPostService.postTextWithAutoRefresh(userId, text);
    }

    /**
     * Used by the "Post Now" endpoint.
     * Publishes to X then stores the post in the database.
     */
    public Mono<XPostResponse> publishNow(UUID userId, String text) {

        return socialAccountRepository.findSocialAccountIdByUserId(userId)
                .flatMap(socialAccountId ->
                        publishText(userId, text)
                                .flatMap(response -> {

                                    Post post = new Post();
                                    post.setUserId(userId);
                                    post.setSocialAccountId(socialAccountId);
                                    post.setContent(text);
                                    post.setPlatform("X");
                                    post.setStatus(PostStatus.published);
                                    post.setPublishedAtUtc(Instant.now());

                                    return postRepository.save(post)
                                            .thenReturn(response);
                                })
                );
    }
}