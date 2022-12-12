package org.niis.xroad.centralserver.globalconf.generator;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class ConfigurationPart {
    @NonNull String filename;
    @NonNull String contentIdentifier;
    @NonNull byte[] data;
}
