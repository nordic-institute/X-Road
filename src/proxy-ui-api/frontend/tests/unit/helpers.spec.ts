
import * as Helpers from '@/util/helpers';
import { Client } from '@/openapi-types';

const arr: Client[] = [
  {
    id: 'CS:ORG:8000:Rek',
    instance_id: 'CS',
    member_name: 'TERM',
    member_class: 'ORG',
    member_code: '8000',
    subsystem_code: 'Rek',
    owner: false,
    connection_type: 'HTTPS',
    status: 'SAVED',
  },
  {
    id: 'CS:ORG:8000:jest',
    instance_id: 'CS',
    member_name: 'TERM',
    member_class: 'ORG',
    member_code: '8000',
    subsystem_code: 'jest',
    owner: false,
    connection_type: 'HTTPS',
    status: 'SAVED',
  },
  {
    id: 'CS:ORG:8000:kraa',
    instance_id: 'CS',
    member_name: 'TERM',
    member_class: 'ORG',
    member_code: '8000',
    subsystem_code: 'kraa',
    owner: false,
    connection_type: 'HTTPS',
    status: 'SAVED',
  },
];

describe('helper functions', () => {

  // REST URL can be http or https
  it('REST URL validation', () => {

    expect(Helpers.isValidRestURL('')).toEqual(false);
    expect(Helpers.isValidRestURL('xx://foo.bar')).toEqual(false);
    expect(Helpers.isValidWsdlURL('https://localhost:0009')).toEqual(true);
    expect(Helpers.isValidRestURL('https://foo.bar')).toEqual(true);
    expect(Helpers.isValidRestURL('http://foo.bar')).toEqual(true);
    expect(Helpers.isValidRestURL('ftp://foo.bar')).toEqual(false);
    expect(Helpers.isValidRestURL('file:///foo.bar')).toEqual(false);
  });

  // WSDL URL can be http or https
  it('WSDL URL validation', () => {

    expect(Helpers.isValidWsdlURL('')).toEqual(false);
    expect(Helpers.isValidWsdlURL('xx://foo.bar')).toEqual(false);
    expect(Helpers.isValidWsdlURL('https://localhost:0009')).toEqual(true);
    expect(Helpers.isValidWsdlURL('https://foo.bar')).toEqual(true);
    expect(Helpers.isValidWsdlURL('http://foo.bar')).toEqual(true);
    expect(Helpers.isValidWsdlURL('ftp://foo.bar')).toEqual(false);
    expect(Helpers.isValidWsdlURL('file:///foo.bar')).toEqual(false);
  });


  // Find client from clients array
  it('Does array contain client', () => {


    expect(Helpers.containsClient(arr, 'ORG', '8000', 'kraa')).toEqual(true);
    expect(Helpers.containsClient(arr, 'ORG', '8000', 'foo')).toEqual(false);
    expect(Helpers.containsClient(arr, 'ORG', '8099', 'kraa')).toEqual(false);
    expect(Helpers.containsClient(arr, 'ORG1', '8000', 'kraa')).toEqual(false);
    expect(Helpers.containsClient(arr, 'ORG', '80001', 'kraa')).toEqual(false);
    expect(Helpers.containsClient(arr, 'ORG', '8000', 'kraa1')).toEqual(false);
    expect(Helpers.containsClient(arr, '', '8000', 'kraa')).toEqual(false);
    expect(Helpers.containsClient(arr, 'ORG', '', 'kraa')).toEqual(false);
    expect(Helpers.containsClient(arr, 'ORG', '8000', '')).toEqual(false);
  });


  // Filter array with excluded key
  it('Filter array with excluded key', () => {

    expect(Helpers.selectedFilter(arr, 'CS:ORG:8000:jest', 'id')).toEqual([]);
    expect(Helpers.selectedFilter(arr, 'CS:ORG:8000:jest')).toHaveLength(1);
    //expect(Helpers.selectedFilter(arr, 'CS:ORG:8000:jest', 'foo')).toHaveLength(1); // TODO: Ask Mikko about this
    expect(Helpers.selectedFilter(arr, 'CS:ORG:8000:jest', 'owner')).toHaveLength(1);
    expect(Helpers.selectedFilter(arr, 'SAVED', 'owner')).toHaveLength(3);
    expect(Helpers.selectedFilter(arr, 'SAVED')).toHaveLength(3);
    //expect(Helpers.selectedFilter(arr, 'SAVED', 'foo')).toHaveLength(3); // TODO: Ask Mikko about this
    expect(Helpers.selectedFilter(arr, 'SAVED', 'status')).toEqual([]);
  });

});
