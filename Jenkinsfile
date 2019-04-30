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
                    reuseNode true
                }
            }
            environment {
                DEBEMAIL = 'info@niis.org'
                DEBFULLNAME = 'NIIS'
            }
            steps {
                script {
                    sh './src/packages/build-deb.sh trusty'
                }
            }
        }
        // stage('Bionic build') {
        //     agent {
        //         dockerfile {
        //             dir 'src/packages/docker/deb-bionic'
        //             args '-v /etc/passwd:/etc/passwd:ro -v /etc/group:/etc/group:ro -e HOME=/tmp'
        //             reuseNode true
        //         }
        //     }
        //     environment {
        //         DEBEMAIL = 'info@niis.org'
        //         DEBFULLNAME = 'NIIS'
        //     }
        //     steps {
        //         script {
        //             if (params.BUILD_RELEASE_PACKAGES) {
        //                 sh './src/packages/build-deb.sh bionic -release'
        //             } else {
        //                 sh './src/packages/build-deb.sh bionic'
        //             }
        //         }
        //     }
        // }
        // stage('RedHat build') {
        //     agent {
        //         dockerfile {
        //             dir 'src/packages/docker/rpm'
        //             args '-v /etc/passwd:/etc/passwd:ro -v /etc/group:/etc/group:ro -e HOME=/workspace/src/packages'
        //             reuseNode true
        //         }
        //     }
        //     steps {
        //         script {
        //             if (params.BUILD_RELEASE_PACKAGES) {
        //                 sh './src/packages/build-rpm.sh -release'
        //             } else {
        //                 sh './src/packages/build-rpm.sh'
        //             }
        //         }
        //     }
        // }


        // stage('Debian build') {
        //     agent {
        //         dockerfile {
        //             dir 'src/packages/docker-debbuild'
        //             args '-v /etc/passwd:/etc/passwd:ro -v /etc/group:/etc/group:ro -e HOME=/tmp'
        //         }
        //     }
        //     steps {
        //         sh './src/deb-docker.sh'
        //     }
        // }
        // stage('RedHat build') {
        //     agent {
        //         dockerfile {
        //             dir 'src/packages/docker-rpmbuild'
        //             args '-v /etc/passwd:/etc/passwd:ro -v /etc/group:/etc/group:ro -e HOME=/workspace/src/packages'
        //         }
        //     }
        //     steps {
        //         sh './src/rpm-docker.sh'
        //     }
        // }
    }
}
