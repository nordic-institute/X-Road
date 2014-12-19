
echo jruby exists?
test -d jruby && exit 0

wget -O jruby.tgz https://s3.amazonaws.com/jruby.org/downloads/1.7.16.1/jruby-bin-1.7.16.1.tar.gz
tar zxf jruby.tgz 
rm jruby.tgz
mv jruby-* jruby
rm -rf `find  jruby/lib/jni/* | grep -v x86_64-Linux`

