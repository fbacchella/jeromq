package zmq.io.mechanism.curve;

import java.util.Base64;

import com.neilalexander.jnacl.crypto.curve25519xsalsa20poly1305;

import zmq.Options;
import zmq.io.SessionBase;
import zmq.io.mechanism.Mechanism;
import zmq.io.mechanism.MechanismSettings;
import zmq.io.mechanism.Mechanisms;
import zmq.io.mechanism.curve.Curve.Size;
import zmq.io.net.Address;
import zmq.util.Z85;

import static zmq.io.mechanism.curve.Curve.CURVE_KEYSIZE;
import static zmq.io.mechanism.curve.Curve.CURVE_KEYSIZE_BASE64;
import static zmq.io.mechanism.curve.Curve.CURVE_KEYSIZE_Z85;

public class CurveMechanismSettings implements MechanismSettings<CurveMechanismSettings>
{
    public static class Builder
    {
        private byte[] publicKey;
        private byte[] secretKey;
        private byte[] serverKey;
        public Builder setPublicKey(Object optval)
        {
            publicKey = curveKey(optval);
            return this;
        }
        public Builder setSecretKey(Object optval)
        {
            secretKey = curveKey(optval);
            return this;
        }
        public Builder setServerKey(Object optval)
        {
            if (optval != null) {
                serverKey = curveKey(optval);
            }
            else {
                serverKey = null;
            }
            return this;
        }
        public Builder generateKey()
        {
            publicKey = new byte[Size.PUBLICKEY.bytes()];
            secretKey = new byte[Size.SECRETKEY.bytes()];
            curve25519xsalsa20poly1305.crypto_box_keypair(publicKey, secretKey);
            return this;
        }
        private byte[] curveKey(Object optval)
        {
            byte[] key = null;
            // if the optval is already the key don't do any parsing
            if (optval instanceof byte[] && ((byte[]) optval).length == CURVE_KEYSIZE) {
                key = (byte[]) optval;
            }
            else if (optval instanceof String) {
                String val = (String) optval;
                int length = val.length();
                if (length == CURVE_KEYSIZE_Z85) {
                    key = Z85.decode(val);
                }
                else if (length == CURVE_KEYSIZE_BASE64) {
                    key = Base64.getDecoder().decode(val);
                }
            }
            return key;
        }
        public CurveMechanismSettings build()
        {
            return new CurveMechanismSettings(this);
        }
    }

    public static Builder getBuilder()
    {
        return new Builder();
    }

    // No default, as an array can't really be a static final
    private final byte[] publicKey;
    private final byte[] secretKey;
    private final byte[] serverKey;

    public CurveMechanismSettings(Builder builder)
    {
        this.publicKey = builder.publicKey;
        this.secretKey = builder.secretKey;
        this.serverKey = builder.serverKey;
        assert (publicKey != null && publicKey.length == Curve.Size.PUBLICKEY.bytes());
        assert (secretKey != null && secretKey.length == Curve.Size.SECRETKEY.bytes());
        // assert (serverKey == null || serverKey.length == Curve.Size.PUBLICKEY.bytes());
    }

    @Override
    public Mechanisms getMechanism()
    {
        return Mechanisms.CURVE;
    }

    @Override
    public CurveMechanismSettings resolve()
    {
        return this;
    }

    public byte[] publicKey()
    {
        return publicKey;
    }

    public byte[] serverKey()
    {
        return serverKey;
    }

    public byte[] secretKey()
    {
        return secretKey;
    }

    public boolean isServer()
    {
        return serverKey == null;
    }

    @Override
    public Mechanism create(SessionBase session, Address<?> peerAddress, Options options)
    {
        if (serverKey == null) {
            return new CurveServerMechanism(session, peerAddress, this, options);
        }
        else {
            return new CurveClientMechanism(session, this, options);
        }
    }

    @Override
    public String name()
    {
        return "CURVE";
    }
}
