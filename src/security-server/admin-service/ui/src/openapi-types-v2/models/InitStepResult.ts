/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { InitializationStep } from './InitializationStep';
import type { InitializationStepStatus } from './InitializationStepStatus';
/**
 * Result of executing an initialization step
 */
export type InitStepResult = {
    step: InitializationStep;
    status: InitializationStepStatus;
    /**
     * whether the step executed successfully
     */
    success: boolean;
    /**
     * optional message about the result
     */
    message?: string;
    /**
     * error code if failed
     */
    error_code?: string;
};

