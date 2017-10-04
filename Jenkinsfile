pipeline {
    agent any
    stages {
        stage("SCM") {
          steps {
            echo 'SCM'
            checkout scm
          }
        }
        stage('Compile Code') {
            agent {
                dockerfile {
                    dir 'src/packages/docker-compile'
                    additionalBuildArgs '--build-arg uid=$(id -u) --build-arg gid=$(id -g)'
                    args '-itv cache-gradle:/mnt/gradle-cache -v cache-rvm:/home/builder/.rvm'
                    reuseNode true
                }
            }
            steps {
                sh 'cd src && ./update_ruby_dependencies.sh'
                sh 'cd src && ./compile_code.sh'
            }
        }
        stage('Debian build') {
            agent {
                dockerfile {
                    dir 'src/packages/docker-debbuild'
                    args '-v /etc/passwd:/etc/passwd:ro -v /etc/group:/etc/group:ro -e HOME=/tmp'
                    reuseNode true
                }
            }
            steps {
                sh './src/deb-docker.sh'
            }
        }
        stage('RedHat build') {
            agent {
                dockerfile {
                    dir 'src/packages/docker-rpmbuild'
                    args '-v /etc/passwd:/etc/passwd:ro -v /etc/group:/etc/group:ro -e HOME=/workspace/src/packages'
                    reuseNode true
                }
            }
            steps {
                sh './src/rpm-docker.sh'
            }
        }
    }
}
