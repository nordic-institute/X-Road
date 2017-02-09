/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.common.util;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import ee.ria.xroad.common.DefaultFilepaths;
import ee.ria.xroad.common.ErrorCodes;

/**
 * Caches stuff in a temporary file.
 */
public class CachingStream extends FilterOutputStream {
    private SeekableByteChannel channel;

    /**
     * Constructs a new caching stream that caches data in a temporary file.
     * @throws IOException if I/O errors occurred
     */
    public CachingStream() throws IOException {
        // Construct the parent class with null stream and replace it later.
        super(null);

        Path tempFile = DefaultFilepaths.createTempFile("tmpattach", null);
        channel = Files.newByteChannel(tempFile, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.READ, StandardOpenOption.DELETE_ON_CLOSE);

        // Now that we are set up, we can set the output stream in the
        // parent class.
        out = Channels.newOutputStream(channel);
    }

    @Override
    public void close() throws IOException {
        // we must not close the channel before reading it
        flush();
    }

    @Override
    public void write(byte[] b, int off, int len)
            throws IOException {
        // prevent FilterOutputStream from writing inefficiently
        out.write(b, off, len);
    }

    /**
     * @return input stream that contains the encoded attachment contents.
     * The caller is responsible for freeing the stream.
     */
    public InputStream getCachedContents() {
        try {
            // Flush any unwritten data, just in case.
            flush();

            // the channel will be closed when the stream is closed
            return Channels.newInputStream(channel.position(0));
        } catch (IOException ex) { // the position shouldn't really throw
            throw ErrorCodes.translateException(ex);
        }
    }

}
