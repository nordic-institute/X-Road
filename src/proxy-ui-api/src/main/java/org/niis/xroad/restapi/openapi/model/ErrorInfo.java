package org.niis.xroad.restapi.openapi.model;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.List;
import org.niis.xroad.restapi.openapi.model.CodeWithDetails;
import org.openapitools.jackson.nullable.JsonNullable;
import javax.validation.Valid;
import javax.validation.constraints.*;

/**
 * object returned in error cases
 */
@ApiModel(description = "object returned in error cases")
@javax.annotation.Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2021-04-06T14:58:59.549+03:00[Europe/Helsinki]")

public class ErrorInfo   {
    @JsonProperty("status")
    private Integer status;

    @JsonProperty("error")
    private CodeWithDetails error;

    @JsonProperty("warnings")
    @Valid
    private List<CodeWithDetails> warnings = null;

    public ErrorInfo status(Integer status) {
        this.status = status;
        return this;
    }

    /**
     * http status code
     * @return status
     */
    @ApiModelProperty(example = "400", required = true, value = "http status code")
    @NotNull


    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public ErrorInfo error(CodeWithDetails error) {
        this.error = error;
        return this;
    }

    /**
     * Get error
     * @return error
     */
    @ApiModelProperty(value = "")

    @Valid

    public CodeWithDetails getError() {
        return error;
    }

    public void setError(CodeWithDetails error) {
        this.error = error;
    }

    public ErrorInfo warnings(List<CodeWithDetails> warnings) {
        this.warnings = warnings;
        return this;
    }

    public ErrorInfo addWarningsItem(CodeWithDetails warningsItem) {
        if (this.warnings == null) {
            this.warnings = new ArrayList<>();
        }
        this.warnings.add(warningsItem);
        return this;
    }

    /**
     * warnings that could be ignored
     * @return warnings
     */
    @ApiModelProperty(value = "warnings that could be ignored")

    @Valid

    public List<CodeWithDetails> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<CodeWithDetails> warnings) {
        this.warnings = warnings;
    }


    @Override
    public boolean equals(java.lang.Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ErrorInfo errorInfo = (ErrorInfo) o;
        return Objects.equals(this.status, errorInfo.status) &&
                Objects.equals(this.error, errorInfo.error) &&
                Objects.equals(this.warnings, errorInfo.warnings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, error, warnings);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class ErrorInfo {\n");

        sb.append("    status: ").append(toIndentedString(status)).append("\n");
        sb.append("    error: ").append(toIndentedString(error)).append("\n");
        sb.append("    warnings: ").append(toIndentedString(warnings)).append("\n");
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

