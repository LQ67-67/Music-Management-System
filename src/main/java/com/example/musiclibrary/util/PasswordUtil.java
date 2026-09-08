package com.example.musiclibrary.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * Password hashing with PBKDF2-HMAC-SHA256 (JDK built-in, no extra dependency).
 *
 * Stored format:  pbkdf2:<iterations>:<base64 salt>:<base64 hash>
 * Legacy plaintext values (no prefix) are verified by direct comparison and can be
 * transparently upgraded via {@link #needsRehash}.
 */
public final class PasswordUtil {

    private static final int ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final String PREFIX = "pbkdf2";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() {
    }

    public static String hash(String plainPassword) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] derived = pbkdf2(plainPassword, salt, ITERATIONS);
        return "%s:%d:%s:%s".formatted(
                PREFIX, ITERATIONS,
                Base64.getEncoder().encodeToString(salt),
                Base64.getEncoder().encodeToString(derived));
    }

    public static boolean verify(String plainPassword, String stored) {
        if (plainPassword == null || stored == null) {
            return false;
        }
        if (!isHashed(stored)) {
            // legacy plaintext row: compare as-is (caller should rehash on success)
            return constantTimeEquals(plainPassword, stored);
        }

        String[] parts = stored.split(":");
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = pbkdf2(plainPassword, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** True when the stored value is not yet a PBKDF2 hash and should be upgraded. */
    public static boolean needsRehash(String stored) {
        return stored == null || !stored.startsWith(PREFIX + ":");
    }

    private static boolean isHashed(String stored) {
        return stored.startsWith(PREFIX + ":") && stored.split(":").length == 4;
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2 unavailable on this JVM", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(), b.getBytes());
    }
}
