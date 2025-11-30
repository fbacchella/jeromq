package org.zeromq;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.zeromq.util.ZDigest;
import org.zeromq.util.ZMetadata;

/**
    To authenticate new clients using the ZeroMQ CURVE security mechanism,
    we have to check that the client's public key matches a key we know and
    accept. There are numerous ways to store accepted client public keys.
    The mechanism CZMQ implements is "certificates" (plain text files) held
    in a "certificate store" (a disk directory). This class works with such
    certificate stores, and lets you easily load them from disk, and check
    if a given client public key is known or not. The {@link org.zeromq.ZCert} class does the
    work of managing a single certificate.
 * <p>Those files need to be in ZMP-Format which is created by {@link org.zeromq.ZConfig}</p>
 */
public class ZCertStore
{
    public interface Fingerprinter
    {
        byte[] print(Path path) throws IOException;
    }

    public static class Timestamper implements Fingerprinter
    {
        @Override
        public byte[] print(Path path) throws IOException
        {
            byte[] buf = new byte[8];
            long value = Files.getLastModifiedTime(path).toMillis();
            buf[0] = (byte) ((value >>> 56) & 0xff);
            buf[1] = (byte) ((value >>> 48) & 0xff);
            buf[2] = (byte) ((value >>> 40) & 0xff);
            buf[3] = (byte) ((value >>> 32) & 0xff);
            buf[4] = (byte) ((value >>> 24) & 0xff);
            buf[5] = (byte) ((value >>> 16) & 0xff);
            buf[6] = (byte) ((value >>> 8) & 0xff);
            buf[7] = (byte) ((value) & 0xff);
            return buf;
        }
    }

    public static class Hasher implements Fingerprinter
    {
        // temporary buffer used for digest. Instance member for performance reasons.
        private final byte[] buffer = new byte[8192];

        @Override
        public byte[] print(Path path) throws IOException
        {
            InputStream input = stream(path);
            if (input != null) {
                return new ZDigest(buffer).update(input).data();
            }
            return null;
        }

        private InputStream stream(Path path) throws IOException
        {
            if (Files.isRegularFile(path)) {
                return Files.newInputStream(path);
            } else if (Files.isDirectory(path)) {
                try(Stream<Path> files = Files.list(path)) {
                    List<String> list = files.map(p -> p.getFileName().toString())
                                             .sorted()
                                             .collect(Collectors.toList());

                    byte[] data = list.toString().getBytes(ZMQ.CHARSET);
                    return new ByteArrayInputStream(data);
                }
            }
            return null;
        }
    }

    private interface IFileVisitor
    {
        /**
         * Visits a file.
         * @param file the file to visit.
         * @return true to stop the traversal, false to continue.
         */
        boolean visitFile(Path file) throws IOException;

        /**
         * Visits a directory.
         * @param dir the directory to visit.
         * @return true to stop the traversal, false to continue.
         */
        boolean visitDir(Path dir) throws IOException;
    }

    //  Directory location
    private final Path location;

    // the scanned files (and directories) along with their fingerprint
    private final Map<Path, byte[]> fingerprints = new HashMap<>();

    // collected public keys
    private final Map<String, ZMetadata> publicKeys = new HashMap<>();

    private final Fingerprinter finger;

    /**
     * Create a Certificate Store at that file system folder location
     * @param location the location of the certificates store
     */
    public ZCertStore(Path location) throws IOException
    {
        this(location, new Timestamper());
    }

    public ZCertStore(Path location, Fingerprinter fingerprinter) throws IOException
    {
        this.finger = fingerprinter;
        this.location = location;
        loadFiles();
    }

    private boolean traverseDirectory(Path root, IFileVisitor visitor) throws IOException
    {
        assert (Files.isDirectory(root));

        if (visitor.visitDir(root)) {
            return true;
        }
        for (Path file : Files.list(root).collect(Collectors.toList())) {
            if (Files.isRegularFile(file)) {
                if (visitor.visitFile(file)) {
                    return true;
                }
            }
            else if (Files.isDirectory(file)) {
                boolean rc = traverseDirectory(file, visitor);
                if (rc) {
                    return true;
                }
            }
            else {
                System.out.printf(
                                  "WARNING: %s is neither file nor directory? This shouldn't happen....SKIPPING%n",
                                  file);
            }
        }
        return false;
    }

