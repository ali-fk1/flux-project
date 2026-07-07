package com.flux.fluxproject.storage.util;

import com.flux.fluxproject.storage.exception.UnsupportedContentTypeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectKeyGeneratorTest {

    private ObjectKeyGenerator generator;

    @BeforeEach
    void setUp() {

        generator = new ObjectKeyGeneratorImpl(
                new ContentTypeResolverImpl()
        );
    }

    @Test
    void shouldGenerateObjectKey() {

        UUID postId = UUID.randomUUID();

        String key = generator.generateObjectKey(
                postId,
                "image/jpeg"
        );

        assertTrue(
                key.startsWith(
                        "posts/" + postId + "/original/"
                )
        );

        assertTrue(
                key.endsWith(".jpg")
        );
    }

    @Test
    void shouldThrowForUnsupportedContentType() {
        UUID postId = UUID.randomUUID();

        assertThrows(
                UnsupportedContentTypeException.class,
                () -> generator.generateObjectKey(postId, "application/pdf")
        );
    }
}