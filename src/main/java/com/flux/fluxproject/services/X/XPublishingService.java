package com.flux.fluxproject.services.X;


import com.flux.fluxproject.model.XPostResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class XPublishingService {

    private final XPostService xPostService;

    public Mono<XPostResponse> publishText(
            UUID userId,
            String text
    ) {
        return xPostService.postTextWithAutoRefresh(
                userId,
                text
        );
    }
}