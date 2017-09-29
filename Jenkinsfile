pipeline {
    agent any
    stages {
        stage("SCM") {
          echo 'SCM'
          checkout scm
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
    }
}
