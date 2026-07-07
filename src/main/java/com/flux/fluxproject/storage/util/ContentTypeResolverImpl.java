package com.flux.fluxproject.storage.util;

import com.flux.fluxproject.storage.exception.UnsupportedContentTypeException;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ContentTypeResolverImpl implements ContentTypeResolver {

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif"
    );

    @Override
    public String resolveExtension(String contentType) {

        String extension = CONTENT_TYPES.get(contentType);

        if (extension == null) {
            throw new UnsupportedContentTypeException(
                    "Unsupported content type: " + contentType
            );
        }

        return extension;
    }
}