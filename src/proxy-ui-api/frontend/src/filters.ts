import Vue from 'vue';

Vue.filter('capitalize', (value: string) => {
  if (!value) { return ''; }
  value = value.toString();
  return value.charAt(0).toUpperCase() + value.slice(1);
});


// Add colon for every two characters.  xxxxxx -> xx:xx:xx
Vue.filter('colonize', (value: string) => {
  if (!value) { return ''; }

  const colonized = value.replace(/(.{2})/g, '$1:');

  if (colonized[colonized.length - 1] === ':') {
    return colonized.slice(0, -1);
  }

  return colonized;
});

// Upper case every word
Vue.filter('upperCaseWords', (value: string) => {

  if (!value) { return ''; }
  return value
    .toLowerCase()
    .split(' ')
    .map((s) => s.charAt(0).toUpperCase() + s.substring(1))
    .join(' ');
});


