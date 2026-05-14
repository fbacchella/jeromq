package org.zeromq;

import java.util.Base64;
import java.util.regex.Pattern;

import zmq.util.Z85;

/**
 * Utility class for detecting whether a string is encoded in Base64,
 * Base64 URL-safe, or Z85 (RFC 7238).
 *
 * <p>Detection relies on three successive criteria:
 * <ol>
 *   <li>Alphabet membership — some characters are exclusive to one encoding.</li>
 *   <li>Length constraints — Base64 requires a multiple of 4, Z85 a multiple of 5.</li>
 *   <li>Ambiguity resolution — purely alphanumeric strings may satisfy both.</li>
 * </ol>
 */
public final class EncodingDetector
{
    /**
     * Possible outcomes of the detection algorithm.
     */
    public enum Encoding {
        /** Standard Base64 as defined in RFC 4648 (uses {@code +} and {@code /}). */
        BASE64,
        /** URL-safe Base64 variant (uses {@code -} and {@code _} instead of {@code +} and {@code /}). */
        BASE64_URL,
        /** Z85 encoding as defined in RFC 7238. */
        Z85,
        /**
         * The string is purely alphanumeric and its length is a multiple of both 4 and 5
         * (i.e. a multiple of 20). Neither encoding can be ruled out on structural grounds alone.
         */
        AMBIGUOUS,
        /** The string does not match any known encoding, or its length violates the encoding's constraints. */
        UNKNOWN
    }

    /**
     * Standard Base64 alphabet (RFC 4648 §4).
     * Accepts an optional padding suffix of one or two {@code =} characters.
     */
    private static final Pattern BASE64_PATTERN =
            Pattern.compile("^[A-Za-z0-9+/]*+={0,2}$");

    /**
     * URL-safe Base64 alphabet (RFC 4648 §5).
     * Substitutes {@code -} for {@code +} and {@code _} for {@code /}.
     * Padding is optional in this variant.
     */
    private static final Pattern BASE64_URL_PATTERN =
            Pattern.compile("^[A-Za-z0-9\\-_]*+={0,2}$");

    /**
     * Z85 alphabet (RFC 7238).
     * Comprises exactly 85 printable ASCII characters.
     * No padding is defined; the encoded length must be an exact multiple of 5.
     */
    private static final Pattern Z85_PATTERN =
            Pattern.compile("^[0-9a-zA-Z.\\-:+=^!/*?&<>()\\[\\]{}@%$#]++$");

    /**
     * Purely alphanumeric subset — common to all three encodings above.
     * Strings matching this pattern are structurally ambiguous.
     */
    private static final Pattern ALPHANUMERIC_PATTERN =
            Pattern.compile("^[A-Za-z0-9]++$");

    /** Prevent instantiation. */
    private EncodingDetector() {}

