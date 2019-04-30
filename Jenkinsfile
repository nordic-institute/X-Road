pipeline {
    agent any
    stages {
        stage("SCM") {
          steps {
            echo 'SCM'
            checkout scm
          }
        }
        // stage('Compile Code') {
        //     agent {
        //         dockerfile {
        //             dir 'src/packages/docker-compile'
        //             additionalBuildArgs '--build-arg uid=$(id -u) --build-arg gid=$(id -g)'
        //         }
        //     }
        //     environment {
        //         GRADLE_OPTS = '-Dorg.gradle.daemon=false -Dsonar.host.url=https://sonarqube.niis.org'
        //         JAVA_HOME = '/usr/lib/jvm/java-8-openjdk-amd64/'
        //         BUILD_TYPE = "${params.BUILD_RELEASE_PACKAGES ? 'RELEASE':'SNAPSHOT'}"
        //         BRANCH_NAME= "${params.BRANCH}"
        //     }
        //     steps {
        //         withCredentials([usernamePassword(credentialsId: 'ee55b797-6abc-4b54-87fe-d414a0c0f303', passwordVariable: 'API_KEY', usernameVariable: 'API_USER')]) {
        //             configFileProvider([configFile(fileId:'init_gradle', variable: 'GRADLE_INIT')]) {
        //                 sh '~/.rvm/bin/rvm jruby-$(cat src/.jruby-version) do gem source -r https://rubygems.org/'
        //                 sh '~/.rvm/bin/rvm jruby-$(cat src/.jruby-version) do gem source -a https://jenkins:$API_KEY@artifactory.niis.org/api/gems/rubygems/'
        //                 sh '~/.rvm/bin/rvm jruby-$(cat src/.jruby-version) do bundle config mirror.https://rubygems.org/ https://jenkins:$API_KEY@artifactory.niis.org/api/gems/rubygems/'
        //                 sh 'cd src && ./update_ruby_dependencies.sh'
        //                 script {
        //                     if (params.RUN_SONAR) {
        //                         withCredentials([string(credentialsId: 'sonarqube-user-token-2', variable: 'SONAR_TOKEN')]) {
        //                             sh 'cd src && ~/.rvm/bin/rvm jruby-$(cat .jruby-version) do ./gradlew -PxroadBuildType="$BUILD_TYPE" -Dsonar.login=$SONAR_TOKEN -Papiuser=jenkins -Papikey=$API_KEY -I $GRADLE_INIT --stacktrace buildAll runProxyTest runMetaserviceTest runProxymonitorMetaserviceTest dependencyCheckAnalyze sonarqube'
        //                         }
        //                     } else {
        //                         sh 'cd src && ~/.rvm/bin/rvm jruby-$(cat .jruby-version) do ./gradlew -PxroadBuildType="$BUILD_TYPE" -Papiuser=jenkins -Papikey=$API_KEY -I $GRADLE_INIT --stacktrace buildAll runProxyTest runMetaserviceTest runProxymonitorMetaserviceTest'
        //                     }
        //                 }
        //             }
        //         }
        //     }
        // }


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
        //             reuseNode true        stage("SCM") {

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
