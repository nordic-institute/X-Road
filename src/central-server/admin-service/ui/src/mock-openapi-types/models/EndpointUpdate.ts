/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

/**
 * Object for updating endpoints method and/or path
 */
export type EndpointUpdate = {
  /**
   * http method mapped to this endpoint
   */
  method: EndpointUpdate.method;
  /**
   * relative path where this endpoint is mapped to
   */
  path: string;
};

export namespace EndpointUpdate {
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
