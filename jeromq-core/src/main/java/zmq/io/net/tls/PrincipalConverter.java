package zmq.io.net.tls;

import java.util.Optional;

import javax.net.ssl.SSLSession;

/**
 * This class will be used to extract an identity from the current {@link SSLSession} and it will be stored in the "User-Id"
 * metadata attribute.
 */
@FunctionalInterface
public interface PrincipalConverter {
    Optional<String> getPrincipal(SSLSession session);
}
