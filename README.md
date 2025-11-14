
# JeroMQ

Pure Java implementation of libzmq (http://zeromq.org).

[![CircleCI](https://circleci.com/gh/fbacchella/jeromq.svg?style=svg)](https://circleci.com/gh/fbacchella/jeromq)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=fr.loghub%3Ajeromq&metric=alert_status)](https://sonarcloud.io/dashboard?id=fr.loghub%3Ajeromq)
[![Maven Central](https://img.shields.io/maven-central/v/fr.loghub/jeromq.svg)](https://maven-badges.herokuapp.com/maven-central/fr.loghub/jeromq)
[![Javadocs](http://www.javadoc.io/badge/fbacchella/jeromq.svg)](http://www.javadoc.io/doc/org.zeromq/jeromq)

## Features

* Based on libzmq 4.1.7.
* ZMTP/3.0 (http://rfc.zeromq.org/spec:23).
* tcp:// protocol and inproc:// is compatible with zeromq.
* ipc:// protocol works only between jeromq (uses tcp://127.0.0.1:port internally).

* Securities
  * [PLAIN](http://rfc.zeromq.org/spec:24).
  * [CURVE](http://rfc.zeromq.org/spec:25).

* Performance that's not too bad, compared to native libzmq.
  * 4.5M messages (100B) per sec.
  * [Performance](https://github.com/zeromq/jeromq/wiki/Performance).
* Exactly same developer experience with zeromq and jzmq.
 
* TCP KeepAlive Count, Idle and Interval are known to only work with JVM 13 and later.

## Unsupported

* ipc:// protocol with zeromq. Java doesn't support UNIX domain socket.
* pgm:// protocol. Cannot find a pgm Java implementation.
* norm:// protocol. Cannot find a Java implementation.
* tipc:// protocol. Cannot find a Java implementation.

* GSSAPI mechanism is not yet implemented.

* Interrupting threads is still unsupported: library is NOT Thread.interrupt safe.

## Contributing

Contributions welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for details about the contribution process and useful development tasks.

## Usage

### Maven

Add it to your Maven project's `pom.xml`:

```xml
    <dependency>
      <groupId>fr.loghub</groupId>
      <artifactId>jeromq</artifactId>
      <version>0.7.0</version>
    </dependency>

    <!-- for the latest SNAPSHOT -->
    <dependency>
      <groupId>fr.loghub</groupId>
      <artifactId>jeromq</artifactId>
      <version>2.0.0-SNAPSHOT</version>
    </dependency>

    <!-- If you can't find the latest snapshot -->
    <repositories>
      <repository>
          <id>central</id>
          <url>https://central.sonatype.com/repository/maven-snapshots</url>
        <releases>
          <enabled>false</enabled>
        </releases>
        <snapshots>
          <enabled>true</enabled>
        </snapshots>
       </repository>
    </repositories>
```

### Ant

To generate an ant build file from `pom.xml`, issue the following command:

```bash
mvn ant:ant
```

## Getting started

### Simple example

Here is how you might implement a server that prints the messages it receives
and responds to them with "Hello, world!":

```java
import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZContext;

public class hwserver
{
    public static void main(String[] args) throws Exception
    {
        try (ZContext context = new ZContext()) {
            // Socket to talk to clients
            ZMQ.Socket socket = context.createSocket(SocketType.REP);
            socket.bind("tcp://*:5555");

            while (!Thread.currentThread().isInterrupted()) {
                // Block until a message is received
                byte[] reply = socket.recv(0);

                // Print the message
                System.out.println(
                    "Received: [" + new String(reply, ZMQ.CHARSET) + "]"
                );

                // Send a response
                String response = "Hello, world!";
                socket.send(response.getBytes(ZMQ.CHARSET), 0);
            }
        }
    }
}
```

### More examples

The JeroMQ [translations of the zguide examples](jeromq-core/src/test/java/guide) are a good
reference for recommended usage.

### Documentation

For API-level documentation, see the
[Javadocs](http://www.javadoc.io/doc/org.zeromq/jeromq).

This repo also has a [doc](doc/) folder, which contains assorted "how to do X"
guides and other useful information about various topics related to using
JeroMQ.

## License

All source files are copyright © 2007-2024 contributors as noted in the AUTHORS file.

Free use of this software is granted under the terms of the Mozilla Public License 2.0. For details see the file `LICENSE` included with the JeroMQ distribution.
