package ee.cyber.sdsb.proxy.protocol;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import ee.cyber.sdsb.common.DefaultFilepaths;
import ee.cyber.sdsb.common.ErrorCodes;

/** Caches stuff in a temporary file. */
class CachingStream extends FilterOutputStream {
    private SeekableByteChannel channel;

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
     * Returns input stream that contains the encoded attachment contents.
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
