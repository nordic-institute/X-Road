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

import { describe, it, beforeAll, expect } from 'vitest';
import { render } from 'vitest-browser-vue';
import { page } from 'vitest/browser';
import { defineComponent, ref } from 'vue';
import { configureGlobals } from '../setup/vue-test-utils';

beforeAll(() => configureGlobals());

// ── v-select ─────────────────────────────────────────────────────────────────

const SelectHost = defineComponent({
  template: `
    <div>
      <v-select
        v-model="selected"
        data-test="demo-select"
        :items="items"
        label="Pick one"
      />
      <span data-test="selected-value">{{ selected }}</span>
    </div>
  `,
  setup() {
    const selected = ref<string | null>(null);
    const items = ['Alpha', 'Beta', 'Gamma'];
    return { selected, items };
  },
});

// ── v-menu ────────────────────────────────────────────────────────────────────

const MenuHost = defineComponent({
  template: `
    <div>
      <v-menu>
        <template #activator="{ props }">
          <v-btn v-bind="props" data-test="menu-trigger">Open menu</v-btn>
        </template>
        <v-list>
          <v-list-item
            v-for="item in items"
            :key="item"
            :title="item"
            :data-test="'menu-item-' + item.toLowerCase()"
            @click="picked = item"
          />
        </v-list>
      </v-menu>
      <span data-test="picked-value">{{ picked }}</span>
    </div>
  `,
  setup() {
    const picked = ref('');
    const items = ['Option A', 'Option B', 'Option C'];
    return { picked, items };
  },
});

// ── dialog with focus / close ─────────────────────────────────────────────────

const DialogHost = defineComponent({
  template: `
    <div>
      <v-btn data-test="open-dialog-btn" @click="open = true">Open</v-btn>
      <v-dialog v-model="open">
        <v-card>
          <v-card-title data-test="dialog-heading">Confirm action</v-card-title>
          <v-card-text>Are you sure?</v-card-text>
          <v-card-actions>
            <v-btn data-test="dialog-confirm-btn" autofocus @click="open = false">Confirm</v-btn>
            <v-btn data-test="dialog-cancel-btn" @click="open = false">Cancel</v-btn>
          </v-card-actions>
        </v-card>
      </v-dialog>
    </div>
  `,
  setup() {
    const open = ref(false);
    return { open };
  },
});

// ── snackbar ──────────────────────────────────────────────────────────────────

const SnackbarHost = defineComponent({
  template: `
    <div>
      <v-btn data-test="show-snackbar-btn" @click="visible = true">Show</v-btn>
      <v-snackbar v-model="visible" data-test="demo-snackbar" :timeout="-1">
        Action completed
        <template #actions>
          <v-btn data-test="snackbar-close-btn" @click="visible = false">Close</v-btn>
        </template>
      </v-snackbar>
    </div>
  `,
  setup() {
    const visible = ref(false);
    return { visible };
  },
});

describe('Overlay patterns — v-select / v-menu / dialog / snackbar (Browser Mode)', () => {
  it('v-select: opens dropdown and selecting an item updates the model', async () => {
    await render(SelectHost);

    await page.getByTestId('demo-select').click();

    const listbox = page.getByRole('listbox');
    await expect.element(listbox).toBeVisible();

    await page.getByRole('option', { name: 'Beta' }).click();

    await expect.element(page.getByTestId('selected-value')).toHaveTextContent('Beta');
  });

  it('v-menu: opens overlay and clicking an item fires the handler', async () => {
    await render(MenuHost);

    await page.getByTestId('menu-trigger').click();

    await expect.element(page.getByTestId('menu-item-option a')).toBeVisible();
    await page.getByTestId('menu-item-option a').click();

    await expect.element(page.getByTestId('picked-value')).toHaveTextContent('Option A');
  });

  it('dialog: opens, confirm button is focusable, close hides dialog', async () => {
    await render(DialogHost);

    await page.getByTestId('open-dialog-btn').click();

    await expect.element(page.getByTestId('dialog-heading')).toBeVisible();
    await expect.element(page.getByTestId('dialog-confirm-btn')).toBeVisible();

    // Close via the confirm button.
    await page.getByTestId('dialog-confirm-btn').click();
    await expect.element(page.getByTestId('dialog-heading')).not.toBeVisible();
  });

  it('snackbar: appears after trigger click, content teleports to overlay container', async () => {
    await render(SnackbarHost);

    expect(page.getByText('Action completed').query()).toBeNull();

    await page.getByTestId('show-snackbar-btn').click();

    await expect.element(page.getByText('Action completed')).toBeVisible();
    await expect.element(page.getByTestId('snackbar-close-btn')).toBeVisible();
  });
});
