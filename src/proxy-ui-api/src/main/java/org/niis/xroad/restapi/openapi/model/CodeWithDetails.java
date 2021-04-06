package org.niis.xroad.restapi.openapi.model;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.openapitools.jackson.nullable.JsonNullable;
import javax.validation.Valid;
import javax.validation.constraints.*;

/**
 * object that contains a code identifier and possibly collection of associated metadata or validation errors. Used to relay error and warning information.
 */
@ApiModel(description = "object that contains a code identifier and possibly collection of associated metadata or validation errors. Used to relay error and warning information.")
@javax.annotation.Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2021-04-06T15:01:49.667+03:00[Europe/Helsinki]")

public class CodeWithDetails   {
    @JsonProperty("code")
    private String code;

    @JsonProperty("metadata")
    @Valid
    private List<String> metadata = null;

    @JsonProperty("validation_errors")
    @Valid
    private Map<String, List<String>> validationErrors = null;

    public CodeWithDetails code(String code) {
        this.code = code;
        return this;
    }

    /**
     * identifier of the item (for example errorcode)
     * @return code
     */
    @ApiModelProperty(example = "adding_services", required = true, value = "identifier of the item (for example errorcode)")
    @NotNull


    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public CodeWithDetails metadata(List<String> metadata) {
        this.metadata = metadata;
        return this;
    }

    public CodeWithDetails addMetadataItem(String metadataItem) {
        if (this.metadata == null) {
            this.metadata = new ArrayList<>();
        }
        this.metadata.add(metadataItem);
        return this;
    }

    /**
     * array containing metadata associated with the item. For example names of services were attempted to add, but failed
     * @return metadata
     */
    @ApiModelProperty(value = "array containing metadata associated with the item. For example names of services were attempted to add, but failed")


    public List<String> getMetadata() {
        return metadata;
    }

    public void setMetadata(List<String> metadata) {
        this.metadata = metadata;
    }

    public CodeWithDetails validationErrors(Map<String, List<String>> validationErrors) {
        this.validationErrors = validationErrors;
        return this;
    }

    public CodeWithDetails putValidationErrorsItem(String key, List<String> validationErrorsItem) {
        if (this.validationErrors == null) {
            this.validationErrors = new HashMap<>();
        }
        this.validationErrors.put(key, validationErrorsItem);
        return this;
    }

    /**
     * A dictionary object that contains validation errors bound to their respected fields. The key represents the field where the validation error has happened and the value is a list of validation errors
     * @return validationErrors
     */
    @ApiModelProperty(example = "{\"clientAdd.client.memberCode\":[\"NoPercent\"],\"clientAdd.client.subsystemCode\":[\"NoPercent\",\"NoBackslashes\"]}", value = "A dictionary object that contains validation errors bound to their respected fields. The key represents the field where the validation error has happened and the value is a list of validation errors")

    @Valid

    public Map<String, List<String>> getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(Map<String, List<String>> validationErrors) {
        this.validationErrors = validationErrors;
    }


    @Override
    public boolean equals(java.lang.Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CodeWithDetails codeWithDetails = (CodeWithDetails) o;
        return Objects.equals(this.code, codeWithDetails.code) &&
                Objects.equals(this.metadata, codeWithDetails.metadata) &&
                Objects.equals(this.validationErrors, codeWithDetails.validationErrors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, metadata, validationErrors);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class CodeWithDetails {\n");

        sb.append("    code: ").append(toIndentedString(code)).append("\n");
        sb.append("    metadata: ").append(toIndentedString(metadata)).append("\n");
        sb.append("    validationErrors: ").append(toIndentedString(validationErrors)).append("\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Convert the given object to string with each line indented by 4 spaces
     * (except the first line).
     */
    private String toIndentedString(java.lang.Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString().replace("\n", "\n    ");
    }
}