    /**
     * Check if a public key is in the certificate store.
     * @param publicKey needs to be a 32 byte array representing the public key
     */
    public boolean containsPublicKey(byte[] publicKey)
    {
        Utils.checkArgument(publicKey.length == 32,
                            "publickey needs to have a size of 32 bytes. got only " + publicKey.length);
        return containsPublicKey(ZMQ.Curve.z85Encode(publicKey));
    }

    /**
     * check if a z85-based public key is in the certificate store.
     * This method will scan the folder for changes on every call
     * @param publicKey the public key to check
     */
    public boolean containsPublicKey(String publicKey)
    {
        Utils.checkArgument(publicKey.length() == 40,
                            "z85 publickeys should have a length of 40 bytes but got " + publicKey.length());
        reloadIfNecessary();
        return publicKeys.containsKey(publicKey);
    }

    public ZMetadata getMetadata(String publicKey)
    {
        reloadIfNecessary();
        return publicKeys.get(publicKey);
    }

    private void loadFiles() throws IOException
    {
        Map<String, ZMetadata> keys = new HashMap<>();
        if (!Files.exists(location)) {
            Files.createDirectory(location);
        }
        Map<Path, byte[]> collected = new HashMap<>();

        traverseDirectory(location, new IFileVisitor()
        {
            @Override
            public boolean visitFile(Path file)
            {
                try {
                    ZConfig zconf = ZConfig.load(file);
                    String publicKey = zconf.getValue("curve/public-key");
                    if (publicKey == null) {
                        System.out.printf(
                                          "Warning!! File %s has no curve/public-key-element. SKIPPING!%n",
                                          file);
                        return false;
                    }
                    if (publicKey.length() == 32) { // we want to store the public-key as Z85-String
                        publicKey = ZMQ.Curve.z85Encode(publicKey.getBytes(ZMQ.CHARSET));
                    }
                    assert (publicKey.length() == 40);
                    keys.put(publicKey, ZMetadata.read(zconf));
                    collected.put(file, finger.print(file));
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
                return false;
            }

            @Override
            public boolean visitDir(Path dir) throws IOException
            {
                collected.put(dir, finger.print(dir));
                return false;
            }
        });

        publicKeys.clear();
        publicKeys.putAll(keys);
        fingerprints.clear();
        fingerprints.putAll(collected);
    }

    int getCertificatesCount()
    {
        reloadIfNecessary();
        return publicKeys.size();
    }

    boolean reloadIfNecessary()
    {
        if (checkForChanges()) {
            try {
                loadFiles();
                return true;
            } catch (IOException e) {
                return false;
            }
        }
        else {
            return false;
        }
    }

    /**
     * Check if files in the certificate folders have been added or removed.
     */
    boolean checkForChanges()
    {
        try {
            // initialize with last checked files
            Map<Path, byte[]> presents = new HashMap<>(fingerprints);
            boolean modified = traverseDirectory(location, new IFileVisitor()
            {
                @Override
                public boolean visitFile(Path file) throws IOException {
                    return modified(presents.remove(file), file);
                }

                @Override
                public boolean visitDir(Path dir) throws IOException {
                    return modified(presents.remove(dir), dir);
                }
            });
            // if some files remain, that means they have been deleted since last scan
            return modified || !presents.isEmpty();
        } catch (IOException e) {
            return false;
        }
    }

    private boolean modified(byte[] fingerprint, Path path) throws IOException {
        if (Files.notExists(path)) {
            // run load-files if one file is not present
            return true;
        }
        if (fingerprint == null) {
            // file was not scanned before, it has been added
            return true;
        }
        // true if file has been modified
        return !Arrays.equals(fingerprint, finger.print(path));
    }
}
