package com.flux.fluxproject.storage.util;

import java.util.UUID;

public interface ObjectKeyGenerator {

    String generateObjectKey(
            UUID postId,
            String contentType
    );

}