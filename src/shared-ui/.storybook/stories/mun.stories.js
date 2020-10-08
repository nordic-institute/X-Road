// Utilities
// A helper function to faciliate the generation of stories

import { storyFactory } from '../util/helpers'
import { text, boolean } from '@storybook/addon-knobs'

// Components
import { AnotherComponent } from '../../src/components/regular/MyButton'

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

/*
export const withAnotherComponent = () => story({
  template: `
    <my-component>
      <another-component></another-component>
    </my-component>
  `,
}) */