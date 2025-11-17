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

import 'vuetify/styles';
import '@fontsource-variable/material-symbols-rounded/fill.css';
import '@fontsource-variable/open-sans';
import '../assets/global-style.scss';

import { createVuetify } from 'vuetify/framework';
import { msrAliases, msr, createLightTheme, createDarkTheme, Color } from '../utils';
import { createVueI18nAdapter } from 'vuetify/locale/adapters/vue-i18n';
import { i18n } from './i18n';
import { useI18n } from 'vue-i18n';
import { VTextField } from 'vuetify/components';
import { XRD_THEME } from '../composables';

export function createXrdVuetify(appSpecificLight: Color, onAppSpecificLight: Color, appSpecificDark: Color, onAppSpecificDark: Color) {
  return createVuetify({
    aliases: {
      XrdSearchField: VTextField,
      XrdRoundedSearchField: VTextField,
    },
    defaults: {
      global: {},
      VDataTable: {
        loaderHeight: 2,
      },
      VTextField: {
        variant: 'underlined',
      },
      VTooltip: {
        maxWidth: '280',
        contentClass: 'bg-inverse-surface',
      },
      VSelect: {
        variant: 'underlined',
        listProps: {
          class: 'xrd-select-list',
        },
      },
      VCombobox: {
        variant: 'underlined',
        listProps: {
          class: 'xrd-select-list',
        },
      },
      VAutocomplete: {
        variant: 'underlined',
        listProps: {
          class: 'xrd-select-list',
        },
      },
      VDateInput: {
        variant: 'underlined',
      },
      VTimeInput: {
        variant: 'underlined',
      },
      XrdSearchField: {
        class: 'xrd xrd-search-field',
        prependInnerIcon: 'search',
        variant: 'underlined',
        density: 'compact',
        singleLine: true,
        hideDetails: true,
      },
      XrdRoundedSearchField: {
        class: 'xrd xrd-search-field',
        prependInnerIcon: 'search',
        variant: 'outlined',
        rounded: 'pill',
        density: 'compact',
        singleLine: true,
        hideDetails: true,
      },
    },
    icons: {
      defaultSet: 'msr',
      aliases: msrAliases,
      sets: {
        msr,
      },
    },
    locale: {
      adapter: createVueI18nAdapter({ i18n, useI18n }),
    },
    theme: {
      defaultTheme: localStorage.getItem(XRD_THEME) ?? 'system',
      themes: {
        light: createLightTheme(appSpecificLight, onAppSpecificLight),
        dark: createDarkTheme(appSpecificDark, onAppSpecificDark),
      },
    },
  });
}
