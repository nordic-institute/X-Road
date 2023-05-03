/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

/**
 * object that contains a code identifier and possibly collection of associated metadata or validation errors. Used to relay error and warning information.
 */
export type CodeWithDetails = {
    /**
     * identifier of the item (for example errorcode)
     */
    code: string;
    /**
     * array containing metadata associated with the item. For example names of services were attempted to add, but failed
     */
    metadata?: Array<string>;
    /**
     * A dictionary object that contains validation errors bound to their respected fields. The key represents the field where the validation error has happened and the value is a list of validation errors
     */
    validation_errors?: Record<string, Array<string>>;
}
