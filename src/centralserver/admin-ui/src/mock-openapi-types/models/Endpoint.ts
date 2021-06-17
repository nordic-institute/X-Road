/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

/**
 * Endpoint for a service
 */
export type Endpoint = {
    /**
     * unique identifier
     */
    id?: string;
    service_code: string;
    /**
     * http method mapped to this endpoint
     */
    method: Endpoint.method;
    /**
     * relative path where this endpoint is mapped to
     */
    path: string;
    /**
     * has endpoint been generated from openapi3 description
     */
    readonly generated?: boolean;
}

export namespace Endpoint {

    /**
     * http method mapped to this endpoint
     */
    export enum method {
        _ = '*',
        GET = 'GET',
        POST = 'POST',
        PUT = 'PUT',
        DELETE = 'DELETE',
        PATCH = 'PATCH',
        HEAD = 'HEAD',
        OPTIONS = 'OPTIONS',
        TRACE = 'TRACE',
    }


}
