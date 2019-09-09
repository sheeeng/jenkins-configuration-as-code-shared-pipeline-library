package local.acme.jenkins

import groovy.xml.XmlUtil

import static groovy.json.JsonOutput.toJson
import static groovy.json.JsonOutput.prettyPrint
import static local.acme.jenkins.JenkinsBuild.buildMvn

class JenkinsBuild {

	static def buildMvn(
		def jenkins,
		Map buildData
	) {
		jenkins.echo "Input Jenkinsfile data: ${prettyPrint(toJson(buildData))}"
		Utils.validateBuildData(jenkins, buildData)

		String pomFile = buildData.applicationFile
		def mvnCoords = readEffectivePomAndUpdatePomXml(jenkins, pomFile)
		mvnCoords = jenkins.readMavenPom(file: pomFile)

		// Get new version number
		String commitSHA1 = buildData._scmData.GIT_COMMIT

		// final boolean isContinuouslyVersioned = isContinuouslyVersioned(mvnCoords.getVersion())
		if (!buildData.useSnapshotVersioning /*&& isContinuouslyVersioned*/) {
			String jenkinsRevision = mvnCoords.getVersion() + "-" + abbreviateCommitId(commitSHA1)

			// We want to upload to Nexus a pom.xml file with the new version,
			// so have Maven replace it for us.
			String setVersion = 'mvn versions:set'
			setVersion += ' --batch-mode'
			setVersion += ' --settings /var/jenkins_home/.m2/settings.xml'
			setVersion += " --define newVersion=${jenkinsRevision}"
			setVersion += " --quiet"
			jenkins.sh setVersion
			mvnCoords = jenkins.readMavenPom(file: pomFile)
		}

		if (!(buildData.find{it.key == 'classifier'}?.value)) {
			buildData.put('classifier', 'jetty')
		}

		mvnCoords = [
				groupId: mvnCoords.getGroupId(),
				artifactId: mvnCoords.getArtifactId(),
				version: mvnCoords.getVersion(),
				classifier: buildData.classifier,
				packaging: mvnCoords.getPackaging()
		]

		// TODO: How to handle Nexus 2 or Nexus 3 selection?
		// if (Nexus.doesJavaArtifactCommitExistOnNexus(jenkins, mvnCoords, commitSHA1)) {
		// 	mvnCoords = Nexus.getMavenCoordsOfCommitOnNexus(
		// 		jenkins,
		// 		mvnCoords,
		// 		Nexus.getLatestJavaVersionFromNexus(
		// 			jenkins, mvnCoords, commitSHA1
		// 		)
		// 	)
		// 	jenkins.echo "Same commit found. Re-use existing artifact:\n${mvnCoords}"
		// }

		String compileCmd = 'mvn'
		compileCmd += ' --also-make'
		compileCmd += ' --batch-mode'
		compileCmd += ' --define maven.test.skip=true'
		compileCmd += ' --define skipTests'
		compileCmd += ' --quiet'
		compileCmd += ' --settings /var/jenkins_home/.m2/settings.xml'
		compileCmd += ' --update-snapshots'
		compileCmd += ' clean install'
		compileCmd += " --projects :${mvnCoords.artifactId}"

		jenkins.sh compileCmd

		return mvnCoords
	}

	protected static readEffectivePomAndUpdatePomXml(jenkins, String pomFile) {
		String effectivePomOutput
		jenkins.dir(new File(pomFile).parent) {
            // jenkins.sh("mvn install:install-file -Dfile=${pomFile} -Dpackaging=pom -DpomFile=${pomFile}")

			String scriptCmd = 'mvn --settings /var/jenkins_home/.m2/settings.xml help:effective-pom '
			effectivePomOutput = Utils.extractEffectivePom(
				jenkins.sh(
					returnStdout: true,
					script: scriptCmd
				)
			)
		}

		def parsedEffectivePom = new XmlParser().parseText(effectivePomOutput)
		String niceXML = XmlUtil.serialize(parsedEffectivePom)

		// Write effective-pom contents to pomFile, and re-read mvnCoords variable
		// so as to get the most (and most updated) information possible.
		jenkins.writeFile(
			file: pomFile,
			encoding: 'UTF-8',
			text: niceXML
		)

		return parsedEffectivePom
	}

	static boolean isLibrary(Map buildData){
		return buildData.type.toLowerCase().endsWith('lib')
	}

	static boolean isContinuouslyVersioned(String versionString){
		if (versionString.endsWith('NON-LIBRARY')) {
			return true
		}
		return false
	}

	private static String abbreviateCommitId(String commitId) {
		return "${commitId.substring(0, 8)}"
	}
}
