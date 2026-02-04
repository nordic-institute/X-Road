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
package org.niis.xroad.cli;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ArchiveHashChainVerifier {

    public static void main(String[] args) {
        ArchiveHashChainVerifier verifier = new ArchiveHashChainVerifier();

        try {
            verifier.run(args);
        } catch (MessageArchiveExtractor.InvalidLogArchiveException e) {
            exitWithError(args[0], e.getMessage());
        } catch (InputErrorException e) {
            System.err.println("INPUT ERROR: " + e.getMessage());
            printUsage();
            System.exit(1);
        }

    }

    public void run(String[] args) throws InputErrorException, MessageArchiveExtractor.InvalidLogArchiveException {
        String[] parsedArgs = parseArguments(args);
        String archiveFile = parsedArgs[0];
        String prevDigest = parsedArgs[1];

        MessageArchiveExtractor extractor = new MessageArchiveExtractor(archiveFile);
        MessageArchiveExtractor.ExtractionResult extractedArchive = extractor.extract();

        verify(archiveFile, prevDigest, extractedArchive);
        printLastDigest(extractedArchive);
    }

    private void verify(final String archiveFile, final String prevDigest,
                        final MessageArchiveExtractor.ExtractionResult extractedArchive)
            throws MessageArchiveExtractor.InvalidLogArchiveException {
        List<MessageArchiveExtractor.AsicContainer> asicContainers = extractedArchive.asicContainers();
        LinkingInfo linkingInfo = extractedArchive.linkingInfo();

        if (asicContainers.isEmpty()) {
            throw new MessageArchiveExtractor.InvalidLogArchiveException("There are no ASiC containers in archive file '"
                    + archiveFile + "', at least one is expected.");
        }

        if (!prevDigest.equals(linkingInfo.getPrevDigest())) {
            throw new MessageArchiveExtractor.InvalidLogArchiveException("Last hash steps given by user and in linking info differ\n"
                    + "\tBy user: '" + prevDigest + "'\n"
                    + "\tIn linking info: '" + linkingInfo.getPrevDigest() + "'\n");
        }

        Set<String> linkingInfoFileNames = linkingInfo.fileNames();
        Set<String> inArchiveFileNames = asicContainers.stream()
                .map(MessageArchiveExtractor.AsicContainer::name)
                .collect(Collectors.toSet());

        if (!linkingInfoFileNames.equals(inArchiveFileNames)) {
            throw new MessageArchiveExtractor.InvalidLogArchiveException("File names in linking info and in archive file differ\n"
                    + "\tIn archive file: '" + String.join(", ", inArchiveFileNames) + "'\n"
                    + "\tIn linking info: '" + String.join(", ", linkingInfoFileNames) + "'\n");
        }

        for (MessageArchiveExtractor.AsicContainer container : asicContainers) {
            String fileName = container.name();
            String fileDigest = container.digest();

            String linkingInfoDigest = linkingInfo.digestForFile(fileName);

            if (!fileDigest.equals(linkingInfoDigest)) {
                throw new MessageArchiveExtractor.InvalidLogArchiveException("Digests of file '" + fileName + "' do not match:\n"
                        + "\tDigest in linking info: " + linkingInfoDigest + "\n"
                        + "\tActual file digest: " + fileDigest + "\n");
            }
        }
    }

    private void printLastDigest(MessageArchiveExtractor.ExtractionResult extractedArchive) {
        System.out.println(extractedArchive.lastDigest());
    }

    private static void exitWithError(String archiveFile, String errorMessage) {
        System.err.println("ERROR: Archive file '" + archiveFile + "' is invalid, reason:\n" + errorMessage);
        System.exit(1);
    }

    private static void printUsage() {
        System.err.println("Program must be invoked like this:\n"
                + "java -jar messagelog-archive-verifier.jar <pathToZippedAsicContainersArchive> "
                + "<(previousArchiveHexDigest) or (-f) or (--first)>");
    }

    private String[] parseArguments(String[] args) throws InputErrorException {
        if (args.length != 2) {
            throw new InputErrorException("Invalid arguments. Expected 2 arguments, got " + args.length + ".");
        }

        String archiveFile = args[0];
        String prevDigest = "";

        if (!firstInHashChain(args)) {
            prevDigest = args[1];
        }

        return new String[]{archiveFile, prevDigest};
    }

    private boolean firstInHashChain(String[] args) {
        String secondArg = args.length > 1 ? args[1].toLowerCase() : "";
        return secondArg.equals("-f") || secondArg.equals("--first");
    }

    public static class InputErrorException extends Exception {
        public InputErrorException(String message) {
            super(message);
        }
    }
}
