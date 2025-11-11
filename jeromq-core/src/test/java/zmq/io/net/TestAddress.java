package zmq.io.net;

import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnknownHostException;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

import zmq.io.net.Address.IZAddress;

public class TestAddress
{
    @Test
    public void testToNotResolvedToString()
    {
        Address addr = new Address("tcp", "google.com:90");
        Assert.assertEquals("tcp://google.com:90", addr.toString());
    }

    @Test
    public void testResolvedToString()
    {
        Address addr = new Address("tcp", "google.com:90");
        addr.resolve(false);
        Assert.assertEquals("tcp://google.com:90", addr.toString());
    }

    @Test
    public void testSourceAddress()
    {
        IZAddress<InetSocketAddress> addr = new Address("tcp", "google.com:90;localhost:10").resolve(false);
        Assert.assertEquals(new InetSocketAddress("google.com", 90), addr.address());
        Assert.assertEquals(new InetSocketAddress("localhost", 10), addr.sourceAddress());
    }

    @Test
    public void testWildCard1()
    {
        IZAddress<InetSocketAddress> addr = new Address("tcp", "google.com:0").resolve(false);
        Assert.assertEquals(0, addr.address().getPort());
    }

    @Test
    public void testWildCard2()
    {
        IZAddress<InetSocketAddress> addr = new Address("tcp", "google.com:*").resolve(false);
        Assert.assertEquals(0, addr.address().getPort());
    }

    @Test
    public void testIpv6Wildcard1()
    {
        IZAddress<InetSocketAddress> addr = new Address("tcp", "[*]:*").resolve(false);
        Assert.assertEquals(0, addr.address().getPort());
        Assert.assertEquals("/0:0:0:0:0:0:0:0", addr.address().getAddress().toString());
        Assert.assertEquals(StandardProtocolFamily.INET6, addr.family());
    }

    @Test
    public void testIpv6Wildcard2()
    {
        IZAddress<InetSocketAddress> addr = new Address("tcp", "*:*").resolve(true);
        Assert.assertEquals(0, addr.address().getPort());
        Assert.assertEquals("/0:0:0:0:0:0:0:0", addr.address().getAddress().toString());
        Assert.assertEquals(StandardProtocolFamily.INET6, addr.family());
    }

    @Test
    public void testInvalid1()
    {
        String hostName = String.format("%s.google.com", new Random().nextLong());
        IllegalArgumentException ex = Assert.assertThrows(IllegalArgumentException.class, () -> new Address("tcp", hostName + ":80").resolve(false));
        String expected = String.format("Failed connecting to \"%s:80\": ", hostName);
        // Don't check the full message, different JVM change it
        Assert.assertTrue(ex.getMessage().startsWith(expected));
        Assert.assertEquals(UnknownHostException.class, ex.getCause().getClass());

    }

    @Test
    public void testInvalid2()
    {
        IllegalArgumentException ex = Assert.assertThrows(IllegalArgumentException.class, () -> new Address("tcp", "www.google.com:-1").resolve(false));
        Assert.assertEquals("port out of range:-1", ex.getMessage());
    }

    @Test
    public void testInvalid3()
    {
        IllegalArgumentException ex = Assert.assertThrows(IllegalArgumentException.class, () -> new Address("tcp", "www.google.com").resolve(false));
        Assert.assertEquals("Not a ZMQ adress \"www.google.com\"", ex.getMessage());
    }

    @Test
    public void testInvalid4()
    {
        IllegalArgumentException ex = Assert.assertThrows(IllegalArgumentException.class, () -> new Address("tcp", "www.google.com:a").resolve(false));
        Assert.assertEquals("Not a integer for the port of \"www.google.com:a\": a", ex.getMessage());
    }
}
