package org.zeromq.auth;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeromq.ZConfig;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ZConfigTest
{
    @TempDir
    private Path TEST_FOLDER;
    private static final ZConfig      conf        = new ZConfig("root", null);

    @BeforeEach
    public void init() throws IOException
    {
        // create test-passwords
        conf.putValue("/curve/public-key", "abcdefg");
        conf.putValue("/curve/secret-key", "(w3lSF/5yv&j*c&0h{4JHe(CETJSksTr.QSjcZE}");
        conf.putValue("metadata/name", "key-value tests");

        Writer write = Files.newBufferedWriter(TEST_FOLDER.resolve("test.zpl"));
        write.write("1. ZPL configuration file example\n"); // should be discarded
        write.write(" # some initial comment \n"); // should be discarded
        write.write("meta\n");
        write.write("    leadingquote = \"abcde\n");
        write.write("    endingquote = abcde\"\n");
        write.write("    quoted = \"abcde\"\n");
        write.write("    singlequoted = 'abcde'\n");
        write.write("    bind = tcp://eth0:5555\n");
        write.write("    verbose = 1      #   Ask for a trace\n");
        write.write("    sub # some comment after container-name\n");
        write.write("        fortuna = f95\n");
        write.close();

        write = Files.newBufferedWriter(TEST_FOLDER.resolve("reference.zpl"));
        write.write("context\n");
        write.write("    iothreads = 1\n");
        write.write("    verbose = 1      #   Ask for a trace\n");
        write.write("main\n");
        write.write("    type = zqueue    #  ZMQ_DEVICE type\n");
        write.write("    frontend\n");
        write.write("        option\n");
        write.write("            hwm = 1000\n");
        write.write("            swap = 25000000     #  25MB\n");
        write.write("        bind = 'inproc://addr1'\n");
        write.write("        bind = 'ipc://addr2'\n");
        write.write("    backend\n");
        write.write("        bind = inproc://addr3\n");
        write.close();
    }

    @Test
    public void testPutKeyDoubleSlash()
    {
        ZConfig config = new ZConfig("root", null);
        config.putValue("inproc://test", "one");
        assertThat(config.pathExists("inproc://test"), is(true));
    }

    @Test
    public void testPutKeySingleSlash()
    {
        ZConfig config = new ZConfig("root", null);
        config.putValue("server/timeout", "1000");
        assertThat(config.pathExists("server/timeout"), is(true));
    }

    @Test
    public void testGetKeySingleSlash()
    {
        ZConfig config = new ZConfig("root", null);
        config.putValue("server/timeout", "1000");
        Map<String, String> values = config.getValues();
        assertThat(values.toString(), values.size(), is(1));
        assertThat(values.toString(), values.containsKey("server/timeout"), is(true));
    }

    @Test
    @Disabled
    public void testGetKeyDoubleSlash()
    {
        ZConfig config = new ZConfig("root", null);
        config.putValue("inproc://test", "one");
        Map<String, String> values = config.getValues();
        assertThat(values.toString(), values.size(), is(1));
        assertThat(values.toString(), values.containsKey("inproc://test"), is(true));
    }

    @Test
    public void testPutGet()
    {
        assertThat(conf.getValue("/curve/public-key"), is("abcdefg"));
        // intentionally checking without leading /
        assertThat(conf.getValue("curve/secret-key"), is("(w3lSF/5yv&j*c&0h{4JHe(CETJSksTr.QSjcZE}"));
        assertThat(conf.getValue("/metadata/name"), is("key-value tests"));

        // checking default value
        assertThat(conf.getValue("/metadata/nothinghere", "default"), is("default"));
    }

    @Test
    public void testLoadSave() throws IOException
    {
        Path certPath = TEST_FOLDER.resolve("test.cert");
        conf.save(certPath);
        Assertions.assertTrue(isFileInPath(TEST_FOLDER, "test.cert"));
        ZConfig loadedConfig = ZConfig.load(TEST_FOLDER.resolve("test.cert"));
        assertThat(loadedConfig.getValue("/curve/public-key"), is("abcdefg"));
        // intentionally checking without leading /
        assertThat(loadedConfig.getValue("curve/secret-key"), is("(w3lSF/5yv&j*c&0h{4JHe(CETJSksTr.QSjcZE}"));
        assertThat(loadedConfig.getValue("/metadata/name"), is("key-value tests"));
    }

    private boolean isFileInPath(Path dirPath, String filename) {
        if (!Files.isDirectory(dirPath)) {
            return false;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath)) {
            for (Path filePath : stream) {
                if (filePath.getFileName().toString().equals(filename)) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    @Test
    public void testZPLSpecialCases() throws IOException
    {
        // this file was generated in the init-method and tests some cases that should be processed by the loader but are not
        // created with our writer.
        ZConfig zplSpecials = ZConfig.load(TEST_FOLDER.resolve("test.zpl"));
        // test leading quotes
        assertThat(zplSpecials.getValue("meta/leadingquote"), is("\"abcde"));
        // test ending quotes
        assertThat(zplSpecials.getValue("meta/endingquote"), is("abcde\""));
        // test full doublequoted. here the quotes should be removed
        assertThat(zplSpecials.getValue("meta/quoted"), is("abcde"));
        // test full singlequoted. here the quotes should be removed
        assertThat(zplSpecials.getValue("meta/singlequoted"), is("abcde"));
        // test no quotes tcp-pattern
        assertThat(zplSpecials.getValue("meta/bind"), is("tcp://eth0:5555"));
        // test comment after value
        assertThat(zplSpecials.getValue("meta/verbose"), is("1"));
        // test comment after container-name
        assertThat(zplSpecials.pathExists("meta/sub"), is(true));
    }

    @Test
    public void testReadReference() throws IOException
    {
        ZConfig ref = ZConfig.load(TEST_FOLDER.resolve("reference.zpl"));
        assertThat(ref.getValue("context/iothreads"), is("1"));
        assertThat(ref.getValue("context/verbose"), is("1"));
        assertThat(ref.getValue("main/type"), is("zqueue"));
        assertThat(ref.getValue("main/frontend/option/hwm"), is("1000"));
        assertThat(ref.getValue("main/frontend/option/swap"), is("25000000"));
        assertThat(ref.getValue("main/frontend/bind"), is("ipc://addr2"));
        assertThat(ref.getValue("main/backend/bind"), is("inproc://addr3"));
    }
}
