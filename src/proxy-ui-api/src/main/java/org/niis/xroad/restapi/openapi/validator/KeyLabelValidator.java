package org.niis.xroad.restapi.openapi.validator;

import ee.ria.xroad.common.validation.SpringFirewallValidationRules;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.openapi.model.KeyLabel;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/**
 * Validator to check key labels for control characters such as zero-width-space
 */
@Slf4j
public class KeyLabelValidator implements Validator {

    private static final String LABEL_FIELD_NAME = "label";

    @Override
    public boolean supports(Class<?> clazz) {
        return KeyLabel.class.equals(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        KeyLabel keyLabel = (KeyLabel) target;
        if (SpringFirewallValidationRules.containsControlChars(keyLabel.getLabel())) {
            errors.rejectValue(LABEL_FIELD_NAME, IdentifierValidationErrorInfo.CONTROL_CHAR.getErrorCode(), null,
                    IdentifierValidationErrorInfo.CONTROL_CHAR.getDefaultMessage());
        }
    }
}
