package com.flux.fluxproject.services.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
public class EncryptionUtil {

    private static final String AES = "AES";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int IV_LENGTH = 12;       // 96 bits

    private final SecretKey secretKey;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EncryptionUtil(@Value("${aes.secret-key}") String base64Key) {
        if (base64Key == null) {
            throw new IllegalStateException("Missing AES secret key! Did you set aes.secret-key in application.properties?");
        }
        byte[] decoded = Base64.getDecoder().decode(base64Key);
        this.secretKey = new SecretKeySpec(decoded, AES);
    }

    public String encrypt(Map<String, Object> data) throws Exception {
        String json = objectMapper.writeValueAsString(data);

        Cipher cipher = Cipher.getInstance(AES_GCM);
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        byte[] cipherText = cipher.doFinal(json.getBytes(StandardCharsets.UTF_8));

        ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
        byteBuffer.put(iv);
        byteBuffer.put(cipherText);

        return Base64.getEncoder().encodeToString(byteBuffer.array());
    }

    public Map<String, Object> decrypt(String encrypted) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(encrypted);

        ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
        byte[] iv = new byte[IV_LENGTH];
        byteBuffer.get(iv);

        byte[] cipherText = new byte[byteBuffer.remaining()];
        byteBuffer.get(cipherText);

        Cipher cipher = Cipher.getInstance(AES_GCM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

        byte[] plainText = cipher.doFinal(cipherText);
        String json = new String(plainText, StandardCharsets.UTF_8);

        return objectMapper.readValue(json, new TypeReference<>() {});
    }
}