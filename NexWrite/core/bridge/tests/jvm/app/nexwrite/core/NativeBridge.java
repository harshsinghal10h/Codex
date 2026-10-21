// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
package app.nexwrite.core;

import java.nio.charset.StandardCharsets;

/** Host JVM probe for the exact byte-array JNI ABI; this is not an Android test. */
public final class NativeBridge {
    private static final byte[] LOCALE = bytes("en-US");
    private static final byte[] PLAIN = bytes("plain");
    private static final long MAX_VERSION = 9_007_199_254_740_991L;

    static {
        System.loadLibrary("nw_bridge");
    }

    public static native byte[] analyzeUtf8(
        byte[] text, long docVersion, byte[] locale, byte[] inputFormat
    );

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static void check(boolean value, String category) {
        if (!value) throw new AssertionError(category);
    }

    private static void rejects(Runnable action, String expected) {
        try {
            action.run();
        } catch (IllegalArgumentException error) {
            check(expected.equals(error.getMessage()), "fixed validation message");
            return;
        }
        throw new AssertionError("invalid input was accepted");
    }

    public static void main(String[] args) {
        String text = "😀 e\u0301 `a  b` clear  words\r\nאבג";
        String json = new String(
            analyzeUtf8(bytes(text), MAX_VERSION, LOCALE, bytes("markdown")),
            StandardCharsets.UTF_8
        );
        int start = text.lastIndexOf("  ");
        int utf8Start = bytes(text.substring(0, start)).length;
        int scalarStart = text.codePointCount(0, start);
        check(json.contains("\"doc_version\":" + MAX_VERSION), "portable version preserved");
        check(json.contains("\"input_format\":\"markdown\""), "source format preserved");
        check(json.contains("\"utf8\":[" + utf8Start + "," + (utf8Start + 2) + "]"), "UTF-8 offsets");
        check(json.contains("\"utf16\":[" + start + "," + (start + 2) + "]"), "UTF-16 offsets");
        check(json.contains("\"scalar\":[" + scalarStart + "," + (scalarStart + 2) + "]"), "scalar offsets");
        check(json.contains("\"rule_id\":\"NW-SPACE-001\""), "actual bundled rule");
        check(json.split("\"rule_id\":", -1).length - 1 == 1, "protected source has no rule result");
        String empty = new String(analyzeUtf8(new byte[0], 0, LOCALE, PLAIN), StandardCharsets.UTF_8);
        check(empty.contains("\"suggestions\":[]"), "empty source result");

        for (byte[] invalid : new byte[][] {
            {(byte) 0xff}, {(byte) 0xc0, (byte) 0xaf},
            {(byte) 0xed, (byte) 0xa0, (byte) 0x80},
            {(byte) 0xf0, (byte) 0x9f, (byte) 0x98},
        }) {
            rejects(() -> analyzeUtf8(invalid, 0, LOCALE, PLAIN), "analysis arguments must contain valid UTF-8");
        }
        for (long invalid : new long[] {-1, MAX_VERSION + 1, Long.MAX_VALUE}) {
            rejects(() -> analyzeUtf8(new byte[0], invalid, LOCALE, PLAIN), "document version must be a nonnegative safe integer");
        }
        rejects(() -> analyzeUtf8(new byte[1_048_577], 0, LOCALE, PLAIN), "analysis input exceeds 1048576 UTF-8 byte limit");
        rejects(() -> analyzeUtf8(new byte[0], 0, bytes("en-US-private"), PLAIN), "analysis supports en-US only");
        rejects(() -> analyzeUtf8(new byte[0], 0, LOCALE, bytes("html")), "input format must be plain or markdown");
        rejects(() -> analyzeUtf8(null, 0, LOCALE, PLAIN), "analysis arguments must not be null");
        rejects(() -> analyzeUtf8(new byte[0], 0, null, PLAIN), "analysis arguments must not be null");
        rejects(() -> analyzeUtf8(new byte[0], 0, LOCALE, null), "analysis arguments must not be null");
        // Exceptions must leave the native boundary usable for the next call.
        check(analyzeUtf8(bytes("very clear"), 1, LOCALE, PLAIN).length > 0, "native boundary recovered");
        System.out.println("Host JNI smoke passed: canonical Unicode/Markdown transport and malformed/size/version/null boundaries.");
    }
}
