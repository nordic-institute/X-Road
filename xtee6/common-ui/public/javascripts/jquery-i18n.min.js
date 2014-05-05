/*
 * jQuery i18n plugin
 * @requires jQuery v1.1 or later
 *
 * See http://recursive-design.com/projects/jquery-i18n/
 *
 * Licensed under the MIT license:
 *   http://www.opensource.org/licenses/mit-license.php
 *
 * Version: 1.0.0 (201210141329)
 */
(function(f){f.i18n={dict:null,setDictionary:function(a){this.dict=a},_:function(a,d){var e=a;if(this.dict&&this.dict[a])e=this.dict[a];return this.printf(e,d)},printf:function(a,d){if(!d)return a;for(var e="",c=/%(\d+)\$s/g,b=c.exec(a);b;){var g=parseInt(b[1],10)-1;a=a.replace("%"+b[1]+"$s",d[g]);b=c.exec(a)}c=a.split("%s");if(c.length>1)for(b=0;b<d.length;b++){if(c[b].length>0&&c[b].lastIndexOf("%")==c[b].length-1)c[b]+="s"+c.splice(b+1,1)[0];e+=c[b]+d[b]}return e+c[c.length-1]}};f.fn._t=function(a,
d){return f(this).text(f.i18n._(a,d))}})(jQuery);
