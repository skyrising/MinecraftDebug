package de.skyrising.util;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;

public class Utf8LineReaderTest {
    @Test
    public void readLines() throws IOException {
        ReadableByteChannel bc = Channels.newChannel(new ByteArrayInputStream("test\n123\r\n\r\n456\n\n".getBytes(StandardCharsets.UTF_8)));
        Utf8LineReader reader = new Utf8LineReader(bc);
        Assert.assertArrayEquals("test".getBytes(StandardCharsets.UTF_8), reader.readLine());
        Assert.assertArrayEquals("123".getBytes(StandardCharsets.UTF_8), reader.readLine());
        Assert.assertArrayEquals("".getBytes(StandardCharsets.UTF_8), reader.readLine());
        Assert.assertArrayEquals("456".getBytes(StandardCharsets.UTF_8), reader.readLine());
        Assert.assertArrayEquals("".getBytes(StandardCharsets.UTF_8), reader.readLine());
        Assert.assertNull(reader.readLine());
    }
}
