package com.flux.fluxproject.storage.util;

import com.flux.fluxproject.storage.exception.UnsupportedContentTypeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContentTypeResolverTest {

    private ContentTypeResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ContentTypeResolverImpl();
    }

    @Test
    void shouldResolveJpegExtension() {

        String extension = resolver.resolveExtension("image/jpeg");

        assertEquals("jpg", extension);
    }

    @Test
    void shouldResolvePngExtension() {

        String extension = resolver.resolveExtension("image/png");

        assertEquals("png", extension);
    }

    @Test
    void shouldResolveWebpExtension() {

        String extension = resolver.resolveExtension("image/webp");

        assertEquals("webp", extension);
    }

    @Test
    void shouldResolveGifExtension() {

        String extension = resolver.resolveExtension("image/gif");

        assertEquals("gif", extension);
    }

    @Test
    void shouldThrowForUnsupportedContentType() {

        assertThrows(
                UnsupportedContentTypeException.class,
                () -> resolver.resolveExtension("application/pdf")
        );
    }
}