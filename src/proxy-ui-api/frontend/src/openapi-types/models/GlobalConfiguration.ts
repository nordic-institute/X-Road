/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { ConfigurationStatus } from './ConfigurationStatus';

/**
 * global configuration
 */
export interface GlobalConfiguration {
    status: ConfigurationStatus;
    /**
     * last time updated
     */
    updated_at: string;
    /**
     * last time updated
     */
    next_update_at: string;
}
