/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

/**
 * object describing input fields for CSR subject DN info
 */
export type CsrSubjectFieldDescription = {
    /**
     * the identifier of the field (such as 'O', 'OU' etc)
     */
    readonly id: string;
    /**
     * label of the field, used to display the field in the user interface
     */
    readonly label?: string;
    /**
     * localization key for label of the field, used to display the field in the user interface
     */
    readonly label_key?: string;
    /**
     * the default value of the field. Can be empty.
     */
    readonly default_value?: string;
    /**
     * if this field is read-only
     */
    readonly read_only: boolean;
    /**
     * if this field is required to be filled
     */
    readonly required: boolean;
    /**
     * if true, label key is in property "label_key". If false, actual label is in property "label"
     */
    readonly localized: boolean;
}
