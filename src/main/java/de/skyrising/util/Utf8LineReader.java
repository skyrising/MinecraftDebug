package de.skyrising.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.channels.ReadableByteChannel;

public class Utf8LineReader {
    private final ReadableByteChannel channel;
    public final ByteBuffer buf;
    private final LongBuffer longBuffer;
    private int read;

    public Utf8LineReader(ReadableByteChannel channel) {
        this(channel, 1024, true);
    }

    public Utf8LineReader(ReadableByteChannel channel, int bufSize, boolean direct) {
        this.channel = channel;
        this.buf = direct ? ByteBuffer.allocateDirect(bufSize) : ByteBuffer.allocate(bufSize);
        this.longBuffer = this.buf.asLongBuffer();
    }

    /**
     * Finds the first 8 byte block containing \n
     * @param start Starting buffer offset in bytes
     * @return Starting offset of the block containing \n
     */
    private int findEndOfLineApprox(int start) {
        int i = start;
        int lpos = start >> 3;
        int lread = this.read >> 3;
        for (; lpos < lread; lpos++) {
            long l = longBuffer.get(lpos);
            l ^= 0x0a0a0a0a0a0a0a0aL; // '\n' = 0x0a
            // http://graphics.stanford.edu/~seander/bithacks.html#ZeroInWord
            if (((l - 0x0101010101010101L) & ~l & 0x8080808080808080L) != 0) {
                return i;
            }
            i = (lpos + 1) << 3;
        }
        return lpos << 3;
    }

    /**
     * Finds the position of \n
     * @param start Starting buffer offset in bytes
     * @return offset of \n, or ~offset if \r was found before \n
     */
    private int findEndOfLine(int start) {
        int i = start;
        // find actual position of \n
        boolean cr = false;
        int read = this.read;
        for (; i < read; i++) {
            byte b = buf.get(i);
            if (b == '\n') {
                break;
            }
            cr = b == '\r';
        }
        return cr ? ~i : i;
    }

    private static byte[] get(ByteBuffer buf, int len) {
        byte[] newBytes = new byte[len];
        buf.get(newBytes, 0 , len);
        return newBytes;
    }

    private static byte[] append(byte[] base, ByteBuffer buf, int len) {
        byte[] newBytes = new byte[base.length + len];
        System.arraycopy(base, 0, newBytes, 0, base.length);
        buf.get(newBytes, base.length, len);
        return newBytes;
    }

    public byte[] readLine() throws IOException {
        if (read < 0) return null;
        byte[] bytes = null;
        while (true) {
            if (read == 0) {
                read = channel.read(buf);
                buf.rewind();
                if (read < 0) {
                    return bytes;
                }
            }
            int i = findEndOfLine(findEndOfLineApprox(buf.position()));
            boolean cr = i < 0;
            if (cr) i = ~i;
            int len = i - buf.position();
            if (cr) len--;
            bytes = bytes == null ? get(buf, len) : append(bytes, buf, len);
            if (i == read - 1) {
                buf.rewind();
                read = 0;
                return bytes;
            }
            if (i < read) {
                buf.position(buf.position() + (cr ? 2 : 1));
                return bytes;
            }
            buf.rewind();
            read = 0;
        }
    }
}
