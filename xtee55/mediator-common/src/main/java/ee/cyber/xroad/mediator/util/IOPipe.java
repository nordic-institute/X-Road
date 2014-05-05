package ee.cyber.xroad.mediator.util;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;

public class IOPipe {

    public final PipedInputStream in;
    public final PipedOutputStream out;

    public IOPipe() throws Exception {
        this.in = new PipedInputStream();
        this.out = new PipedOutputStream(this.in);
    }
}
