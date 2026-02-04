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
import { XrdTheme } from '../types';

import { Color } from './colors';

const variables = {
  'high-emphasis-opacity': 1,
  'medium-emphasis-opacity': 1,
  'disabled-opacity': 0.5,
  'border-opacity': 1,
};

export function createDarkTheme(appSpecific: Color, onAppSpecific: Color): XrdTheme {
  const border = Color.B_600;
  return {
    dark: true,
    variables: {
      ...variables,
      'border-color': border,
      'success-container-opacity': 0.15,
      'info-container-opacity': 0.2,
      'warning-container-opacity': 0.15,
      'error-container-opacity': 0.2,
    },
    colors: {
      /* Vuetify colors */
      background: Color.D_900, //TODO XRD8 is it?
      surface: Color.B_700,
      primary: Color.D_200,
      secondary: Color.D_100,
      success: Color.G_300,
      warning: Color.Y_300,
      error: Color.R_300,
      info: Color.B_300,

      'on-background': Color.WHITE, //TODO XRD8 is it?
      'on-surface': Color.WHITE,
      'on-primary': Color.D_700,
      'on-secondary': Color.D_700,
      'on-success': Color.D_900,
      'on-warning': Color.D_900,
      'on-error': Color.D_900,
      'on-info': Color.D_900,

      /* Custom colors */
      'surface-variant': Color.B_700,
      'on-surface-variant': Color.D_200,

      'surface-container': Color.B_800,
      'surface-container-low': Color.B_800,
      'surface-container-lowest': Color.B_900,
      'surface-container-high': Color.B_700,

      'surface-dim': Color.B_900,

      'inverse-primary': Color.M_600,
      'inverse-surface': Color.B_100,
      'on-inverse-surface': Color.M_900,

      special: Color.D_100,
      'special-start': Color.WHITE,
      'on-special': Color.D_700,

      'app-specific': appSpecific,
      'on-app-specific': onAppSpecific,

      'logo-wordmark': Color.WHITE,
      'on-logo-wordmark': onAppSpecific,

      accent: Color.Y_200,
      'on-accent': Color.Y_900,
      'accent-container': Color.B_500,
      'on-accent-container': Color.Y_300,

      tertiary: Color.R_200,
      'on-tertiary': Color.R_900,

      border,
      'border-bright': Color.B_400,
      'border-strong': Color.D_100,

      'success-container': Color.G_400,
      'on-success-container': Color.WHITE,

      'info-container': Color.B_400,
      'on-info-container': Color.WHITE,

      'warning-container': Color.Y_400,
      'on-warning-container': Color.WHITE,

      'error-container': Color.R_400,
      'on-error-container': Color.WHITE,

      'elevation-1': Color.BLACK,

      login: Color.D_100,
      'login-start': Color.WHITE,
    },
  };
}

export function createLightTheme(appSpecific: Color, onAppSpecific: Color): XrdTheme {
  const border = Color.B_100;

  return {
    dark: false,
    variables: {
      ...variables,
      'border-color': border,
      'success-container-opacity': 0.15,
      'info-container-opacity': 0.15,
      'warning-container-opacity': 0.15,
      'error-container-opacity': 0.1,
    },
    colors: {
      /* Vuetify colors */
      background: Color.WHITE, //TODO XRD8 is it?
      surface: Color.B_100,
      primary: Color.D_400,
      secondary: Color.D_600,
      success: Color.G_700,
      warning: Color.Y_700,
      error: Color.R_600,
      info: Color.B_500,

      'on-background': Color.D_900, //TODO XRD8 is it?
      'on-surface': Color.D_900,
      'on-primary': Color.WHITE,
      'on-secondary': Color.WHITE,
      'on-success': Color.WHITE,
      'on-warning': Color.WHITE,
      'on-error': Color.WHITE,
      'on-info': Color.WHITE,

      /* Custom colors */
      'surface-variant': Color.B_100,
      'on-surface-variant': Color.D_400,

      'surface-container': Color.WHITE,
      'surface-container-low': Color.B_50,
      'surface-container-lowest': Color.B_10,
      'surface-container-high': Color.B_50,

      'surface-dim': Color.B_50,

      'inverse-primary': Color.Y_200,
      'inverse-surface': Color.B_700,
      'on-inverse-surface': Color.WHITE,

      special: Color.D_100,
      'special-start': Color.WHITE,
      'on-special': Color.D_700,

      'app-specific': appSpecific,
      'on-app-specific': onAppSpecific,

      'logo-wordmark': Color.D_700,
      'on-logo-wordmark': onAppSpecific,

      accent: Color.M_600,
      'on-accent': Color.WHITE,
      'accent-container': Color.WHITE,
      'on-accent-container': Color.M_600,

      tertiary: Color.M_600,
      'on-tertiary': Color.WHITE,

      border,
      'border-bright': Color.B_400,
      'border-strong': Color.D_600,

      'success-container': Color.G_400,
      'on-success-container': Color.D_700,

      'info-container': Color.B_400,
      'on-info-container': Color.D_700,

      'warning-container': Color.Y_400,
      'on-warning-container': Color.D_700,

      'error-container': Color.R_400,
      'on-error-container': Color.D_700,

      'elevation-1': Color.B_500,

      login: Color.D_800,
      'login-start': Color.D_600,
    },
  };
}
