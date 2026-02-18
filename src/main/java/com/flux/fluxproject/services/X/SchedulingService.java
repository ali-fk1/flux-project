package com.flux.fluxproject.services.X;

import com.flux.fluxproject.domain.Post;
import com.flux.fluxproject.domain.PostStatus;
import com.flux.fluxproject.model.ScheduledPostRequest;
import com.flux.fluxproject.repositories.PostRepository;
import com.flux.fluxproject.repositories.SocialAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Slf4j
@Service
public class SchedulingService {

    private final PostRepository postRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final XPostService xPostService;

    public Mono<Post> saveScheduledPost (ScheduledPostRequest scheduledPostRequest , UUID userId){
        return socialAccountRepository.findByUserIdAndPlatform(userId , "X")
                .flatMap(socialAccount->{
                    Post newPost = Post.builder()
                            .userId(userId)
                            .socialAccountId(socialAccount.getId())
                            .platform("X")
                            .content(scheduledPostRequest.getText())
                            .scheduledAtUtc(scheduledPostRequest.getScheduledAtUtc())
                            .createdAtUtc(Instant.now())
                            .status(PostStatus.scheduled)
                            .mediaUrls(List.of()) //currently null to be implemented in upcoming features
                            .apiPayload(null)
                            .retryCount(0)
                            .maxRetries(3)
                            .updatedAtUtc(Instant.now())
                            .build();
                    return postRepository.save(newPost);
                });
    }

    public Flux<Post> executePosting(int batchSize){
        return postRepository.claimDuePosts(batchSize)
                .flatMap(duePost->
                    xPostService.postTextWithAutoRefresh(duePost.getUserId() ,duePost.getContent())
                            .flatMap(resp->postRepository.markPublished(duePost.getId(),Instant.now()))
                            .onErrorResume(e->postRepository.markFailed(duePost.getId() , safeMsg(e)))
               ,2);
    }


    private String safeMsg(Throwable e) {
        String msg = e.getMessage();
        return (msg == null || msg.isBlank()) ? e.getClass().getSimpleName() : msg;
    }


}
