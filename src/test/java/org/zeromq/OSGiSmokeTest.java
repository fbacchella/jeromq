package org.zeromq;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;

import static org.ops4j.pax.exam.CoreOptions.bundle;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.options;

@RunWith(PaxExam.class)
public class OSGiSmokeTest
{
    @Configuration
    public Option[] config()
    {
        return options(bundle("reference:file:target/classes"), junitBundles());
    }

    @Test(timeout = 5000)
    public void basicUsage()
    {
        try (ZMQ.Context context = ZMQ.context(1);
             ZMQ.Socket push = context.socket(SocketType.PUSH);
             ZMQ.Socket pull = context.socket(SocketType.PULL)
        ) {
            int port = push.bindToRandomPort("tcp://127.0.0.1");
            pull.connect("tcp://127.0.0.1:" + port);
            push.send("ZMQ");
            pull.recv();
        }
    }
}
