/*
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.common.exception.util;

import lombok.Getter;
import org.niis.xroad.restapi.exceptions.DeviationProvider;

public enum CommonDeviationMessage implements DeviationProvider {
    INTERNAL_ERROR("internal_error", "Internal error. See server logs for more details"),
    GENERIC_VALIDATION_FAILURE("invalid_parameters", "Validation failure"),
    SECURITY_SERVER_NOT_FOUND("security_server_not_found", "Security server not found"),
    INVALID_ENCODED_ID("invalid_encoded_id", "Invalid encoded id"),
    ERROR_ID_NOT_A_NUMBER("id_not_a_number", "Id is not a number"),

    API_KEY_NOT_FOUND("api_key_not_found", "Api key not found"),
    API_KEY_INVALID_ROLE("invalid_role", "Invalid role"),
    INVALID_FILENAME("invalid_filename", "Invalid filename"),
    INVALID_FILE_CONTENT_TYPE("invalid_file_content_type", "Invalid file content type"),
    INVALID_FILE_EXTENSION("invalid_file_extension", "Invalid file extension"),
    DOUBLE_FILE_EXTENSION("double_file_extension", "Double file extension"),
    INVALID_BACKUP_FILE("invalid_backup_file", "Invalid backup file"),

    BACKUP_FILE_NOT_FOUND("backup_file_not_found", "Backup was not found"),
    BACKUP_GENERATION_FAILED("backup_generation_failed", "Failed to generate backup"),
    BACKUP_GENERATION_INTERRUPTED("generate_backup_interrupted", "Backup generation has been interrupted"),
    BACKUP_RESTORATION_FAILED("restore_process_failed", "Failed to generate backup"),
    BACKUP_RESTORATION_INTERRUPTED("backup_restore_interrupted", "Backup restoration has been interrupted"),
    BACKUP_DELETION_FAILED("backup_deletion_failed", "Failed to delete backup"),

    ERROR_RESOURCE_READ("resource_read_failed", "Failed to read resource"),

    ANCHOR_NOT_FOR_EXTERNAL_SOURCE("conf_verification.anchor_not_for_external_source",
            "Configuration verification failed: anchor_not_for_external_source"),
    MISSING_PRIVATE_PARAMS("conf_verification.missing_private_params",
            "Configuration verification failed: missing_private_params"),
    CONF_VERIFICATION_OTHER("conf_verification.other",
            "Configuration verification failed: other"),
    CONF_VERIFICATION_OUTDATED("conf_verification.outdated",
            "Configuration verification failed: outdated"),
    CONF_VERIFICATION_SIGNATURE("conf_verification.signature_invalid",
            "Configuration verification failed: signature_invalid"),
    CONF_VERIFICATION_UNREACHABLE("conf_verification.unreachable",
            "Configuration verification failed: unreachable"),

    ERROR_READING_OPENAPI_FILE("openapi_file_error", "Error reading open api definition file"),
    INITIALIZATION_INTERRUPTED("initialization_interrupted", "Initialization has been interrupted"),;

    @Getter
    private final String code;
    @Getter
    private final String description;

    CommonDeviationMessage(final String code, final String description) {
        this.code = code;
        this.description = description;
    }

}
