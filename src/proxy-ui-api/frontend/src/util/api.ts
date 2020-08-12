// Ok to ignore any here because that is the axios contract as well
/* eslint-disable @typescript-eslint/no-explicit-any */

import axios, { AxiosRequestConfig, AxiosResponse } from 'axios';

/*
 * Wrapper that is used to encode path variables in URL-s
 */
export function encodePathParameter(value: string | number | boolean): string {
  return encodeURIComponent(value);
}

export type PostPutPatch = <T>(
  uri: string,
  data: any,
  config?: AxiosRequestConfig,
) => Promise<AxiosResponse<T>>;

/*
 * Wraps axios and post method calls with data
 */
export function post<T>(
  uri: string,
  data: any,
  config?: AxiosRequestConfig,
): Promise<AxiosResponse<T>> {
  return axios.post<T>(uri, data, config);
}

/*
 * Wraps axios patch method calls with data
 */
export function patch<T>(
  uri: string,
  data: any,
  config?: AxiosRequestConfig,
): Promise<AxiosResponse<T>> {
  return axios.patch<T>(uri, data, config);
}

/*
 * Wraps axios put method calls with data
 */
export function put<T>(
  uri: string,
  data: any,
  config?: AxiosRequestConfig,
): Promise<AxiosResponse<T>> {
  return axios.put<T>(uri, data, config);
}

/*
 * Wraps axios delete method
 */
export function remove<T>(uri: string): Promise<AxiosResponse<T>> {
  return axios.delete<T>(uri);
}

/*
 * Wraps axios get method calls
 */
export function get<T>(
  uri: string,
  config?: AxiosRequestConfig,
): Promise<AxiosResponse<T>> {
  return axios.get<T>(uri, config);
}
