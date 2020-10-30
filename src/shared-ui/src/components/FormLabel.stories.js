import FormLabel from './FormLabel.vue';

export default {
  title: 'X-Road/Form label',
  component: FormLabel,
  argTypes: {
    labelText: { control: 'text' },
    helpText: { control: 'text' },
  },
};

const Template = (args, { argTypes }) => ({
  props: Object.keys(argTypes),
  components: { FormLabel },
  template:
    '<form-label v-bind="$props" />',
});

export const Primary = Template.bind({});
Primary.args = {
  primary: true,
  labelText: 'This is the label',
  helpText: 'This is a help text',
};

export const LongText = Template.bind({});
LongText.args = {
  labelText: `This is a very, very long label: Lorem ipsum dolor sit amet, 
  consectetur adipiscing elit. Nam porta vehicula dolor in molestie.`,
  helpText: `This is a very, very long text: Lorem ipsum dolor sit amet, 
  consectetur adipiscing elit. Nam porta vehicula dolor in molestie. 
  Praesent sem sem, pretium ut massa sit amet, ultrices volutpat elit. 
  Ut ac arcu at libero vestibulum condimentum ut sed mi. Nullam egestas 
  eu risus at sollicitudin. Sed eleifend pretium eleifend. Vivamus 
  aliquet quam et gravida iaculis. Duis quis gravida nibh, vel faucibus felis.`,
};
