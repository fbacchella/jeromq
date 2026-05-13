package org.zeromq;

import zmq.util.Z85;

import static zmq.io.mechanism.curve.Curve.CURVE_KEYSIZE;
import static zmq.io.mechanism.curve.Curve.CURVE_KEYSIZE_Z85;

/**
 * Class that interfaces the generation of CURVE key pairs.
 *
 * <p>The CURVE mechanism defines a mechanism for secure authentication and confidentiality for communications between a client and a server.
 * CURVE is intended for use on public networks.
 * The CURVE mechanism is defined by this document: http://rfc.zeromq.org/spec:25.</p>
 *
 * <h2>Client and server roles</h2>
 *
 * <p>A socket using CURVE can be either client or server, at any moment, but not both. The role is independent of bind/connect direction.
 * A socket can change roles at any point by setting new options. The role affects all connect and bind calls that follow it.</p>
 *
 * <p>To become a CURVE server, the application sets the {@link ZMQ.Socket#setAsServerCurve(boolean)} option on the socket,
 * and then sets the {@link ZMQ.Socket#setCurveSecretKey(byte[])} option to provide the socket with its long-term secret key.
 * The application does not provide the socket with its long-term public key, which is used only by clients.</p>
 *
 * <p>To become a CURVE client, the application sets the {@link ZMQ.Socket#setCurveServerKey(byte[])} option
 * with the long-term public key of the server it intends to connect to, or accept connections from, next.
 * The application then sets the {@link ZMQ.Socket#setCurvePublicKey(byte[])} and {@link ZMQ.Socket#setCurveSecretKey(byte[])} options with its client long-term key pair.
 * If the server does authentication it will be based on the client's long term public key.</p>
 *
 * <h3>Key encoding</h3>
 *
 * <p>The standard representation for keys in source code is either 32 bytes of base 256 (binary) data,
 * or 40 characters of base 85 data encoded using the Z85 algorithm defined by http://rfc.zeromq.org/spec:32.
 * The Z85 algorithm is designed to produce printable key strings for use in configuration files, the command line, and code.
 * There is a reference implementation in C at https://github.com/zeromq/rfc/tree/master/src.</p>
 *
 * <h3>Test key values</h3>
 *
 * <p>For test cases, the client shall use this long-term key pair (specified as hexadecimal and in Z85):</p>
 * <ul>
 * <li>public:
 * <p>BB88471D65E2659B30C55A5321CEBB5AAB2B70A398645C26DCA2B2FCB43FC518</p>
 * <p>{@code Yne@$w-vo<fVvi]a<NY6T1ed:M$fCG*[IaLV{hID}</p>
 * </li>
 * <li>secret:
 * <p>7BB864B489AFA3671FBE69101F94B38972F24816DFB01B51656B3FEC8DFD0888</p>
 * <p>{@code D:)Q[IlAW!ahhC2ac:9*A}h:p?([4%wOTJ%JR%cs}</p>
 * </li>
 * </ul>
 *
 * <p>And the server shall use this long-term key pair (specified as hexadecimal and in Z85):</p>
 * <ul>
 * <li>public:
 * <p>54FCBA24E93249969316FB617C872BB0C1D1FF14800427C594CBFACF1BC2D652</p>
 * <p>{@code rq:rM>}U?@Lns47E1%kR.o@n%FcmmsL/@{H8]yf7}</p>
 * </li>
 * <li>secret:
 * <p>8E0BDD697628B91D8F245587EE95C5B04D48963F79259877B49CD9063AEAD3B7</p>
 * <p>{@code JTKVSB%%)wK0E.X)V>+}o?pNmC{O&amp;4W4b!Ni{Lh6}</p>
 * </li>
 * </ul>
 */
public class Curve
{
    public static final int KEY_SIZE = CURVE_KEYSIZE;
    public static final int KEY_SIZE_Z85 = CURVE_KEYSIZE_Z85;

    private Curve()
    {
        // Private constructor
    }

    /**
     * <p>Returns a newly generated random keypair consisting of a public key
     * and a secret key.</p>
     *
     * <p>The keys are encoded using {@link #z85Encode}.</p>
     *
     * @return Randomly generated {@link KeyPair}
     */
    public static KeyPair generateKeyPair()
    {
        String[] keys = new zmq.io.mechanism.curve.Curve().keypairZ85();
        return new KeyPair(keys[0], keys[1]);
    }

    /**
     * <p>The function shall decode given key encoded as Z85 string into byte array.</p>
     * <p>The length of string shall be divisible by 5.</p>
     * <p>The decoding shall follow the ZMQ RFC 32 specification.</p>
     *
     * @param key Key to be decoded
     * @return The resulting key as byte array
     */
    public static byte[] z85Decode(String key)
    {
        return Z85.decode(key);
    }

    /**
     * <p>Encodes the binary block specified by data into a string.</p>
     * <p>The size of the binary block must be divisible by 4.</p>
     * <p>A 32-byte CURVE key is encoded as 40 ASCII characters plus a null terminator.</p>
     * <p>The function shall encode the binary block specified into a string.</p>
     * <p>The encoding shall follow the ZMQ RFC 32 specification.</p>
     *
     * @param key Key to be encoded
     * @return The resulting key as String in Z85
     */
    public static String z85Encode(byte[] key)
    {
        return zmq.io.mechanism.curve.Curve.z85EncodePublic(key);
    }

    /**
     * A container for a public and a corresponding secret key.
     * Keys have to be encoded in Z85 format.
     */
    public static class KeyPair
    {
        /**
         * Z85-encoded public key.
         */
        public final String publicKey;

        /**
         * Z85-encoded secret key.
         */
        public final String secretKey;

        public KeyPair(String publicKey, String secretKey)
        {
            Utils.checkArgument(publicKey != null, "Public key cannot be null");
            Utils.checkArgument(publicKey.length() == Curve.KEY_SIZE_Z85, "Public key has to be Z85 format");
            Utils.checkArgument(secretKey == null || secretKey.length() == Curve.KEY_SIZE_Z85,
                    "Secret key has to be null or in Z85 format");
            this.publicKey = publicKey;
            this.secretKey = secretKey;
        }
    }
}
