package ee.cyber.xroad.mediator.util;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

/**
 * Encapsulates piped input and output streams
 */
public class IOPipe {

    public final PipedInputStream in;
    public final PipedOutputStream out;

    /**
     * Constructs a new IO pipe.
     * @throws IOException if an I/O error occurs
     */
    public IOPipe() throws IOException {
        this.in = new PipedInputStream();
        this.out = new PipedOutputStream(this.in);
    }
}
