package com.inkspace.common.util;

import com.inkspace.config.JwtProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户 API Key 落库前必须能稳定加解密，且换 jwt.secret 就读不出来（当前按"未配置"处理）。
 */
class SecretCipherTest {

    private static SecretCipher cipher(String secret) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        return new SecretCipher(properties);
    }

    @Test
    void roundTripKeepsPlainText() {
        SecretCipher cipher = cipher("0123456789abcdef0123456789abcdef");
        String apiKey = "sk-abcdefghijklmnopqrstuvwxyz0123456789";

        String encrypted = cipher.encrypt(apiKey);

        assertNotEquals(apiKey, encrypted, "密文不能等于明文");
        assertTrue(encrypted.length() < 512, "密文要能放进 varchar(512)");
        assertEquals(apiKey, cipher.decrypt(encrypted));
    }

    @Test
    void sameInputProducesDifferentCipherText() {
        SecretCipher cipher = cipher("0123456789abcdef0123456789abcdef");

        assertNotEquals(cipher.encrypt("sk-same"), cipher.encrypt("sk-same"), "随机 nonce 应让两次密文不同");
    }

    @Test
    void differentSecretCannotDecrypt() {
        String encrypted = cipher("0123456789abcdef0123456789abcdef").encrypt("sk-secret");

        assertEquals("", cipher("ffffffffffffffffffffffffffffffff").decrypt(encrypted));
    }

    @Test
    void blankAndGarbageAreHandled() {
        SecretCipher cipher = cipher("0123456789abcdef0123456789abcdef");

        assertEquals("", cipher.encrypt(""));
        assertEquals("", cipher.decrypt(""));
        assertEquals("", cipher.decrypt("not-base64-@@@"));
    }
}
