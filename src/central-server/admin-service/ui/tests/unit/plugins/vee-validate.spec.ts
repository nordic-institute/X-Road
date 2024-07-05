/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

import createValidators from '@/plugins/vee-validate';
import { validate } from 'vee-validate';
import en from '@/locales/en.json';
import { describe, expect, it } from 'vitest';

describe('vee-validate', () => {
  describe('ipAddresses', () => {
    createValidators.install();

    it('should validate ip v4 correctly', async () => {
      let result = await validate('192.3.4.XX', 'ipAddresses');
      expect(result.errors[0]).toBe(en.customValidation.invalidIpAddress);
      result = await validate('12.3.04.5', 'ipAddresses');
      expect(result.errors[0]).toBe(en.customValidation.invalidIpAddress);
      result = await validate('256.3.4.5', 'ipAddresses');
      expect(result.errors[0]).toBe(en.customValidation.invalidIpAddress);
      result = await validate('12.3.4.5.', 'ipAddresses');
      expect(result.errors[0]).toBe(en.customValidation.invalidIpAddress);
      result = await validate('-1.3.4.5', 'ipAddresses');
      expect(result.errors[0]).toBe(en.customValidation.invalidIpAddress);
      result = await validate('12.3.4', 'ipAddresses');
      expect(result.errors[0]).toBe(en.customValidation.invalidIpAddress);
      result = await validate('12.3.4.5', 'ipAddresses');
      expect(result.errors[0]).toBe(undefined);
    });

    it('should validate ip v6 correctly', async () => {
      let result = await validate(
        '1111:2222:3333:4444:AAAA:BBBB:CCCC:XXXX',
        'ipAddresses',
      );
      expect(result.errors[0]).toBe(en.customValidation.invalidIpAddress);
      result = await validate(
        '1111:2222:3333:4444:AAAA:BBBB:CCCC:FFFF:',
        'ipAddresses',
      );
      expect(result.errors[0]).toBe(en.customValidation.invalidIpAddress);
      result = await validate(
        '1111:2222:3333:4444:AAAA:CCCC:FFFF:',
        'ipAddresses',
      );
      expect(result.errors[0]).toBe(en.customValidation.invalidIpAddress);
      result = await validate('1111:222:3:::BBBB:C:FFF', 'ipAddresses');
      expect(result.errors[0]).toBe(en.customValidation.invalidIpAddress);
      result = await validate(
        '1111:2222:3333:4444:AAAA:BBBB:CCCC:FFFF',
        'ipAddresses',
      );
      expect(result.errors[0]).toBe(undefined);
      result = await validate('11:222:3:44:AAA:B9E8:C:FFF', 'ipAddresses');
      expect(result.errors[0]).toBe(undefined);
      result = await validate('1234:3::BCDE:C:FFF', 'ipAddresses');
      expect(result.errors[0]).toBe(undefined);
      result = await validate('Aa:bB::cd:EE:fFF', 'ipAddresses');
      expect(result.errors[0]).toBe(undefined);
    });

    it('should validate multiple ip-s correctly', async () => {
      let result = await validate('12.3.4.5,192.3.04.XX', 'ipAddresses');
      expect(result.errors[0]).toBe(en.customValidation.invalidIpAddress);
      result = await validate('1:2:3:4:5:6:7.8,22:33::44:55:FF', 'ipAddresses');
      expect(result.errors[0]).toBe(en.customValidation.invalidIpAddress);
      result = await validate(
        '1.2.3.4,5.6.7.8,a:b:c:d:e:f:0:1,22:33:::44:55:FF',
        'ipAddresses',
      );
      expect(result.errors[0]).toBe(en.customValidation.invalidIpAddress);
      result = await validate('12.3.4.5,192.3.44.5,', 'ipAddresses');
      expect(result.errors[0]).toBe(en.customValidation.invalidIpAddress);
      result = await validate('12.3.4.5,,192.3.44.5', 'ipAddresses');
      expect(result.errors[0]).toBe(en.customValidation.invalidIpAddress);
      result = await validate('12.3.4.5,6.7.8.9', 'ipAddresses');
      expect(result.errors[0]).toBe(undefined);
      result = await validate('12.3.4.5, 6.7.8.9', 'ipAddresses');
      expect(result.errors[0]).toBe(undefined);
      result = await validate('1:2:3:4:5:6:7:8,22:33::44:55:FF', 'ipAddresses');
      expect(result.errors[0]).toBe(undefined);
      result = await validate('12.3.4.5,1:2:3:4:5:6:7:8', 'ipAddresses');
      expect(result.errors[0]).toBe(undefined);
    });
  });
});
