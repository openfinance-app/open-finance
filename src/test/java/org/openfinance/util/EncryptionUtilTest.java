package org.openfinance.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Encryption encoding preserves bytes and rejects corrupt key material")
class EncryptionUtilTest {
    @ParameterizedTest
    @ValueSource(ints = {16, 24, 32})
    void acceptsSupportedAesKeysWithoutClearingTheReturnedKey(int length) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) 0xA5);
        SecretKey key =
                EncryptionUtil.decodeEncryptionKey(
                        "  " + Base64.getEncoder().encodeToString(bytes) + "\n");
        assertThat(key.getAlgorithm()).isEqualTo("AES");
        assertThat(key.getEncoded()).containsExactly(bytes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not base64!", "AA==", "AAAAAAAAAAAAAAAAAAAAAAAAAAA="})
    void refusesMalformedOrUnsupportedKeys(String key) {
        assertThatThrownBy(() -> EncryptionUtil.decodeEncryptionKey(key))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid encryption key format");
    }

    @Test
    void base64RoundTripsBinaryDataAndRejectsNullOrCorruption() {
        byte[] bytes = {0, 1, 15, 16, 127, (byte) 128, (byte) 255};
        assertThat(EncryptionUtil.decodeBase64(EncryptionUtil.encodeBase64(bytes)))
                .containsExactly(bytes);
        assertThat(EncryptionUtil.decodeBase64("")).isEmpty();
        assertThatThrownBy(() -> EncryptionUtil.encodeBase64(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EncryptionUtil.decodeBase64(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EncryptionUtil.decodeBase64("%%%%"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void utf8ConversionHandlesMultibytePasswordsWithoutChangingTheSource() {
        char[] password = "été 中文 🔐".toCharArray();
        char[] original = password.clone();
        byte[] expected = new String(password).getBytes(StandardCharsets.UTF_8);
        byte[] bytes = EncryptionUtil.charArrayToBytes(password);
        assertThat(bytes).containsExactly(expected);
        assertThat(EncryptionUtil.bytesToCharArray(bytes)).containsExactly(password);
        assertThat(password).containsExactly(original);
        assertThat(bytes).containsExactly(expected);
        assertThat(EncryptionUtil.charArrayToBytes(new char[0])).isEmpty();
        assertThat(EncryptionUtil.bytesToCharArray(new byte[0])).isEmpty();
        assertThatThrownBy(() -> EncryptionUtil.charArrayToBytes(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EncryptionUtil.bytesToCharArray(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clearsSensitiveBuffersInPlaceAndToleratesAbsentBuffers() {
        byte[] bytes = {1, 2, 3};
        char[] chars = {'s', 'e', 'c'};
        EncryptionUtil.clearArray(bytes);
        EncryptionUtil.clearArray(chars);
        EncryptionUtil.clearArray((byte[]) null);
        EncryptionUtil.clearArray((char[]) null);
        assertThat(bytes).containsExactly(0, 0, 0);
        assertThat(chars).containsExactly('\0', '\0', '\0');
    }

    @Test
    void comparisonDetectsDifferencesAtEveryPositionAndLength() {
        assertThat(EncryptionUtil.constantTimeEquals(null, null)).isTrue();
        assertThat(EncryptionUtil.constantTimeEquals(null, new byte[0])).isFalse();
        assertThat(EncryptionUtil.constantTimeEquals(new byte[0], null)).isFalse();
        byte[] value = {1, 2, 3};
        assertThat(EncryptionUtil.constantTimeEquals(value, value.clone())).isTrue();
        assertThat(EncryptionUtil.constantTimeEquals(value, new byte[] {1, 2})).isFalse();
        for (int index = 0; index < value.length; index++) {
            byte[] changed = value.clone();
            changed[index]++;
            assertThat(EncryptionUtil.constantTimeEquals(value, changed)).isFalse();
        }
    }

    @Test
    void producesFreshSaltAndRandomMaterialOfTheRequestedSize() {
        assertThat(EncryptionUtil.generateSalt(32))
                .hasSize(32)
                .isNotEqualTo(EncryptionUtil.generateSalt(32));
        assertThat(EncryptionUtil.generateRandomBytes(24))
                .hasSize(24)
                .isNotEqualTo(EncryptionUtil.generateRandomBytes(24));
        assertThatThrownBy(() -> EncryptionUtil.generateSalt(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EncryptionUtil.generateRandomBytes(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesRequiredBufferSizes() {
        EncryptionUtil.validateByteArray(new byte[16], 16, "iv");
        assertThatThrownBy(() -> EncryptionUtil.validateByteArray(null, 16, "iv"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("iv");
        assertThatThrownBy(() -> EncryptionUtil.validateByteArray(new byte[12], 16, "iv"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("16 bytes");
    }

    @Test
    void hexConversionPreservesLeadingZeroesAndUnsignedBytes() {
        byte[] bytes = {0, 15, 16, (byte) 255};
        assertThat(EncryptionUtil.bytesToHex(bytes)).isEqualTo("000f10ff");
        assertThat(EncryptionUtil.hexToBytes("000F10fF")).containsExactly(bytes);
        assertThat(EncryptionUtil.hexToBytes("")).isEmpty();
        assertThatThrownBy(() -> EncryptionUtil.bytesToHex(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EncryptionUtil.hexToBytes(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"f", "0g", "-1", "zz", " 0", "0!"})
    void invalidHexDigitsCannotSilentlyBecomeDifferentBytes(String hex) {
        assertThatThrownBy(() -> EncryptionUtil.hexToBytes(hex))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
