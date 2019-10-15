import axios from 'axios';

/*
 * Wraps axios and post method calls with data
 */
export function post(uri: string, data: any) {
  return axios.post(uri, data);
}

/*
 * Wraps axios put method calls with data
 */
export function put(uri: string, data: any) {
  return axios.put(uri, data);
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
export function get(uri: string) {
  return axios.get(uri);
}

