import HelpIcon from './HelpIcon.vue';

export default {
  title: 'X-Road/Help icon',
  component: HelpIcon,
  argTypes: {
    text: { control: 'text' },
  },
};

const Template = (args, { argTypes }) => ({
  props: Object.keys(argTypes),
  components: { HelpIcon },
  template:
    '<help-icon v-bind="$props" />',
});

export const Primary = Template.bind({});
Primary.args = {
  primary: true,
  text: 'Hello world!',
};

export const LongText = Template.bind({});
LongText.args = {
  text: `This is a very, very long text: Lorem ipsum dolor sit amet, 
  consectetur adipiscing elit. Nam porta vehicula dolor in molestie. 
  Praesent sem sem, pretium ut massa sit amet, ultrices volutpat elit. 
  Ut ac arcu at libero vestibulum condimentum ut sed mi. Nullam egestas 
  eu risus at sollicitudin. Sed eleifend pretium eleifend. Vivamus 
  aliquet quam et gravida iaculis. Duis quis gravida nibh, vel faucibus felis.`,
};
