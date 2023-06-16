import type { Meta, StoryObj } from '@storybook/vue3';

import XrdComponent from './XrdComponent.vue';

// More on how to set up stories at: https://storybook.js.org/docs/vue/writing-stories/introduction
const meta = {
  title: 'XrdComponent',
  component: XrdComponent,
  // This component will have an automatically generated docsPage entry: https://storybook.js.org/docs/vue/writing-docs/autodocs
  tags: ['autodocs'],
  argTypes: {
    text: { control: 'text' },
    onPress: { action: 'pressed' },
  },
  args: { text: 'default text' }, // default value
} satisfies Meta<typeof XrdComponent>;

export default meta;
type Story = StoryObj<typeof meta>;
/*
 *👇 Render functions are a framework specific feature to allow you control on how the component renders.
 * See https://storybook.js.org/docs/vue/api/csf
 * to learn how to use render functions.
 */
export const Primary: Story = {
  args: {
    text: "primary"
  },
};

export const Secondary: Story = {
  args: {
    text: "Secondary"
  },
};

