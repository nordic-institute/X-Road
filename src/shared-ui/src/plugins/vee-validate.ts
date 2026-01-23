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
import { configure, defineRule } from 'vee-validate';
import { between, confirmed, email, is, max, min, one_of, required, url } from '@vee-validate/rules';
import { i18n } from '@niis/shared-ui';
import * as Helpers from '@/util/helpers';
import { FieldValidationMetaInfo } from '@vee-validate/i18n';

function _param(params: unknown, idx: number): unknown {
  if (params) {
    return (params as unknown[])[idx];
  }
  return undefined;
}

export function createValidators() {
  return {
    install() {
      const { t } = i18n.global;

      // Common for both security and central server
      configure({
        generateMessage: (ctx: FieldValidationMetaInfo) => {
          const field = ctx.label || (t(`fields.${ctx.field}`) as string);
          const args: Record<string, unknown> = { field };

          switch (ctx.rule?.name) {
            case 'max':
            case 'min': {
              args.length = _param(ctx.rule.params, 0);
              break;
            }
            case 'is': {
              args.other = _param(ctx.rule.params, 0);
              break;
            }
            case 'between': {
              args.min = _param(ctx.rule.params, 0);
              args.max = _param(ctx.rule.params, 1);
              break;
            }
          }
          return i18n.global.t(`validation.messages.${ctx.rule?.name}`, args) as string;
        },
      });

      // Install required rule and message.
      defineRule('required', required);

      // Install min rule and message.
      defineRule('min', min);

      // Install max rule and message.
      defineRule('max', max);

      // Install between rule and message.
      defineRule('between', between);

      defineRule('address', (value: string, _params: unknown, ctx: FieldValidationMetaInfo) => {
        if (!value) {
          return true;
        }
        if (/[^a-zA-Z\d-.]/.test(value)) {
          const field = t('fields.' + ctx.field);
          return i18n.global.t('validation.messages.address', { field });
        }
        return true;
      });

      defineRule('validDescription', (value: string) => {
        if (!value) {
          return true;
        }
        if (/[^a-zA-Z0-9\-_,.;:!? ]/.test(value)) {
          return i18n.global.t('customValidation.description');
        }
        return true;
      });

      // Central server specific

      // Install is rule and message.
      defineRule('is', is);

      defineRule('url', url);

      defineRule('one_of', one_of);

      defineRule('ipAddresses', (value: string) => {
        if (!value) {
          return true;
        }
        for (const ipAddress of value.split(',')) {
          if (!isIP(ipAddress.trim())) {
            return i18n.global.t('customValidation.invalidIpAddress');
          }
        }
        return true;
      });

      // Security server specific

      // Install email rule and message.
      defineRule('email', email);

      // Install the confirmed rule for cross-field validation (password confirm)
      defineRule('confirmed', confirmed);

      defineRule('restUrl', (value: string) => {
        if (Helpers.isValidRestURL(value)) {
          return true;
        }
        return i18n.global.t('customValidation.invalidUrl');
      });

      defineRule('wsdlUrl', (value: string) => {
        if (Helpers.isValidWsdlURL(value)) {
          return true;
        }
        return i18n.global.t('customValidation.invalidUrl');
      });

      const allowedIdentifierPattern = new RegExp('^((?![:/;%\\\\]).)*$');
      defineRule('xrdIdentifier', (value: string) => {
        if (allowedIdentifierPattern.test(value)) {
          return true;
        }
        return i18n.global.t('customValidation.invalidXrdIdentifier');
      });

      const allowedEndpointPattern = new RegExp('^\\S*$'); // No spaces
      defineRule('xrdEndpoint', (value: string) => {
        if (allowedEndpointPattern.test(value)) {
          return true;
        }
        return i18n.global.t('customValidation.invalidEndpoint');
      });
    },
  };
}
