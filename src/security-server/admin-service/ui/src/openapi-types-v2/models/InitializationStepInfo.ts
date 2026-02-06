/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { InitializationStep } from './InitializationStep';
import type { InitializationStepStatus } from './InitializationStepStatus';
/**
 * Detailed status information for a single initialization step
 */
export type InitializationStepInfo = {
    step: InitializationStep;
    status: InitializationStepStatus;
    /**
     * when the step started executing
     */
    started_at?: string;
    /**
     * when the step completed
     */
    completed_at?: string;
    /**
     * human-readable error message if failed
     */
    error_message?: string;
    /**
     * machine-readable error code if failed
     */
    error_code?: string;
    /**
     * whether this step can be retried after failure
     */
    retryable: boolean;
};

