package ee.ria.xroad.common.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InMemoryFileSourceChangeCheckerTest {

    private FileSource<InMemoryFile> sourceMock;
    private InMemoryFileSourceChangeChecker checker;

    @BeforeEach
    void setUp() {
        sourceMock = mock(FileSource.class);
    }

    @Test
    void testHasChangedWhenFileChanged() throws Exception {
        var initialChecksum = "initialChecksum";
        var newChecksum = "newChecksum";

        var fileMock = new InMemoryFile("", newChecksum);
        when(sourceMock.getFile()).thenReturn(Optional.of(fileMock));

        checker = new InMemoryFileSourceChangeChecker(sourceMock, initialChecksum);

        assertTrue(checker.hasChanged());
    }

    @Test
    void testHasChangedWhenFileNotChanged() throws Exception {
        var checksum = "sameChecksum";

        var fileMock = new InMemoryFile("", checksum);
        when(sourceMock.getFile()).thenReturn(Optional.of(fileMock));

        checker = new InMemoryFileSourceChangeChecker(sourceMock, checksum);

        assertFalse(checker.hasChanged());
    }

    @Test
    void testHasChangedWhenFileNotPresent() throws Exception {
        when(sourceMock.getFile()).thenReturn(Optional.empty());

        checker = new InMemoryFileSourceChangeChecker(sourceMock, "anyChecksum");

        assertTrue(checker.hasChanged());
    }
}
