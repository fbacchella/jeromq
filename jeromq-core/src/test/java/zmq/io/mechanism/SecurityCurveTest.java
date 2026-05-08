package zmq.io.mechanism;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.junit.Test;

import zmq.SocketBase;
import zmq.ZMQ;
import zmq.io.mechanism.curve.Curve;
import zmq.io.mechanism.curve.CurveMechanismSettings;
import zmq.io.mechanism.plain.PlainMechanismSettings;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class SecurityCurveTest
{
    private static class CurveTestContext extends MechanismTester.TestContext
    {
        String serverPublic;
        String serverSecret;
        String clientPublic;
        String clientSecret;
    }

    private Boolean runTest(boolean withzap, Function<CurveTestContext, Boolean> tested) throws InterruptedException
    {
        CurveTestContext testCtx = new CurveTestContext();
        //  Generate new keypairs for this test
        Curve cryptoBox = new Curve();
        String[] clientKeys = cryptoBox.keypairZ85();
        testCtx.clientPublic = clientKeys[0];
        testCtx.clientSecret = clientKeys[1];

        String[] serverKeys = cryptoBox.keypairZ85();
        testCtx.serverPublic = serverKeys[0];
        testCtx.serverSecret = serverKeys[1];

        BiFunction<SocketBase, CompletableFuture<Boolean>, ZapHandler> zapProvider = (s, f) -> new ZapHandler(s, f, testCtx.clientPublic);
        Runnable configurator = () -> {
            MechanismSettings<?> serverSettings = CurveMechanismSettings.getBuilder()
                                                               .setSecretKey(testCtx.serverSecret)
                                                               .setPublicKey(testCtx.serverPublic)
                                                               .build();
            // Preconfigure server with valid identity, might be changed for individual tests
            ZMQ.setSocketOption(testCtx.server, ZMQ.ZMQ_MECHANISM, serverSettings);
            ZMQ.setSocketOption(testCtx.server, ZMQ.ZMQ_IDENTITY, "IDENT");
            if (withzap) {
                ZMQ.setSocketOption(testCtx.server, ZMQ.ZMQ_ZAP_DOMAIN, "global");
                ZMQ.setSocketOption(testCtx.client, ZMQ.ZMQ_ZAP_DOMAIN, "global");
            }

            MechanismSettings<?> clientSettings = CurveMechanismSettings.getBuilder()
                                                       .setSecretKey(testCtx.clientSecret)
                                                       .setPublicKey(testCtx.clientPublic)
                                                       .setServerKey(testCtx.serverPublic)
                                                       .build();
            // Preconfigure client with valid identity, might be changed for individual tests
            ZMQ.setSocketOption(testCtx.client, ZMQ.ZMQ_MECHANISM, clientSettings);
        };

        return MechanismTester.runTest(testCtx, withzap, tested, zapProvider, configurator);
    }

    private boolean doSuccess(CurveTestContext ctx)
    {
        boolean rc;

        rc = ZMQ.bind(ctx.server, ctx.host);
        assertThat(rc, is(true));

        String host = (String) ZMQ.getSocketOptionExt(ctx.server, ZMQ.ZMQ_LAST_ENDPOINT);
        rc = ZMQ.connect(ctx.client, host);
        assertThat(rc, is(true));

        return true;
    }

    @Test
    public void testSuccessWithZap() throws InterruptedException
    {
        assertThat(runTest(true, this::doSuccess), is(true));
    }

    @Test
    public void testSuccessNoZap() throws InterruptedException
    {
        assertThat(runTest(false, this::doSuccessInverted), nullValue());
    }

    private boolean doSuccessInverted(CurveTestContext ctx)
    {
        boolean rc;

        rc = ZMQ.bind(ctx.client, ctx.host);
        assertThat(rc, is(true));

        String host = (String) ZMQ.getSocketOptionExt(ctx.client, ZMQ.ZMQ_LAST_ENDPOINT);

        rc = ZMQ.connect(ctx.server, host);
        assertThat(rc, is(true));

        return true;
    }

    @Test
    public void testSuccessInvertedWithZap() throws InterruptedException
    {
        assertThat(runTest(true, this::doSuccessInverted), is(true));
    }

    @Test
    public void testSuccessInvertedNoZap() throws InterruptedException
    {
        assertThat(runTest(false, this::doSuccessInverted), nullValue());
    }

    @Test
    public void testGarbageClientSecretKeyZap() throws InterruptedException
    {
        Boolean zapCheck = runTest(true, ctx -> {
            boolean rc;
            rc = ZMQ.bind(ctx.server, ctx.host);
            assertThat(rc, is(true));

            ZMQ.setSocketOption(ctx.client, ZMQ.ZMQ_MECHANISM, CurveMechanismSettings.getBuilder()
                                                                                    .setPublicKey(ctx.clientPublic)
                                                                                    .setSecretKey("0000000000000000000000000000000000000000")
                                                                                    .setServerKey(ctx.serverPublic)
                                                                                    .build());
            String host = (String) ZMQ.getSocketOptionExt(ctx.server, ZMQ.ZMQ_LAST_ENDPOINT);
            rc = ZMQ.connect(ctx.client, host);

            assertThat(rc, is(true));
            return false;
        });
        assertThat(zapCheck, nullValue());
    }

    @Test
    public void testGarbageServerSecretKeyZap() throws InterruptedException
    {
        Boolean zapCheck = runTest(true, ctx -> {
            boolean rc;

            ZMQ.setSocketOption(ctx.server, ZMQ.ZMQ_MECHANISM, CurveMechanismSettings.getBuilder()
                                                                                    .setPublicKey(ctx.serverPublic)
                                                                                    .setSecretKey("0000000000000000000000000000000000000000")
                                                                                    .build());
            rc = ZMQ.bind(ctx.server, ctx.host);
            assertThat(rc, is(true));

            String host = (String) ZMQ.getSocketOptionExt(ctx.server, ZMQ.ZMQ_LAST_ENDPOINT);
            rc = ZMQ.connect(ctx.client, host);
            assertThat(rc, is(true));
            return false;
        });
        assertThat(zapCheck, nullValue());
    }

    @Test
    public void testBogusClientKey() throws InterruptedException
    {
        //  Check CURVE security with bogus client credentials
        //  This must be caught by the ZAP handler
        Boolean zapCheck = runTest(true, ctx -> {
            boolean rc;

            rc = ZMQ.bind(ctx.server, ctx.host);
            assertThat(rc, is(true));

            Curve cryptoBox = new Curve();
            String[] bogus = cryptoBox.keypairZ85();
            String bogusPublic = bogus[0];
            String bogusSecret = bogus[1];

            ZMQ.setSocketOption(ctx.client, ZMQ.ZMQ_MECHANISM, CurveMechanismSettings.getBuilder()
                                                                                    .setPublicKey(bogusPublic)
                                                                                    .setSecretKey(bogusSecret)
                                                                                    .setServerKey(ctx.serverPublic)
                                                                                    .build());
            String host = (String) ZMQ.getSocketOptionExt(ctx.server, ZMQ.ZMQ_LAST_ENDPOINT);
            rc = ZMQ.connect(ctx.client, host);
            assertThat(rc, is(true));
            return false;
        });
        assertThat(zapCheck, is(false));
    }

    @Test(timeout = 5000)
    public void testBogusClientInvertedKey() throws InterruptedException
    {
        //  Check CURVE security with bogus client credentials
        //  This must be caught by the ZAP handler
        Boolean zapCheck = runTest(true, ctx -> {
            boolean rc;

            rc = ZMQ.bind(ctx.client, ctx.host);
            assertThat(rc, is(true));

            Curve cryptoBox = new Curve();
            String[] bogus = cryptoBox.keypairZ85();
            String bogusPublic = bogus[0];
            String bogusSecret = bogus[1];

            ZMQ.setSocketOption(ctx.client, ZMQ.ZMQ_MECHANISM, CurveMechanismSettings.getBuilder()
                                                                                    .setPublicKey(bogusPublic)
                                                                                    .setSecretKey(bogusSecret)
                                                                                    .build());
            String host = (String) ZMQ.getSocketOptionExt(ctx.client, ZMQ.ZMQ_LAST_ENDPOINT);
            rc = ZMQ.connect(ctx.server, host);
            assertThat(rc, is(true));
            return false;
        });
        assertThat(zapCheck, is(false));
    }

    @Test
    public void testNullClient() throws InterruptedException
    {
        //  Check CURVE security with bogus client credentials
        //  This must be caught by the ZAP handler
        Boolean zapCheck = runTest(false, ctx -> {
            boolean rc;

            rc = ZMQ.bind(ctx.server, ctx.host);
            assertThat(rc, is(true));

            ZMQ.closeZeroLinger(ctx.client);
            ctx.client = ZMQ.socket(ctx.zctxt, ZMQ.ZMQ_DEALER);

            String host = (String) ZMQ.getSocketOptionExt(ctx.server, ZMQ.ZMQ_LAST_ENDPOINT);
            rc = ZMQ.connect(ctx.client, host);
            assertThat(rc, is(true));
            return false;
        });
        assertThat(zapCheck, nullValue());
    }

    @Test
    public void testPlainClient() throws InterruptedException
    {
        //  Check CURVE security with bogus client credentials
        //  This must be caught by the ZAP handler
        Boolean zapCheck = runTest(false, ctx -> {
            boolean rc;

            rc = ZMQ.bind(ctx.server, ctx.host);
            assertThat(rc, is(true));

            ZMQ.closeZeroLinger(ctx.client);
            ctx.client = ZMQ.socket(ctx.zctxt, ZMQ.ZMQ_DEALER);
            ZMQ.setSocketOption(ctx.client, ZMQ.ZMQ_MECHANISM, new PlainMechanismSettings(false, "user", "pass"));

            String host = (String) ZMQ.getSocketOptionExt(ctx.server, ZMQ.ZMQ_LAST_ENDPOINT);
            rc = ZMQ.connect(ctx.client, host);
            assertThat(rc, is(true));
            return false;
        });
        assertThat(zapCheck, nullValue());
    }

    @Test
    public void testRawSocket() throws InterruptedException
    {
        // Unauthenticated messages from a vanilla socket shouldn't be received
        Boolean zapCheck = runTest(false, MechanismTester::testRawSocket);
        assertThat(zapCheck, nullValue());
    }
}
