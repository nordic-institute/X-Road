package ee.ria.xroad.common.asic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import lombok.RequiredArgsConstructor;

/**
 * Generates unique filenames for a set of ASiC containers.
 */
@RequiredArgsConstructor
public class AsicContainerNameGenerator {

    private final Supplier<String> randomGenerator;
    private final int maxAttempts;

    private final List<String> existingFilenames = new ArrayList<>();

    /**
     * Attempts to generate a unique filename with a random part and given
     * static parts, formatted as "{static#1}-...-{static#N}-{random}.asice".
     * @param staticParts static parts of the filename
     * @return the generated filename
     */
    public String getArchiveFilename(String... staticParts) {
        String result = createFilenameWithRandom(staticParts);

        int attempts = 0;
        while (existingFilenames.contains(result) && attempts++ < maxAttempts) {
            result = createFilenameWithRandom(staticParts);
        }

        existingFilenames.add(result);
        return result;
    }

    private String createFilenameWithRandom(String... staticParts) {
        return AsicUtils.escapeString(String.join("-", staticParts) +
                String.format("-%s.asice", randomGenerator.get()));
    }

}
