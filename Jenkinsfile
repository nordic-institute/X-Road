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
                }
            }
            steps {
                sh 'cd src && ./update_ruby_dependencies.sh'
                sh 'cd src && ./compile_code.sh'
            }
        }
        stage('Trusty build') {
            agent {
                dockerfile {
                    dir 'src/packages/docker/deb-trusty'
                    args '-v /etc/passwd:/etc/passwd:ro -v /etc/group:/etc/group:ro -e HOME=/tmp'
                }
            }
            steps {
                script {
                    sh './src/packages/build-deb.sh trusty'
                }
            }
        }
        stage('Bionic build') {
            agent {
                dockerfile {
                    dir 'src/packages/docker/deb-bionic'
                    args '-v /etc/passwd:/etc/passwd:ro -v /etc/group:/etc/group:ro -e HOME=/tmp'
                }
            }
            steps {
                script {
                    sh './src/packages/build-deb.sh bionic'
                }
            }
        }
        stage('RedHat build') {
            agent {
                dockerfile {
                    dir 'src/packages/docker/rpm'
                    args '-v /etc/passwd:/etc/passwd:ro -v /etc/group:/etc/group:ro -e HOME=/workspace/src/packages'
                }
            }
            steps {
                script {
                    sh './src/packages/build-rpm.sh'
                }
            }
        }
    }
}
