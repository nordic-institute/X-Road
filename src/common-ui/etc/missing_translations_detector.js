/**
Helps to find missing translations. Usage for Firefox:
  * Open scratchpad (shift + F4)
  * Either copy content of this file into scratchpad or open this file (using 'Open file...')
  * Run this script (ctrl + R)
*/
$('.translation_missing').css({ color: 'red', fontWeight: 700 }).each(function() {
  if($(this).parent().is('button'))
    $(this).parent().attr('title', $(this).attr('title').replace('translation missing: ', '')).tooltip();
  else
    $('<h2/>').css('font-size', '10px').text( $(this).attr('title').replace('translation missing: ', '')).insertAfter($(this));
});
