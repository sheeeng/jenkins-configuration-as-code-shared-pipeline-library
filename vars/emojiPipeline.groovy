#!/usr/bin/env groovy

import static groovy.json.JsonOutput.prettyPrint
import static groovy.json.JsonOutput.toJson

def call(
    Map _buildData
) {
    pipeline {
        agent none
        options {
            buildDiscarder(logRotator(numToKeepStr: '4'))
            disableConcurrentBuilds()
            disableResume()
            parallelsAlwaysFailFast()
            skipDefaultCheckout()
            timeout(time: 1, unit: 'HOURS')
            timestamps()
        }
        stages {
            stage('🌱') {
                steps {
                    echo "Stage 🌱 was executed."
                }
            }
            stage('🌞') {
                parallel {
                    stage('💖') {
                        steps {
                            echo "Stage 💖 was executed."
                        }
                    }
                    stage('🧡') {
                        steps {
                            echo "Stage 🧡 was executed."
                        }
                    }
                    stage('💛') {
                        steps {
                            echo "Stage 💛 was executed."
                        }
                    }
                    stage('💚') {
                        steps {
                            echo "Stage 💚 was executed."
                        }
                    }
                    stage('💙') {
                        steps {
                            echo "Stage 💙 was executed."
                        }
                    }
                    stage('💜') {
                        steps {
                            echo "Stage 💜 was executed."
                        }
                    }
                    stage('🖤') {
                        steps {
                            echo "Stage 🖤 was executed."
                        }
                    }
                }
            }
            stage('🍂️') {
                steps {
                    echo "Stage 🍂️ was executed."
                }
            }
            stage('❄️️') {
                steps {
                    echo "Stage ❄️️ was executed."
                }
            }
        }
        post {
            always {
                script {
                    echo "BuildData:\n${_buildData.properties.collect{it}.join('\n')}"
                    echo "BuildData:\n${prettyPrint(toJson(_buildData))}"
                    echo "BuildData:\n${_buildData.dump()}"
                }
            }
        }
    }
}
