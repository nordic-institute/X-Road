
import * as Helpers from '@/util/helpers';

describe('helper functions', () => {

  // REST URL can be http or https
  it('REST URL validation', () => {

    expect(Helpers.isValidRestURL('')).toEqual(false);
    expect(Helpers.isValidRestURL('xx://foo.bar')).toEqual(false);
    expect(Helpers.isValidRestURL('https://foo')).toEqual(false);
    expect(Helpers.isValidRestURL('https://foo.bar')).toEqual(true);
    expect(Helpers.isValidRestURL('http://foo.bar')).toEqual(true);
    expect(Helpers.isValidRestURL('ftp://foo.bar')).toEqual(false);
    expect(Helpers.isValidRestURL('file://foo.bar')).toEqual(false);
  });

  // WSDL URL can be http, https, ftp, file
  it('WSDL URL validation', () => {

    expect(Helpers.isValidWsdlURL('')).toEqual(false);
    expect(Helpers.isValidWsdlURL('xx://foo.bar')).toEqual(false);
    expect(Helpers.isValidWsdlURL('https://foo')).toEqual(false);
    expect(Helpers.isValidWsdlURL('https://foo.bar')).toEqual(true);
    expect(Helpers.isValidWsdlURL('http://foo.bar')).toEqual(true);
    expect(Helpers.isValidWsdlURL('ftp://foo.bar')).toEqual(true);
    expect(Helpers.isValidWsdlURL('file://foo.bar')).toEqual(true);
  });
});
