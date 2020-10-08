// Button.stories.js

import Button from "./DummyButton.vue";

export default { title: "Components/Button" };

export const Primary = () => ({
  components: { Button },
  template: '<Button primary label="Button" />'
});

Primary.storyName = "I am the primary";
