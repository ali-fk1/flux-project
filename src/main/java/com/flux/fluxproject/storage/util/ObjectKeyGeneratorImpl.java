package com.flux.fluxproject.storage.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ObjectKeyGeneratorImpl implements ObjectKeyGenerator {

    private static final String POSTS_DIRECTORY = "posts";
    private static final String ORIGINAL_DIRECTORY = "original";

    private final ContentTypeResolver contentTypeResolver;

    @Override
    public String generateObjectKey(UUID postId, String contentType) {

        String extension =
                contentTypeResolver.resolveExtension(contentType);

        String filename =
                UUID.randomUUID() + "." + extension;

        return String.format(
                "%s/%s/%s/%s",
                POSTS_DIRECTORY,
                postId,
                ORIGINAL_DIRECTORY,
                filename
        );
    }
}
