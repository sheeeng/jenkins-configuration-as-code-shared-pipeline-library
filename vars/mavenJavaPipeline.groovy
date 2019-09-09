/*
[Java] Class JsonOutput
http://docs.groovy-lang.org/latest/html/gapi/groovy/json/JsonOutput.html
Class responsible for the actual String serialization of the possible values of a JSON structure.
*/

import static groovy.json.JsonOutput.prettyPrint
import static groovy.json.JsonOutput.toJson
import static local.acme.jenkins.GitUtils.gitTagType
import static local.acme.jenkins.GitUtils.isAnnotatedTag
import static local.acme.jenkins.JenkinsBuild.buildMvn


@groovy.transform.Field // https://jenkins.io/doc/book/pipeline/shared-libraries/#defining-global-variables
def checkedOutScm;

@groovy.transform.Field // https://jenkins.io/doc/book/pipeline/shared-libraries/#defining-global-variables
String defaultPipelineDockerImage = 'library/maven:3.6';


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
            timeout(time: 1, unit: 'DAYS')
            timestamps()
        }
        stages {
            stage('Build') {
                agent any
                steps {
                    deleteDir()
                    script {
                        // https://jenkins.io/doc/pipeline/steps/workflow-scm-step/
                        checkedOutScm = checkout scm
                        if (_buildData == null) {
                            _buildData = [:]
                        }
                        _buildData.put('_scmData', checkedOutScm)
                        _buildData.put('type', 'mvn-app')

                        // Check if pipeline run/execution is based on git tag push or git branch push
                        _buildData.put(
                            '_isAnnotatedGitTag',
                            isAnnotatedTag(
                            gitTagType(this, _buildData._scmData.GIT_BRANCH),
                            _buildData._scmData.GIT_BRANCH
                            )
                        )
                        /*
                            Object get(Object key, Object defaultValue)
                            http://docs.groovy-lang.org/latest/html/groovy-jdk/java/util/Map.html#get(java.lang.Object,%20java.lang.Object)
                            Looks up an item in a Map for the given key and returns the value,
                            unless there is no entry for the given key,
                            in which case add the default value to the map and return with the added value.
                        */
                        _buildData.get('applicationFile', './pom.xml')
                        _buildData.get('useSnapshotVersioning', false)
                        withCredentials([
                            usernamePassword(
                                credentialsId: 'NEXUS_USER',
                                passwordVariable: 'NEXUS_USERPASSWORD',
                                usernameVariable: 'NEXUS_USERNAME'
                            )]
                        ){
                            // Maven installation declared in the Jenkins "Global Tool Configuration".
                            withMaven(maven: 'maven-3') {
                                // Fetch deployArgs from build-step
                                _buildData.put("_module", buildMvn(this, _buildData))
                            }
                        }

                        archiveArtifacts "target/**/*.jar, target/**/*.war"

                        echo "BuildData:\n${prettyPrint(toJson(_buildData))}"
                    }
                }
            }
            stage('Test') {
                agent any
                steps {
                    deleteDir()
                    checkout scm
                    script {
                        String testCmd = 'mvn'
                        testCmd += ' --also-make'
                        testCmd += ' --batch-mode'
                        testCmd += ' --define maven.test.skip=false'
                        testCmd += ' --define skipTests=false'
                        testCmd += ' --define source.skip=false'
                        testCmd += ' --define failIfNoTests=false'
                        testCmd += ' --quiet'
                        testCmd += ' --settings /var/jenkins_home/.m2/settings.xml'
                        testCmd += ' --update-snapshots'
                        testCmd += ' clean verify'
                        testCmd += " --projects :${_buildData._module.artifactId}"

                        // Maven installation declared in the Jenkins "Global Tool Configuration".
                        withMaven(maven: 'maven-3') {
                            sh testCmd
                        }
                        echo "BuildData:\n${prettyPrint(toJson(_buildData))}"
                    }
                }
            }
            stage('Artifact') {
                agent any
                steps {
                    deleteDir()
                    checkout scm
                    script {
                        String deployCmd = 'mvn'
                        deployCmd += ' --batch-mode --update-snapshots clean deploy'
                        deployCmd += ' --settings /var/jenkins_home/.m2/settings.xml'
                        deployCmd += ' --define maven.javadoc.skip=true'
                        deployCmd += ' --define failIfNoTests=false'
                        deployCmd += ' --define source.skip=true'
                        deployCmd += ' --define skipITs=false'
                        deployCmd += ' --define test=none'

                        withCredentials([
                            usernamePassword(
                                credentialsId: 'NEXUS_USER',
                                passwordVariable: 'NEXUS_USERPASSWORD',
                                usernameVariable: 'NEXUS_USERNAME'
                            )]
                        ){
                            // Maven installation declared in the Jenkins "Global Tool Configuration".
                            withMaven(maven: 'maven-3') {
                                sh deployCmd
                            }
                        }
                        echo "BuildData:\n${prettyPrint(toJson(_buildData))}"
                    }
                }
            }
        }
        post {
            always {
                script {
                    echo "BuildData:\n${prettyPrint(toJson(_buildData))}"
                }
            }
        }
    }
}
