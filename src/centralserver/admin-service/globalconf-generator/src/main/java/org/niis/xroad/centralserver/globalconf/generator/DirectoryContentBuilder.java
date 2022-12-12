package org.niis.xroad.centralserver.globalconf.generator;

import ee.ria.xroad.common.util.HashCalculator;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.niis.xroad.centralserver.globalconf.generator.MultipartMessage.header;
import static org.niis.xroad.centralserver.globalconf.generator.MultipartMessage.partBuilder;

@RequiredArgsConstructor
public class DirectoryContentBuilder {
    public static final DateTimeFormatter EXPIRE_DATE_FORMATTER = DateTimeFormatter
            .ofPattern("uuuu-MM-dd'T'HH:mm:ssX")
            .withZone(ZoneOffset.UTC);

    private final HashCalculator hashCalculator;
    private final Instant expireDate;
    private final String pathPrefix;
    private final String instanceIdentifier;

    private List<ConfigurationPart> configurationParts = new ArrayList<>();

    DirectoryContentBuilder contentPart(ConfigurationPart configurationPart) {
        configurationParts.add(configurationPart);
        return this;
    }


    String build() {
        var builder = MultipartMessage.builder();
        builder.part(partBuilder()
                .header(header("Expire-date", EXPIRE_DATE_FORMATTER.format(expireDate)))
                .header(header("Version", "2"))
                .build());
        configurationParts.forEach(confPart -> {
            builder.part(buildPart(confPart));
        });
        return builder.build().toString();

    }

    private MultipartMessage.Part buildPart(ConfigurationPart confPart) {
        return partBuilder()
                .content(calculateHash(confPart.getData()))
                .header(header("Content-type", "application/octet-stream"))
                .header(header("Content-transfer-encoding", "base64"))
                .header(header("Content-identifier",
                        String.format("%s; instance='%s'", confPart.getContentIdentifier(), instanceIdentifier)))
                .header(header("Content-location", String.format("%s/%s", pathPrefix, confPart.getFilename())))
                .header(header("Hash-algorithm-id", hashCalculator.getAlgoURI()))
                .build();
    }

    @SneakyThrows
    private String calculateHash(@NonNull byte[] data) {
        return hashCalculator.calculateFromBytes(data);
    }

}
