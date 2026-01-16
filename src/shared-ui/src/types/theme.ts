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

// Version 8.0 colors as enum.
import { ThemeDefinition } from 'vuetify/framework';
import { Colors } from 'vuetify/lib/composables/theme';

interface CustomColors extends Colors {
  'on-surface-variant': string;
  'surface-container': string;
  'surface-container-low': string;
  'surface-container-lowest': string;
  'surface-container-high': string;
  'surface-dim': string;

  'inverse-primary': string;
  'inverse-surface': string;
  'on-inverse-surface': string;

  'app-specific': string; // in figma a.k.a security or central
  'on-app-specific': string;

  'logo-wordmark': string;
  'on-logo-wordmark': string;

  special: string;
  'special-start': string;
  'on-special': string;

  accent: string;
  'on-accent': string;
  'accent-container': string;
  'on-accent-container': string;

  tertiary: string;
  'on-tertiary': string;

  border: string;
  'border-bright': string;
  'border-strong': string;

  'success-container': string;
  'on-success-container': string;

  'info-container': string;
  'on-info-container': string;

  'warning-container': string;
  'on-warning-container': string;

  'error-container': string;
  'on-error-container': string;

  'elevation-1': string;

  //Used for login page side-panel gradient
  login: string;
  'login-start': string;
}

export interface XrdTheme extends ThemeDefinition {
  colors: CustomColors;
}