    /**
     * Analyses the given string and returns the most likely encoding.
     *
     * <p>The algorithm proceeds as follows:
     * <ol>
     *   <li>If the string contains characters exclusive to Z85 (e.g. {@code !}, {@code ?},
     *       {@code *}, {@code &}, {@code <}, {@code >}, brackets, …) and its length is a
     *       multiple of 5, it is classified as {@link Encoding#Z85}.</li>
     *   <li>If it contains {@code +} or {@code /} (exclusive to standard Base64) and its
     *       length is a multiple of 4, it is classified as {@link Encoding#BASE64}.</li>
     *   <li>If it contains {@code -} or {@code _} (exclusive to URL-safe Base64) and its
     *       length is a multiple of 4, it is classified as {@link Encoding#BASE64_URL}.</li>
     *   <li>If it is purely alphanumeric, length constraints are used as a tiebreaker;
     *       if both are satisfied (multiple of 20), {@link Encoding#AMBIGUOUS} is returned.</li>
     *   <li>Otherwise {@link Encoding#UNKNOWN} is returned.</li>
     * </ol>
     *
     * @param  input the string to analyse; must not be {@code null}
     * @return the detected encoding
     * @throws IllegalArgumentException if {@code input} is {@code null}
     */
    public static Encoding detect(String input)
    {
        if (input == null) {
            throw new IllegalArgumentException("Input must not be null.");
        }
        if (input.isEmpty()) {
            return Encoding.BASE64;
        }

        int length = input.length();

        // Length validity flags
        boolean validLengthForBase64 = (length % 4 == 0);
        boolean validLengthForZ85    = (length % 5 == 0);

        // Alphabet membership flags
        boolean matchesBase64    = BASE64_PATTERN.matcher(input).matches();
        boolean matchesBase64Url = BASE64_URL_PATTERN.matcher(input).matches();
        boolean matchesZ85       = Z85_PATTERN.matcher(input).matches();
        boolean isAlphanumeric   = ALPHANUMERIC_PATTERN.matcher(input).matches();

        // ------------------------------------------------------------------
        // Branch 1 — unambiguous Z85
        // The string contains at least one character that belongs to Z85 but
        // not to either Base64 variant (e.g. !, ?, *, &, <, >, brackets …).
        // ------------------------------------------------------------------
        if (matchesZ85 && !matchesBase64 && !matchesBase64Url) {
            // Characters are valid Z85, but the length constraint is violated:
            // the frame is corrupt or truncated.
            if (!validLengthForZ85) {
                return Encoding.UNKNOWN;
            }
            else {
                return Encoding.Z85;
            }
        }

        // ------------------------------------------------------------------
        // Branch 2 — unambiguous standard Base64
        // The string contains + or /, which are absent from both Z85 and the
        // URL-safe variant.  (Z85 does include + and -, but not / alone.)
        // ------------------------------------------------------------------
        if (matchesBase64 && !matchesZ85) {
            return validLengthForBase64 ? Encoding.BASE64 : Encoding.UNKNOWN;
        }

        // ------------------------------------------------------------------
        // Branch 3 — unambiguous URL-safe Base64
        // The string contains - or _, which are absent from standard Base64
        // and from Z85.
        // ------------------------------------------------------------------
        if (matchesBase64Url && !matchesBase64 && !matchesZ85) {
            return validLengthForBase64 ? Encoding.BASE64_URL : Encoding.UNKNOWN;
        }

        // ------------------------------------------------------------------
        // Branch 4 — purely alphanumeric: use length as a tiebreaker
        // ------------------------------------------------------------------
        if (isAlphanumeric) {
            if  (validLengthForZ85 && !validLengthForBase64) {
                return Encoding.Z85;
            }
            else if  (validLengthForBase64 && !validLengthForZ85) {
                return Encoding.BASE64;
            }
            else if  (validLengthForBase64) {
                // Length is a multiple of both 4 and 5 (i.e. a multiple of 20).
                return Encoding.AMBIGUOUS;
            }
            else {
                // Length satisfies neither constraint.
                return Encoding.UNKNOWN;
            }
        }

        return Encoding.UNKNOWN;
    }

    /**
     * Decodes the given string using the encoding detected by {@link #detect(String)}.
     *
     * @param  value the encoded string to decode; must not be {@code null}
     * @return the decoded bytes
     * @throws IllegalArgumentException if {@code value} is {@code null}, its encoding is
     *                                  {@link Encoding#UNKNOWN}, or its encoding is
     *                                  {@link Encoding#AMBIGUOUS} and cannot be resolved
     */
    public static byte[] decode(String value)
    {
        switch (detect(value)) {
            case BASE64:
                return Base64.getDecoder().decode(value);
            case BASE64_URL:
                return Base64.getUrlDecoder().decode(value);
            case Z85:
                return Z85.decode(value);
            case AMBIGUOUS:
                throw new IllegalArgumentException("Encoding is ambiguous (alphanumeric, multiple of 20): " + value);
            default:
                throw new IllegalArgumentException("Unable to detect encoding for value: " + value);
        }
    }
}
