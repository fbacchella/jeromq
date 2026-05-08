package zmq.io.mechanism;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import zmq.SocketBase;
import zmq.ZMQ;
import zmq.io.mechanism.plain.PlainMechanismSettings;

public class SecurityPlainTest
{
    private static class PlainTestContext extends MechanismTester.TestContext
    {
        String user;
        String password;
    }

    private Boolean runTest(boolean withzap, Function<PlainTestContext, Boolean> tested) throws InterruptedException
    {
        PlainTestContext testCtx = new PlainTestContext();
        testCtx.user = "admin";
        testCtx.password = "password";

        BiFunction<SocketBase, CompletableFuture<Boolean>, ZapHandler> zapProvider = (s, f) -> new ZapHandler(s, f, "admin", "password");
        Runnable configurator = () -> {
            ZMQ.setSocketOption(testCtx.server, ZMQ.ZMQ_MECHANISM, new PlainMechanismSettings(true, "", ""));
            ZMQ.setSocketOption(testCtx.server, ZMQ.ZMQ_IDENTITY, "IDENT");
            if (withzap) {
                ZMQ.setSocketOption(testCtx.server, ZMQ.ZMQ_ZAP_DOMAIN, "global");
                ZMQ.setSocketOption(testCtx.client, ZMQ.ZMQ_ZAP_DOMAIN, "global");
            }
        };

        return MechanismTester.runTest(testCtx, withzap, tested, zapProvider, configurator);
    }

    public boolean runValid(PlainTestContext tctxt)
    {
        boolean rc;

        rc = ZMQ.bind(tctxt.server, tctxt.host);
        Assertions.assertTrue(rc);

        String host = (String) ZMQ.getSocketOptionExt(tctxt.server, ZMQ.ZMQ_LAST_ENDPOINT);
        PlainMechanismSettings settings = new PlainMechanismSettings(false, tctxt.user, tctxt.password);
        ZMQ.setSocketOption(tctxt.client, ZMQ.ZMQ_MECHANISM, settings);

        rc = ZMQ.connect(tctxt.client, host);
        Assertions.assertTrue(rc);
        return true;
    }

    @Test
    @Timeout(5)
    public void testNoZap() throws InterruptedException
    {
        //  We first test client/server with no ZAP domain
        Boolean status = runTest(false, this::runValid);
        Assertions.assertNull(status);
    }

    @Test
    @Timeout(5)
    public void testZap() throws InterruptedException
    {
        //  We first test client/server with no ZAP domain
        Boolean status = runTest(true, this::runValid);
        Assertions.assertTrue(status);
    }

    @Test
    @Timeout(5)
    public void testZapInverted() throws InterruptedException
    {
        //  When ZAP is not used, always accept
        Boolean status = runTest(false, tctxt -> {
            boolean rc;

            PlainMechanismSettings settings = new PlainMechanismSettings(false, tctxt.user, tctxt.password);
            ZMQ.setSocketOption(tctxt.client, ZMQ.ZMQ_MECHANISM, settings);

            rc = ZMQ.bind(tctxt.client, tctxt.host);
            Assertions.assertTrue(rc);

            String host = (String) ZMQ.getSocketOptionExt(tctxt.client, ZMQ.ZMQ_LAST_ENDPOINT);

            rc = ZMQ.connect(tctxt.server, host);
            Assertions.assertTrue(rc);

            return true;
        });
        Assertions.assertNull(status);
    }

    @Test
    @Timeout(5)
    public void testBothServer() throws InterruptedException
    {
        //  We first test client/server with no ZAP domain
        Boolean status = runTest(true, tctxt -> {
            boolean rc;

            rc = ZMQ.bind(tctxt.server, tctxt.host);
            Assertions.assertTrue(rc);

            String host = (String) ZMQ.getSocketOptionExt(tctxt.server, ZMQ.ZMQ_LAST_ENDPOINT);
            PlainMechanismSettings settings = new PlainMechanismSettings(true, "", "");
            ZMQ.setSocketOption(tctxt.client, ZMQ.ZMQ_MECHANISM, settings);

            rc = ZMQ.connect(tctxt.client, host);
            Assertions.assertTrue(rc);
            return false;
        });
        Assertions.assertNull(status);
    }

    @Test
    @Timeout(5)
    public void testFailedLoginZap() throws InterruptedException
    {
        //  We first test client/server with no ZAP domain
        Boolean status = runTest(true, tctxt -> {
            boolean rc;

            rc = ZMQ.bind(tctxt.server, tctxt.host);
            Assertions.assertTrue(rc);

            String host = (String) ZMQ.getSocketOptionExt(tctxt.server, ZMQ.ZMQ_LAST_ENDPOINT);
            PlainMechanismSettings settings = new PlainMechanismSettings(false, "wronguser", "wrongpass");
            ZMQ.setSocketOption(tctxt.client, ZMQ.ZMQ_MECHANISM, settings);

            rc = ZMQ.connect(tctxt.client, host);
            Assertions.assertTrue(rc);
            return false;
        });
        Assertions.assertFalse(status);
    }

    @Test
    @Timeout(5)
    public void testSuccessBadPasswordNoZap() throws InterruptedException
    {
        //  When ZAP is not used, always accept
        Boolean status = runTest(false, tctxt -> {
            boolean rc;

            rc = ZMQ.bind(tctxt.server, tctxt.host);
            Assertions.assertTrue(rc);

            String host = (String) ZMQ.getSocketOptionExt(tctxt.server, ZMQ.ZMQ_LAST_ENDPOINT);
            PlainMechanismSettings settings = new PlainMechanismSettings(false, "wronguser", "wrongpass");
            ZMQ.setSocketOption(tctxt.client, ZMQ.ZMQ_MECHANISM, settings);

            rc = ZMQ.connect(tctxt.client, host);
            Assertions.assertTrue(rc);
            return true;
        });
        Assertions.assertNull(status);
    }

    @Test
    public void testRawSocket() throws InterruptedException
    {
        // Unauthenticated messages from a vanilla socket shouldn't be received
        Boolean zapCheck = runTest(false, MechanismTester::testRawSocket);
        Assertions.assertNull(zapCheck);
    }

    @Test
    public void testDeprecatedOptions()
    {
        MechanismTester.checkOptions(Mechanisms.PLAIN, opt -> {
            Assertions.assertThrows(IllegalArgumentException.class, () -> {
                opt.setSocketOpt(ZMQ.ZMQ_PLAIN_USERNAME, "plainUsername");
            });
            Assertions.assertThrows(IllegalArgumentException.class, () -> {
                opt.setSocketOpt(ZMQ.ZMQ_PLAIN_PASSWORD, "plainPassword");
            });
        });
    }
}
