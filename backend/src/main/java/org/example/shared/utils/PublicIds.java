package org.example.shared.utils;

import java.security.SecureRandom;

public final class PublicIds {
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private PublicIds() {}

    public static String generate(String prefix, int length) {
        char[] chars = new char[length];
        for (int i = 0; i < length; i++) {
            chars[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return prefix + "-" + new String(chars);
    }
}
