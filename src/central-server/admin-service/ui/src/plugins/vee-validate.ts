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
import { between, is, max, min, required } from '@vee-validate/rules';
import * as Helpers from '@/util/helpers';
import { App } from "vue";
import i18n from "@/plugins/i18n";

export function createValidators(i18nMessages = {}) {
  return {
    install(app: App) {
      configure({
        // This should be ok, as it is the vee-validate contract
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        generateMessage: ctx => {
          // override the field name.

          // values._field_ = i18n.t(`fields.${field}`); TODO refactor to work properly

          return i18n.global.t(`validation.messages.${ctx.rule.name}`, {}) as string;
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

// Install is rule and message.
      defineRule('is', is);

      defineRule('url', (value: any) => {
        if (Helpers.isValidUrl(value)) {
          return true;
        }
        return t('customValidation.invalidUrl') as string;
      });
    },
  };
}
