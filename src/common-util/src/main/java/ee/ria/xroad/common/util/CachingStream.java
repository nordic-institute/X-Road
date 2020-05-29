/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import ee.ria.xroad.common.DefaultFilepaths;
import ee.ria.xroad.common.ErrorCodes;

import lombok.extern.slf4j.Slf4j;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Caches stuff in a temporary file.
 */
@Slf4j
public class CachingStream extends FilterOutputStream {
    private SeekableByteChannel channel;
    private Path tempFile;

    /**
     * Constructs a new caching stream that caches data in a temporary file.
     *
     * @throws IOException if I/O errors occurred
     */
    public CachingStream() throws IOException {
        // Construct the parent class with null stream and replace it later.
        super(null);

        tempFile = DefaultFilepaths.createTempFile("tmpattach", null);
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
    public void write(byte[] b, int off, int len) throws IOException {
        // prevent FilterOutputStream from writing inefficiently
        out.write(b, off, len);
    }

    /**
     * @return input stream that contains the encoded attachment contents.
     * The returned stream does not support mark, and closing the stream has no effect.
     * @see #consume() to free resources used by the cache.
     */
    public CacheInputStream getCachedContents() {
        try {
            return new CacheInputStream(channel);
        } catch (IOException ex) { // the position shouldn't really throw
            throw ErrorCodes.translateException(ex);
        }
    }

    /**
     * Finalize caching stream. Use to avoid file handle leaks.
     */
    public void consume() {
        try {
            channel.close();
        } catch (IOException e) {
            log.warn("Error closing channel of the temporary file '{}'", tempFile.toString(), e);
        }
    }

}
