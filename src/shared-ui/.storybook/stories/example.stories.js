// Utilities
// A helper function to faciliate the generation of stories
/*
import { storyFactory } from '../util/helpers'
import { text, boolean } from '@storybook/addon-knobs'

// Components
import { AnotherComponent } from '../../src/components/Dummybutton.vue'

// Generate a factory function
// Will automatically bootstrap the story components
const story = storyFactory({
  // Can pass in an import function
  MyComponent: () => import('../../src/components/Dummybutton.vue'),
  // Or explicitly import and use
  AnotherComponent,
})

export const asDefault = () => story({
  template: `<my-component></my-component>`,
})

export const withAnotherComponent = () => story({
  template: `
    <my-component>
      <another-component></another-component>
    </my-component>
  `,
})
*/


/*
import { storyFactory } from '../util/helpers'
import { text, boolean } from '@storybook/addon-knobs'

export default { title: 'BaseCard' }

function genComponent (name) {
  return {
    name,

    render (h) {
      return h('div', this.$slots.default)
    },
  }
}

const story = storyFactory({
  BaseBtn: genComponent('BaseBtn'),
  BaseCard: genComponent('BaseCard'),
})

export const asDefault = () => story({
  props: {
    actions: {
      default: boolean('Actions', false),
    },
    cardText: {
      default: text('Card text', 'Sed augue ipsum, egestas nec, vestibulum et, malesuada adipiscing, dui. Donec sodales sagittis magna. Vestibulum dapibus nunc ac augue. Donec sodales sagittis magna. Duis vel nibh at velit scelerisque suscipit.'),
    },
    divider: {
      default: boolean('Divider', false),
    },
    text: {
      default: boolean('Text', true),
    },
    title: {
      default: boolean('Show title', true),
    },
    titleText: {
      default: text('Title text', 'Card title'),
    },
  },
  template: `
    <base-card>
      <v-card-title v-if="title">{{ titleText }}</v-card-title>

      <v-card-text v-if="text">{{ cardText }}</v-card-text>

      <v-divider v-if="divider"></v-divider>

      <v-card-actions v-if="actions">
        <v-btn text>Cancel</v-btn>

        <v-spacer></v-spacer>

        <base-btn depressed>Accept</base-btn>
      </v-card-actions>
    </base-card>
  `,
})

*/

// Utilities
// A helper function to faciliate the generation of stories

import { storyFactory } from '../util/helpers'
//import { text, boolean } from '@storybook/addon-knobs'
import { withKnobs, text, boolean } from '@storybook/addon-knobs';

export default { title: 'My Card' ,  decorators: [withKnobs],}
// Components
import { AnotherComponent } from '../../src/components/regular/MyButton'

import MyButton from '../../src/components/regular/MyButton.vue';

// Generate a factory function
// Will automatically bootstrap the story components
const story = storyFactory({
  // Can pass in an import function
  MyComponent: () => import('../../src/components/regular/MyButton'),
  // Or explicitly import and use
 // AnotherComponent,
})

export const asDefault = () => story({
  template: `<my-component></my-component>`,
})


// Assign `props` to the story's component, calling
// knob methods within the `default` property of each prop,
// then pass the story's prop data to the component’s prop in
// the template with `v-bind:` or by placing the prop within
// the component’s slot.
export const exampleWithKnobs = () => ({
  components: { MyButton },
  props: {
    isDisabled: {
      default: boolean('Disabled', false),
    },
    text: {
      default: text('Text', 'Hello Storybook'),
    },
  },
  template: `<my-button :isDisabled="isDisabled">{{ text }}</my-button>`,
});

/*
export const withAnotherComponent = () => story({
  template: `
    <my-component>
      <another-component></another-component>
    </my-component>
  `,
}) */
