package org.zeromq;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZCertStoreTest
{
    @TempDir
    private Path certStoreLocation;

    private ZCertStore certStore;

    @BeforeEach
    void init() throws IOException
    {
        certStore = new ZCertStore(certStoreLocation, new ZCertStore.Timestamper());
    }

    @Test
    void testAddCertificates() throws IOException
    {
        int beforeAmount = certStore.getCertificatesCount();
        assertEquals(0, beforeAmount);

        ZCert c1 = new ZCert();
        Path p = certStoreLocation.resolve("c1.cert");
        c1.savePublic(p);
        assertTrue(Files.exists(p));

        boolean rc = certStore.reloadIfNecessary();
        assertTrue(rc);
        assertEquals(1, certStore.getCertificatesCount());

        assertTrue(certStore.containsPublicKey(c1.getPublicKeyAsZ85()));
        assertTrue(certStore.containsPublicKey(c1.getPublicKey()));
        assertFalse(certStore.containsPublicKey("1234567890123456789012345678901234567890"));

        zmq.ZMQ.msleep(1000);

        ZCert c2 = new ZCert();
        Path p2 = certStoreLocation.resolve("sub").resolve("c2.cert");
        Files.createDirectory(p2.getParent());
        c2.savePublic(p2);
        assertTrue(Files.exists(p2));

        assertEquals(2, certStore.getCertificatesCount());
    }

    @Test
    void testRemoveCertificates() throws IOException
    {
        assertEquals(0, certStore.getCertificatesCount());

        ZCert c1 = new ZCert();
        Path p = certStoreLocation.resolve("c1.cert");
        c1.savePublic(p);
        assertTrue(Files.exists(p));

        boolean rc = certStore.reloadIfNecessary();
        assertTrue(rc);
        assertEquals(1, certStore.getCertificatesCount());

        rc = Files.exists(p);
        assertTrue(rc);

        rc = Files.deleteIfExists(p);
        assertTrue(rc);

        rc = certStore.reloadIfNecessary();
        assertTrue(rc);

        assertEquals(0, certStore.getCertificatesCount());
        assertFalse(certStore.containsPublicKey(c1.getPublicKeyAsZ85()));
        assertFalse(certStore.containsPublicKey(c1.getPublicKey()));
    }

    @Test
    void testCheckForCertificateChanges() throws IOException
    {
        assertEquals(0, certStore.getCertificatesCount());

        Path p1 = certStoreLocation.resolve("c1.cert");
        Path p2 = certStoreLocation.resolve("sub").resolve("c2.cert");
        Files.createDirectory(p2.getParent());

        ZCert cert1 = new ZCert();
        cert1.savePublic(p1);
        assertTrue(Files.exists(p1));

        ZCert cert2 = new ZCert();
        cert2.saveSecret(p2);
        assertTrue(Files.exists(p2));

        assertEquals(2, certStore.getCertificatesCount());
        assertFalse(certStore.checkForChanges());

        zmq.ZMQ.msleep(1000);

        ZCert othercert1 = new ZCert();
        othercert1.savePublic(p1);
        assertTrue(Files.exists(p1));

        assertTrue(certStore.checkForChanges());
        assertTrue(certStore.checkForChanges());

        assertEquals(2, certStore.getCertificatesCount());
        assertFalse(certStore.checkForChanges());

        assertFalse(certStore.containsPublicKey(cert1.getPublicKeyAsZ85()));
        assertFalse(certStore.containsPublicKey(cert1.getPublicKey()));

        assertTrue(certStore.containsPublicKey(othercert1.getPublicKeyAsZ85()));
        assertTrue(certStore.containsPublicKey(othercert1.getPublicKey()));

        zmq.ZMQ.msleep(1000);

        cert2.savePublic(p2);
        assertTrue(Files.exists(p2));

        assertTrue(certStore.checkForChanges());
        assertTrue(certStore.checkForChanges());

        assertEquals(2, certStore.getCertificatesCount());
        assertFalse(certStore.checkForChanges());
    }
}
