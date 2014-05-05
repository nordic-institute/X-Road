
echo jruby exists?
test -d jruby && exit 0

wget -O jruby.tgz http://jruby.org.s3.amazonaws.com/downloads/1.7.8/jruby-bin-1.7.8.tar.gz
tar zxf jruby.tgz 
rm jruby.tgz
mv jruby-* jruby
rm -rf `find  jruby/lib/jni/* | grep -v x86_64-Linux`

