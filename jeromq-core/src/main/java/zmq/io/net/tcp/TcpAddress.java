package zmq.io.net.tcp;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnknownHostException;
import java.util.Arrays;

import zmq.io.net.Address;

public class TcpAddress implements Address.IZAddress<InetSocketAddress>
{
    public static class TcpAddressMask extends TcpAddress
    {
        public TcpAddressMask(String addr, boolean ipv6)
        {
            super(addr, ipv6);
        }

        public boolean matchAddress(SocketAddress addr)
        {
            return address().equals(addr);
        }
    }

    private final InetSocketAddress address;
    private final InetSocketAddress sourceAddress;

    public TcpAddress(String addr, boolean ipv6)
    {
        String[] strings = addr.split(";");

        address = resolve(strings[0], ipv6, false);
        if (strings.length == 2 && !"".equals(strings[1])) {
            sourceAddress = resolve(strings[1], ipv6, false);
        }
        else {
            sourceAddress = null;
        }
    }

    protected TcpAddress(InetSocketAddress address)
    {
        this.address = address;
        sourceAddress = null;
    }

    @Override
    public ProtocolFamily family()
    {
        if (address.getAddress() instanceof Inet6Address) {
            return StandardProtocolFamily.INET6;
        }
        else {
            return StandardProtocolFamily.INET;
        }
    }

    // The opposite to resolve()
    @Override
    public String toString()
    {
        return toString(address.getPort());
    }

    // The opposite to resolve()
    @Override
    public String toString(int port)
    {
        if (address == null) {
            return "";
        }

        int addressPort = address.getPort();
        if (addressPort == 0) {
            addressPort = port;
        }
        return "tcp://" + address.getHostString() + ":" + addressPort;
    }

    /**
     * @param name
     * @param ipv6
     * @param local ignored
     * @return the resolved address
     * @see zmq.io.net.Address.IZAddress#resolve(java.lang.String, boolean, boolean)
     */
    @Override
    public InetSocketAddress resolve(String name, boolean ipv6, boolean local)
    {
        //  Find the ':' at end that separates address from the port number.
        int delimiter = name.lastIndexOf(':');
        if (delimiter < 0) {
            throw new IllegalArgumentException(String.format("Not a ZMQ adress \"%s\"", name));
        }

        //  Separate the address/port.
        String addrStr = name.substring(0, delimiter);
        String portStr = name.substring(delimiter + 1);

        // [], an IPv6 was requested
        if (addrStr.length() >= 2 && addrStr.charAt(0) == '[' && addrStr.charAt(addrStr.length() - 1) == ']') {
            ipv6 = true;
        }

        int port;
        //  Allow 0 specifically, to detect invalid port error in atoi if not
        if (portStr.equals("*") || portStr.equals("0")) {
            //  Resolve wildcard to 0 to allow autoselection of port
            port = 0;
        }
        else {
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(String.format("Not a integer for the port of \"%s\": %s", name, portStr));
            }
        }

        InetAddress addrNet = null;

        // '*' as unspecified address is not accepted in Java
        // '::' for IPv6 is accepted
        if (addrStr.equals("*") || addrStr.equals("[*]")) {
            addrStr = ipv6 ? "::" : "0.0.0.0";
        }

        try {
            InetAddress[] addresses = InetAddress.getAllByName(addrStr);
            if (ipv6) {
                // prefer IPv6: return the first ipv6 or the first value if not found
                addrNet = Arrays.stream(addresses).filter(Inet6Address.class::isInstance).findFirst().orElseGet(() -> addresses[0]);
            }
            else {
                addrNet = Arrays.stream(addresses).filter(Inet4Address.class::isInstance).findFirst().orElseGet(() -> null);
            }
        }
        catch (UnknownHostException e) {
            throw new IllegalArgumentException(String.format("Failed connecting to \"%s\": %s", name, e.getMessage()), e);
        }

        if (addrNet == null) {
            throw new IllegalArgumentException(String.format("Not found matching IPv4/IPv6 \"%s\" address for \"%s\"", addrStr, name));
        }

        return new InetSocketAddress(addrNet, port);
    }

    @Override
    public InetSocketAddress address()
    {
        return address;
    }

    @Override
    public InetSocketAddress sourceAddress()
    {
        return sourceAddress;
    }
}
