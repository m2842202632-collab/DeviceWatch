package com.devicewatch.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/** 加盐 SHA-256 口令工具 */
public class PasswordUtil {
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String genSalt() {
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    public static String hash(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest((salt + password).getBytes(StandardCharsets.UTF_8));
            for (int i = 0; i < 1024; i++) {
                md.reset();
                d = md.digest(d);
            }
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean verify(String password, String salt, String hash) {
        return hash(password, salt).equals(hash);
    }
}
