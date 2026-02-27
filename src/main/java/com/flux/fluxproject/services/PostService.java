package com.flux.fluxproject.services;

import com.flux.fluxproject.domain.Post;
import com.flux.fluxproject.domain.PostStatus;
import com.flux.fluxproject.mappers.PostViewMapper;
import com.flux.fluxproject.model.CursorPageResponse;
import com.flux.fluxproject.model.PostCursor;
import com.flux.fluxproject.model.PostViewResponse;
import com.flux.fluxproject.repositories.PostRepository;
import com.flux.fluxproject.util.CursorUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final PostViewMapper postViewMapper;
    private final CursorUtil cursorUtil;

    public Mono<CursorPageResponse<PostViewResponse>> getPosts(
            UUID userId,
            int size,
            PostStatus status,
            String cursor
    ) {

        int normalizedSize = (size <= 0) ? 20 : Math.min(size, 100);
        int fetchSize = normalizedSize + 1;

        log.info("Fetching posts | userId={} | size={} | normalizedSize={} | status={} | cursorPresent={}",
                userId, size, normalizedSize, status, cursor != null);

        Flux<Post> postFlux;

        if (cursor == null || cursor.isBlank()) {

            log.info("Cursor is null/blank → fetching FIRST page");

            postFlux = postRepository.findFirstPage(userId, status, fetchSize);

        } else {

            log.info("Cursor provided → decoding cursor={}", cursor);

            PostCursor decoded;

            try {
                decoded = cursorUtil.decode(cursor);
                log.info("Decoded cursor → scheduledAt={} | id={}",
                        decoded.scheduledAt(), decoded.id());
            } catch (Exception e) {
                log.error("Failed to decode cursor!", e);
                return Mono.error(new IllegalArgumentException("Invalid cursor"));
            }

            postFlux = postRepository.findNextPage(
                    userId,
                    status,
                    decoded.scheduledAt(),
                    decoded.id(),
                    fetchSize
            );
        }

        return postFlux
                .doOnNext(post ->
                        log.debug("Fetched Post → id={} | scheduledAt={} | status={}",
                                post.getId(),
                                post.getScheduledAtUtc(),
                                post.getStatus()
                        )
                )
                .collectList()
                .map(posts -> {

                    log.info("Total posts fetched from DB (including extra row) = {}", posts.size());

                    boolean hasNext = posts.size() > normalizedSize;

                    List<Post> page = hasNext
                            ? posts.subList(0, normalizedSize)
                            : posts;

                    log.info("Page size after trimming = {} | hasNext={}",
                            page.size(), hasNext);

                    List<PostViewResponse> content = page.stream()
                            .map(postViewMapper::postToPostView)
                            .toList();

                    String nextCursor = null;

                    if (hasNext && !page.isEmpty()) {
                        Post last = page.getLast();

                        log.info("Generating nextCursor from → id={} | scheduledAt={}",
                                last.getId(), last.getScheduledAtUtc());

                        PostCursor newCursor = new PostCursor(
                                last.getScheduledAtUtc(),
                                last.getId()
                        );

                        nextCursor = cursorUtil.encode(newCursor);

                        log.info("Encoded nextCursor={}", nextCursor);
                    }

                    return new CursorPageResponse<>(content, nextCursor, hasNext);
                })
                .doOnError(error ->
                        log.error("Error while fetching posts for userId={}", userId, error)
                );
    }
}