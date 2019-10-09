pipeline {
    agent any
    stages {
        stage('Output build parameters') {
            steps {
                sh 'env'
            }
        }        
        stage("SCM") {
          steps {
            checkout scm
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
            steps {
                sh 'cd src && ./update_ruby_dependencies.sh'
                withCredentials([string(credentialsId: 'sonarqube-user-token-2', variable: 'SONAR_TOKEN')]) {
                    sh 'cd src && ~/.rvm/bin/rvm jruby-$(cat .jruby-version) do ./gradlew -Dsonar.login=${SONAR_TOKEN} -Dsonar.pullrequest.key=${ghprbPullId} -Dsonar.pullrequest.branch=${ghprbSourceBranch} -Dsonar.pullrequest.base=${ghprbTargetBranch} --stacktrace --no-daemon buildAll runProxyTest runMetaserviceTest runProxymonitorMetaserviceTest jacocoTestReport dependencyCheckAggregate sonarqube'
                }
            }
        }
        stage('Bionic build') {
            agent {
                dockerfile {
                    dir 'src/packages/docker/deb-bionic'
                    args '-v /etc/passwd:/etc/passwd:ro -v /etc/group:/etc/group:ro -e HOME=/tmp'
                    reuseNode true
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
