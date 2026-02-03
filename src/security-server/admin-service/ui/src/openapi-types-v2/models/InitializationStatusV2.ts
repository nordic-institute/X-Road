/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { InitializationOverallStatus } from './InitializationOverallStatus';
import type { InitializationStep } from './InitializationStep';
import type { InitializationStepInfo } from './InitializationStepInfo';
/**
 * Complete initialization status with granular step tracking
 */
export type InitializationStatusV2 = {
    overall_status: InitializationOverallStatus;
    /**
     * whether the configuration anchor has been imported
     */
    anchor_imported: boolean;
    /**
     * detailed status for each initialization step
     */
    steps: Array<InitializationStepInfo>;
    /**
     * list of steps that are pending
     */
    pending_steps: Array<InitializationStep>;
    /**
     * list of steps that have failed
     */
    failed_steps: Array<InitializationStep>;
    /**
     * list of steps that have completed
     */
    completed_steps: Array<InitializationStep>;
    /**
     * whether all required steps are completed
     */
    fully_initialized: boolean;
    /**
     * the security server ID if serverconf is initialized
     */
    security_server_id?: string;
    /**
     * whether token PIN policy is enforced
     */
    token_pin_policy_enforced?: boolean;
};

