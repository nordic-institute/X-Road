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

// Helper to copy text to clipboard
function toClipboard(val: string): void {
  // If a dialog is overlaying the entire page we need to put the textbox inside it, otherwise it doesn't get copied
  const container = document.getElementsByClassName('v-dialog--active')[0] || document.body;
  const tempValueContainer = document.createElement('input');
  tempValueContainer.setAttribute('type', 'text');
  tempValueContainer.style.zIndex = '300';
  tempValueContainer.style.opacity = '0';
  tempValueContainer.style.filter = 'alpha(opacity=0)';
  tempValueContainer.setAttribute('data-test', 'generated-temp-value-container');
  tempValueContainer.value = val;
  container.appendChild(tempValueContainer);
  tempValueContainer.select();
  document.execCommand('copy');
  container.removeChild(tempValueContainer);
}

export const helper = {
  toClipboard,
};
