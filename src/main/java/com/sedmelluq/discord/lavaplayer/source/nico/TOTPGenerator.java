package com.sedmelluq.discord.lavaplayer.source.nico;/*
 *  Copyright 2024 Cosgy Dev (info@cosgy.dev).
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

public class TOTPGenerator {

    private static final int DIGIT = 6;
    private static final int TIME_STEP = 30;
    private static final Map<Character, Integer> base32table = new HashMap<>();

    static {
        base32table.put('A', 0); base32table.put('B', 1); base32table.put('C', 2); base32table.put('D', 3);
        base32table.put('E', 4); base32table.put('F', 5); base32table.put('G', 6); base32table.put('H', 7);
        base32table.put('I', 8); base32table.put('J', 9); base32table.put('K', 10); base32table.put('L', 11);
        base32table.put('M', 12); base32table.put('N', 13); base32table.put('O', 14); base32table.put('P', 15);
        base32table.put('Q', 16); base32table.put('R', 17); base32table.put('S', 18); base32table.put('T', 19);
        base32table.put('U', 20); base32table.put('V', 21); base32table.put('W', 22); base32table.put('X', 23);
        base32table.put('Y', 24); base32table.put('Z', 25); base32table.put('2', 26); base32table.put('3', 27);
        base32table.put('4', 28); base32table.put('5', 29); base32table.put('6', 30); base32table.put('7', 31);
    }

    // Main method to get the TOTP code
    public static String getCode(String key) {
        byte[] decodedKey = base32decode(key.toUpperCase());
        return totp(decodedKey, Instant.now());
    }

    // TOTP generation function
    private static String totp(byte[] key, Instant instant) {
        long counter = instant.getEpochSecond() / TIME_STEP;
        return hotp(key, counter);
    }

    // HOTP generation function
    private static String hotp(byte[] key, long counter) {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(counter);

        byte[] hash = hmacSha1(key, buffer.array());
        int truncatedHash = truncate(hash);
        return String.format("%06d", truncatedHash);
    }

    // Truncate function as defined in RFC 4226
    private static int truncate(byte[] hash) {
        int offset = hash[hash.length - 1] & 0xf;
        int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);
        return binary % (int) Math.pow(10, DIGIT);
    }

    // HMAC-SHA1 calculation
    private static byte[] hmacSha1(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key, "HmacSHA1");
            mac.init(secretKeySpec);
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    // Base32 decoding function
    private static byte[] base32decode(String str) {
        str = str.replaceAll("[^A-Z2-7]", ""); // Only keep valid base32 characters
        int outputLength = str.length() * 5 / 8;
        byte[] result = new byte[outputLength];

        int buffer = 0;
        int bitsLeft = 0;
        int index = 0;

        for (char c : str.toCharArray()) {
            buffer <<= 5;
            buffer |= base32table.get(c) & 31;
            bitsLeft += 5;

            if (bitsLeft >= 8) {
                result[index++] = (byte) (buffer >> (bitsLeft - 8));
                bitsLeft -= 8;
            }
        }

        return result;
    }

    public static void main(String[] args) {
        // Example usage
        Scanner scanner = new Scanner(System.in);
        System.out.println("二段階認証のシークレットキーを入力してください。");
        String secretKey = scanner.next(); // Sample Base32 secret key

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                String code = getCode(secretKey);
                long current = (Instant.now().getEpochSecond() % TIME_STEP);
                String gauge = "=".repeat((int) current) + "-".repeat(TIME_STEP - (int) current);
                System.out.printf("\rTOTP Code: %s [%s] %s sec", code, gauge, TIME_STEP - (int) current);
            }
        }, 0, 500); // 0ミリ秒で開始し、500ミリ秒ごとに更新
    }
}

