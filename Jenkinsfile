node {
    stage("SCM") {
        echo 'SCM'
        checkout scm
    }
    stage("Build") {
        echo 'Build stage'
        sh 'git clean -Xf -d'
        sh 'export GRADLE_OPTS="-Dorg.gradle.daemon=false"'
        sh 'cd src && ./update_ruby_dependencies.sh'
        sh 'cd src && ./build_packages.sh || exit 1'
    }
}
        
