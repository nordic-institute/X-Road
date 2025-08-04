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
import { Colors as XrdColors } from './colors';
import { computed, ComputedRef } from 'vue';

interface CustomColors extends Colors {
  'on-surface-variant': string;
  'surface-container': string;
  'surface-container-low': string;
  'surface-container-lowest': string;
  'surface-dim': string;

  'inverse-surface': string;
  'on-inverse-surface': string;

  'system-bar': string;
  'on-system-bar': string;

  'system-bar-init': string;
  'on-system-bar-init': string;

  special: string;
  'special-start': string;
  'on-special': string;

  accent: string;
  'on-accent': string;
  'accent-container': string;
  'on-accent-container': string;

  tertiary: string;
  'on-tertiary': string;

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

interface CustomTheme extends ThemeDefinition {
  colors: CustomColors;
}

type Themes = {
  light: CustomTheme;
};

export const themes: Themes = {
  light: {
    dark: false,
    colors: {
      /* Vuetify colors */
      background: XrdColors.White, //TODO XRD8 is it?
      surface: XrdColors.Blue100,
      primary: XrdColors.Maroon400,
      secondary: XrdColors.Maroon600,
      success: XrdColors.Green700,
      warning: XrdColors.Yellow700,
      error: XrdColors.Red600,
      info: XrdColors.Blue500,

      'on-background': XrdColors.Maroon900, //TODO XRD8 is it?
      'on-surface': XrdColors.Maroon900,
      'on-primary': XrdColors.White,
      'on-secondary': XrdColors.White,
      'on-success': XrdColors.White,
      'on-warning': XrdColors.White,
      'on-error': XrdColors.White,
      'on-info': XrdColors.White,

      /* Custom colors */
      'surface-variant': XrdColors.Blue100,
      'on-surface-variant': XrdColors.Maroon400,

      'surface-container': XrdColors.White,
      'surface-container-low': XrdColors.Blue50,
      'surface-container-lowest': XrdColors.Blue10,

      'surface-dim': XrdColors.Blue50,

      'inverse-surface': XrdColors.Blue700,
      'on-inverse-surface': XrdColors.White,

      special: XrdColors.Maroon100,
      'special-start': XrdColors.White,
      'on-special': XrdColors.Maroon700,

      'system-bar': XrdColors.Magenta900,
      'on-system-bar': XrdColors.Maroon100,

      'system-bar-init': XrdColors.Magenta900,
      'on-system-bar-init': XrdColors.Maroon100,

      accent: XrdColors.Magenta600,
      'on-accent': XrdColors.White,
      'accent-container': XrdColors.White,
      'on-accent-container': XrdColors.Magenta600,

      tertiary: XrdColors.Magenta600,
      'on-tertiary': XrdColors.White,

      'border-bright': XrdColors.Blue400,
      'border-strong': XrdColors.Maroon600,

      'success-container': XrdColors.Green400,
      'on-success-container': XrdColors.Maroon700,

      'info-container': XrdColors.Blue400,
      'on-info-container': XrdColors.Maroon700,

      'warning-container': XrdColors.Yellow400,
      'on-warning-container': XrdColors.Maroon700,

      'error-container': XrdColors.Red400,
      'on-error-container': XrdColors.Maroon700,

      'elevation-1': XrdColors.Blue500,

      login: XrdColors.Maroon800,
      'login-start': XrdColors.Maroon600,
    } as CustomColors,
    variables: {
      'high-emphasis-opacity': 1,
      'medium-emphasis-opacity': 1,
      'disabled-opacity': 0.5,
      'border-opacity': 1,
      'border-color': XrdColors.Blue100,
    },
  },
};

export function useColors(): { colors: ComputedRef<CustomColors> } {
  const colors = computed(() => themes.light.colors);
  return {
    colors,
  };
}
