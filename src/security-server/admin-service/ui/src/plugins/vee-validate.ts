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
import { configure, defineRule } from 'vee-validate';
import {
  required,
  email,
  max,
  min,
  between,
  confirmed,
} from '@vee-validate/rules';
import i18n from './i18n';
import * as Helpers from '@/util/helpers';
import { FieldValidationMetaInfo } from '@vee-validate/i18n';

export function createValidators() {
  return {
    install() {
      configure({
        generateMessage: (ctx: FieldValidationMetaInfo): string => {
          const field = ctx.label || i18n.global.t(`fields.${ctx.field}`);
          const args: Record<string, unknown> = { field };
          const ruleParams = ctx.rule?.params;
          switch (ctx.rule?.name) {
            case 'max': {
              args.length = Array.isArray(ruleParams)
                ? ruleParams?.[0]
                : ruleParams?.['max'];
              break;
            }
            case 'min': {
              args.length = Array.isArray(ruleParams)
                ? ruleParams?.[0]
                : ruleParams?.['min'];
              break;
            }
            case 'between': {
              args.min = Array.isArray(ruleParams)
                ? ruleParams?.[0]
                : ruleParams?.['min'];
              args.max = Array.isArray(ruleParams)
                ? ruleParams?.[1]
                : ruleParams?.['max'];
              break;
            }
          }
          return i18n.global.t(`validation.messages.${ctx.rule?.name}`, args);
        },
      });

      // Install required rule and message.
      defineRule('required', required);

      // Install email rule and message.
      defineRule('email', email);

      // Install min rule and message.
      defineRule('min', min);

      // Install min rule and message.
      defineRule('max', max);

      // Install between rule and message.
      defineRule('between', between);

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
