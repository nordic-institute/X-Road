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

interface CustomColors extends Colors {
  systemBar: string;
  systemBarInit: string;
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
      background: XrdColors.White, //TODO XRD8 is it?
      surface: XrdColors.Blue100,
      primary: XrdColors.Maroon400,
      secondary: XrdColors.Maroon600,
      success: XrdColors.Green700,
      warning: XrdColors.Yellow700,
      error: XrdColors.Red600,
      info: XrdColors.Blue500,

      'on-background': XrdColors.White, //TODO XRD8 is it?
      'on-surface': XrdColors.Maroon900,
      'on-primary': XrdColors.White,
      'on-secondary': XrdColors.White,
      'on-success': XrdColors.White,
      'on-warning': XrdColors.White,
      'on-error': XrdColors.White,
      'on-info': XrdColors.White,

      systemBar: XrdColors.Magenta900,
      systemBarInit: XrdColors.Maroon700,
    },
  },
};
