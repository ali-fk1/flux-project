package com.flux.fluxproject.schedulers;

import com.flux.fluxproject.repositories.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class CleanupScheduler {

    private final PostRepository postRepository;

    @Scheduled(cron = "0 0 3 * * ?")  // every day at 3 AM
    public void cleanupDeletedPosts() {
        postRepository.findDeletedOlderThan(30) // posts deleted >30 days ago
                .flatMap(post -> postRepository.deleteById(post.getId()))
                .subscribe(count -> log.info("Deleted soft-deleted post permanently"));
    }
}