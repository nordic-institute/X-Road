/*
 * The MIT License
 *
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
package ee.ria.xroad.common.messagelog.archive;

import ee.ria.xroad.common.SystemProperties;

import org.bouncycastle.bcpg.BCPGInputStream;
import org.bouncycastle.bcpg.PacketTags;
import org.bouncycastle.bcpg.PublicKeyEncSessionPacket;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class GPGOutputStreamTest {
    private static final Path GPG_HOME = Paths.get("build/gpg");
    private static final Set<String> KEYS = Collections.singleton("AAAA");

    @Before
    public void before() {
        Assume.assumeTrue(Files.isExecutable(Paths.get("/usr/bin/gpg")));
        System.setProperty(SystemProperties.TEMP_FILES_PATH, "build/tmp");
    }

    @Test(expected = GPGOutputStream.GPGException.class)
    public void shouldFailIfInvalidRecipient() throws IOException {
        final Path path = Files.createTempFile(Paths.get(SystemProperties.getTempFilesPath()), null, null);
        try (GPGOutputStream gpgStream = new GPGOutputStream(GPG_HOME, path, KEYS)) {
            gpgStream.write(42);
        }
    }

    @Test
    public void shouldEncrypt() throws IOException {
        final Path path = Files.createTempFile(Paths.get(SystemProperties.getTempFilesPath()), null, null);
        try (GPGOutputStream gpgStream = new GPGOutputStream(GPG_HOME, path, null /* self as recipient */)) {
            gpgStream.write(42);
        }

        //sanity check -- resulting file should be a PGP file
        try (BCPGInputStream is = new BCPGInputStream(Files.newInputStream(path))) {
            assertEquals(PacketTags.PUBLIC_KEY_ENC_SESSION, is.nextPacketTag());
            final PublicKeyEncSessionPacket packet = (PublicKeyEncSessionPacket) is.readPacket();
            assertNotNull(packet.getEncSessionKey());
        }

        try (GPGInputStream is = new GPGInputStream(GPG_HOME, path)) {
            assertEquals(42, is.read());
            assertEquals(-1, is.read());
        }
    }
}
