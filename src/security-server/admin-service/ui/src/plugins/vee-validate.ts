/*
 * The MIT License
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
import { extend, configure } from 'vee-validate';
import {
  required,
  email,
  min,
  between,
  confirmed,
} from 'vee-validate/dist/rules';
import i18n from '../i18n';
import * as Helpers from '@/util/helpers';

configure({
  // This should be ok, as it is the vee-validate contract
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  defaultMessage: (field, values: any): string => {
    // override the field name.
    values._field_ = i18n.t(`fields.${field}`);

    return i18n.t(`validation.${values._rule_}`, values) as string;
  },
});

// Install required rule and message.
extend('required', required);

// Install email rule and message.
extend('email', email);

// Install min rule and message.
extend('min', min);

// Install between rule and message.
extend('between', between);

// Install the confirmed rule for cross-field validation (password confirm)
extend('confirmed', confirmed);

extend('restUrl', {
  validate: (value) => {
    if (Helpers.isValidRestURL(value)) {
      return true;
    }
    return false;
  },
  message() {
    // You might want to generate a more complex message with this function.
    return i18n.t('customValidation.invalidUrl') as string;
  },
});

extend('wsdlUrl', {
  validate: (value) => {
    if (Helpers.isValidWsdlURL(value)) {
      return true;
    }
    return false;
  },
  message() {
    return i18n.t('customValidation.invalidUrl') as string;
  },
});

const allowedIdentifierPattern = new RegExp('^((?![:/;%\\\\]).)*$');
extend('xrdIdentifier', {
  validate: (value) => allowedIdentifierPattern.test(value),
  message: () => i18n.t('customValidation.invalidXrdIdentifier') as string,
});

const allowedEndpointPattern = new RegExp('^\\S*$'); // No spaces

extend('xrdEndpoint', {
  validate: (value) => allowedEndpointPattern.test(value),
  message: () => i18n.t('customValidation.invalidEndpoint') as string,
});
