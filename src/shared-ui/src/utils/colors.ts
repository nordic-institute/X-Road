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

export enum Color {
  //neutral
  WHITE = '#FFFFFF',
  BLACK = '#000000',
  NONE = '#FFFFFF00',
  //dark maroon (D)
  D_10 = '#FCFAFF',
  D_50 = '#F7F5FA',
  D_100 = '#E3E0E9',
  D_200 = '#BEB5D0',
  D_300 = '#8F7DB2',
  D_400 = '#583F8B',
  D_500 = '#3F2080',
  D_600 = '#220066',
  D_700 = '#16094E',
  D_800 = '#0B0633',
  D_900 = '#000428',
  //blue (B)
  B_10 = '#F7FCFF',
  B_50 = '#EAF3F9',
  B_100 = '#DDE8F0',
  B_200 = '#B3CFE5',
  B_300 = '#84BDE9',
  B_400 = '#55ACEE',
  B_500 = '#1B4361',
  B_600 = '#143752',
  B_700 = '#0B283E',
  B_800 = '#0E1D2E',
  B_900 = '#061623',
  //magenta (M)
  M_10 = '#FFF7FD',
  M_50 = '#FFE9F8',
  M_100 = '#FAC3E9',
  M_200 = '#F291D5',
  M_300 = '#EB52BD',
  M_400 = '#DE069E',
  M_500 = '#BB0384',
  M_600 = '#99006B',
  M_700 = '#7A0357',
  M_800 = '#5D0342',
  M_900 = '#470233',
  //red (R)
  R_10 = '#FFFAFA',
  R_50 = '#FFF0F0',
  R_100 = '#FFC3C2',
  R_200 = '#FF9B99',
  R_300 = '#FF7573',
  R_400 = '#FF0503',
  R_500 = '#E80503',
  R_600 = '#B50402',
  R_700 = '#800402',
  R_800 = '#4D0101',
  R_900 = '#330101',
  //yellow (Y)
  Y_10 = '#FEFDF4',
  Y_50 = '#FEFBE0',
  Y_100 = '#FFFACC',
  Y_200 = '#FEF5A3',
  Y_300 = '#FCEF70',
  Y_400 = '#F9E100',
  Y_500 = '#BDAB00',
  Y_600 = '#938500',
  Y_700 = '#736800',
  Y_800 = '#524A00',
  Y_900 = '#2E2900',
  //green (G)
  G_10 = '#FBFFF2',
  G_50 = '#F5FCE6',
  G_100 = '#E5F7BC',
  G_200 = '#D5F291',
  G_300 = '#BBE55C',
  G_400 = '#9CE100',
  G_500 = '#8ECD00',
  G_600 = '#6FA000',
  G_700 = '#567C00',
  G_800 = '#395200',
  G_900 = '#243300',
  //brand
  SPACE_BLACK = BLACK,
  EMPTY_SPACE = WHITE,
  ROCKET_RED = R_400,
  ROCKET_BLUE = B_400,
  ROCKET_YELLOW = Y_400,
  AURORA_GREEN = G_400,
  MILKY_WAY_MAGENTA = M_400,
  NIGHT_VIOLET = '#5500EE',
  SPACE_MAROON = D_700,
}
