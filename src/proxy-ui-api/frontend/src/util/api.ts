import axios, { AxiosRequestConfig } from 'axios';

/*
 * Wraps axios and post method calls with data
 */
export function post(uri: string, data: any, config?: AxiosRequestConfig) {
  return axios.post(uri, data, config);
}

/*
 * Wraps axios patch method calls with data
 */
export function patch(uri: string, data: any) {
  return axios.patch(uri, data);
}

/*
 * Wraps axios put method calls with data
 */
export function put(uri: string, data: any, config?: AxiosRequestConfig) {
  return axios.put(uri, data, config);
}

/*
 * Wraps axios delete method
 */
export function remove(uri: string) {
  return axios.delete(uri);
}

/*
 * Wraps axios get method calls
 */
export function get(uri: string, config?: AxiosRequestConfig) {
  return axios.get(uri, config);
}
