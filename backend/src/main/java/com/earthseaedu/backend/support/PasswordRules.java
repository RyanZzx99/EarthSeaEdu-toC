package com.earthseaedu.backend.support;

import cn.hutool.core.text.CharSequenceUtil;
import java.nio.charset.StandardCharsets;

public final class PasswordRules {

    private static final int PASSWORD_MIN_LENGTH = 8;
    private static final int PASSWORD_MAX_LENGTH = 24;
    private static final int BCRYPT_PASSWORD_MAX_BYTES = 72;
    private static final String SPECIAL_CHARS = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";

    private PasswordRules() {
    }

    public static void validatePasswordStrength(String password) {
        ensureBcryptPasswordLength(password);

        if (password == null || password.length() < PASSWORD_MIN_LENGTH || password.length() > PASSWORD_MAX_LENGTH) {
            throw new IllegalArgumentException("密码长度需要在 8-24 位");
        }

        if (password.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("密码不能包含空格或其他空白字符");
        }

        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(ch -> SPECIAL_CHARS.indexOf(ch) >= 0);
        int matchedTypes = (hasLetter ? 1 : 0) + (hasDigit ? 1 : 0) + (hasSpecial ? 1 : 0);
        if (matchedTypes < 2) {
            throw new IllegalArgumentException("密码至少需要包含字母、数字、特殊字符中的 2 种");
        }
    }

    public static void ensureBcryptPasswordLength(String password) {
        String value = CharSequenceUtil.nullToEmpty(password);
        if (value.getBytes(StandardCharsets.UTF_8).length > BCRYPT_PASSWORD_MAX_BYTES) {
            throw new IllegalArgumentException("密码长度不能超过 72 字节");
        }
    }
}
