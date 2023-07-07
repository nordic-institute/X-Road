/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

import type { GroupMember } from './GroupMember';

/**
 * group
 */
export type LocalGroup = {
    /**
     * unique identifier
     */
    readonly id?: string;
    /**
     * group code
     */
    code: string;
    /**
     * group description
     */
    description: string;
    /**
     * member count
     */
    member_count?: number;
    /**
     * last time updated
     */
    updated_at?: string;
    /**
     * group members
     */
    members?: Array<GroupMember>;
}
