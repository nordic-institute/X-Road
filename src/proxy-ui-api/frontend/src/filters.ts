import Vue from 'vue';

Vue.filter('capitalize', (value: string): string => {
  if (!value) { return ''; }
  value = value.toString();
  return value.charAt(0).toUpperCase() + value.slice(1);
});


// Add colon for every two characters.  xxxxxx -> xx:xx:xx
Vue.filter('colonize', (value: string): string => {
  if (!value) { return ''; }

  const colonized = value.replace(/(.{2})/g, '$1:');

  if (colonized[colonized.length - 1] === ':') {
    return colonized.slice(0, -1);
  }

  return colonized;
});

// Upper case every word
Vue.filter('upperCaseWords', (value: string): string => {

  if (!value) { return ''; }
  return value
    .toLowerCase()
    .split(' ')
    .map((s) => s.charAt(0).toUpperCase() + s.substring(1))
    .join(' ');
});

// Format date string. Result YYYY-MM-DD.
Vue.filter('formatDate', (value: string): string => {
  const date = new Date(value);
  return date.toISOString().substring(0, 10);
});

// Format date string. Result YYYY-MM-DD HH:MM.
Vue.filter('formatDateTime', (value: string): string => {
  const date = new Date(value);
  return date.toISOString().substring(0, 10) + ' ' + date.toISOString().substring(11, 16);
});

