pipeline {
    agent any
    environment {
        DOCKER_GID = """${sh(returnStdout: true, script: 'getent group docker | cut -d: -f3')}""".trim()
    }
    stages {
        stage('Output build parameters') {
            steps {
                sh 'env'
                echo '---------------'
                echo "${currentBuild.changeSets}"
                echo '--------------'
            }
        }        
        stage('Clean and clone repository') {
            steps {
                checkout([
                        $class                           : 'GitSCM',
                        branches                         : [[name: ghprbSourceBranch]],
                        doGenerateSubmoduleConfigurations: false,
                        extensions                       : [[$class: 'CleanBeforeCheckout'],
                                                            [$class: 'ChangelogToBranch', options: [compareRemote: 'origin', compareTarget: '${ghprbTargetBranch}']],
                                                           ],
                        gitTool                          : 'Default',
                        submoduleCfg                     : [],
                        userRemoteConfigs                : [
                            [
                                url: 'https://github.com/nordic-institute/X-Road.git',
                                refspec: '+refs/heads/*:refs/remotes/origin/* +refs/pull/*/head:refs/remotes/origin/pull/*'
                            ]
                        ]
                ])
                echo '--------------'
                echo "${currentBuild.changeSets}"
                echo '-------------'
                script {
                    showChangeLogs(currentBuild.changeSets)
                }
                echo '--------------'
            }
        }
        stage('Compile Code') {
            when {
                anyOf {
                    changeset "src/**"
//                    changeset "Jenkinsfile"
                }
            }
            agent {
                dockerfile {
                    dir 'src/packages/docker-jenkins-compile'
                    additionalBuildArgs  '--build-arg JENKINSUID=`id -u jenkins` --build-arg JENKINSGID=`id -g jenkins` --build-arg DOCKERGID=${DOCKER_GID}'
                    args '-v /var/run/docker.sock:/var/run/docker.sock --group-add ${DOCKER_GID} --add-host=host.docker.internal:host-gateway'
                    reuseNode true
                }
            }
            environment {
                GRADLE_OPTS = '-Dorg.gradle.daemon=false -Dsonar.host.url=https://sonarqube.niis.org'
                JAVA_HOME = '/usr/lib/jvm/java-11-openjdk-amd64'
            }
            steps {
                withCredentials([string(credentialsId: 'sonarqube-user-token-2', variable: 'SONAR_TOKEN')]) {
                    sh 'cd src && ./gradlew -Dsonar.login=${SONAR_TOKEN} -Dsonar.pullrequest.key=${ghprbPullId} -Dsonar.pullrequest.branch=${ghprbSourceBranch} -Dsonar.pullrequest.base=${ghprbTargetBranch} --stacktrace --no-daemon build runProxyTest runMetaserviceTest runProxymonitorMetaserviceTest jacocoTestReport dependencyCheckAggregate sonarqube -Pfrontend-unit-tests -Pfrontend-npm-audit -PintTestProfilesInclude="ci"'
                }
            }
        }
        stage('Ubuntu focal packaging') {
            when {
                beforeAgent true
                expression { return fileExists('src/packages/docker/deb-focal/Dockerfile') }
                anyOf {
                    changeset "src/**"
                    changeset "Jenkinsfile"
                }
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
                anyOf {
                    changeset "src/**"
                    changeset "Jenkinsfile"
                }
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
            when {
                anyOf {
                    changeset "src/**"
                    changeset "Jenkinsfile"
                }
            }
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
            when {
                anyOf {
                    changeset "src/**"
                    changeset "Jenkinsfile"
                }
            }
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

@NonCPS
// showChangeLogs(currentBuild.changeSets)
def showChangeLogs(changeLogSets) {
    for (int i = 0; i < changeLogSets.size(); i++) {
        def entries = changeLogSets[i].items
        for (int j = 0; j < entries.length; j++) {
            def entry = entries[j]
            echo "${entry.commitId} by ${entry.author} on ${new Date(entry.timestamp)}: ${entry.msg}"
//            def files = new ArrayList(entry.affectedFiles)
//            for (int k = 0; k < files.size(); k++) {
//                def file = files[k]
//                echo "${file.editType.name} ${file.path}"
//            }
        }
    }
}
