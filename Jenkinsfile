pipeline {
    agent any
    stages {
        stage('Output build parameters') {
            steps {
                sh 'env'
            }
        }        
        stage('Clean and clone repository') {
            steps {
                checkout([
                        $class                           : 'GitSCM',
                        branches                         : [[name: ghprbSourceBranch]],
                        doGenerateSubmoduleConfigurations: false,
                        extensions                       : [[$class: 'CleanBeforeCheckout']],
                        gitTool                          : 'Default',
                        submoduleCfg                     : [],
                        userRemoteConfigs                : [
                            [
                                url: 'https://github.com/nordic-institute/X-Road.git',
                                refspec: '+refs/heads/*:refs/remotes/origin/* +refs/pull/*/head:refs/remotes/origin/pull/*'
                            ]
                        ]
                ])
            }
        }
        stage('Compile Code') {
            agent {
                dockerfile {
                    dir 'src/packages/docker-compile'
                    additionalBuildArgs '--build-arg uid=$(id -u) --build-arg gid=$(id -g)'
                    reuseNode true
                }
            }
            environment {
                GRADLE_OPTS = '-Dorg.gradle.daemon=false -Dsonar.host.url=https://sonarqube.niis.org'
                JAVA_HOME = '/usr/lib/jvm/java-8-openjdk-amd64/'
            }
            steps {
                sh 'cd src && ./update_ruby_dependencies.sh'
                withCredentials([string(credentialsId: 'sonarqube-user-token-2', variable: 'SONAR_TOKEN')]) {
                    sh 'cd src && ~/.rvm/bin/rvm jruby-$(cat .jruby-version) do ./gradlew -Dsonar.login=${SONAR_TOKEN} -Dsonar.pullrequest.key=${ghprbPullId} -Dsonar.pullrequest.branch=${ghprbSourceBranch} -Dsonar.pullrequest.base=${ghprbTargetBranch} --stacktrace --no-daemon build runProxyTest runMetaserviceTest runProxymonitorMetaserviceTest jacocoTestReport dependencyCheckAggregate sonarqube -Pfrontend-unit-tests -Pfrontend-npm-audit'
                }
            }
        }
        stage('Ubuntu focal packaging') {
            when {
                beforeAgent true
                expression { return fileExists('src/packages/docker/deb-focal/Dockerfile') }
            }
            agent {
                dockerfile {
                    dir 'src/packages/docker/deb-focal'
                    args '-v /etc/passwd:/etc/passwd:ro -v /etc/group:/etc/group:ro -e HOME=/tmp'
                    reuseNode true
                }
            }
            steps {
                script {
                    sh './src/packages/build-deb.sh focal'
                }
            }
        }
        stage('Ubuntu jammy packaging') {
            when {
                beforeAgent true
                expression { return fileExists('src/packages/docker/deb-jammy/Dockerfile') }
            }
            agent {
                dockerfile {
                    dir 'src/packages/docker/deb-jammy'
                    args '-v /etc/passwd:/etc/passwd:ro -v /etc/group:/etc/group:ro -e HOME=/tmp'
                    reuseNode true
                }
            }
            steps {
                script {
                    sh './src/packages/build-deb.sh jammy'
                }
            }
        }
        stage('RHEL 7 packaging') {
            agent {
                dockerfile {
                    dir 'src/packages/docker/rpm'
                    args '-v /etc/passwd:/etc/passwd:ro -v /etc/group:/etc/group:ro -e HOME=/workspace/src/packages'
                    reuseNode true
                }
            }
            steps {
                script {
                    sh './src/packages/build-rpm.sh'
                }
            }
        }
        stage('RHEL 8 packaging') {
            agent {
                dockerfile {
                    dir 'src/packages/docker/rpm-el8'
                    args '-e HOME=/workspace/src/packages'
                    reuseNode true
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
