package local.acme.jenkins

import groovy.json.JsonSlurper

class Nexus {
	protected static final String LUCENE_SEARCH_URL = 'http://localhost:8081/nexus/service/local/lucene/search'
	protected static final String NEXUS2_SEARCH = "curl --silent --header 'Accept: application/json' --location '${LUCENE_SEARCH_URL}"

	protected static final String JAVA_INSTALL_REGISTRY = 'incomplete'
	protected static final String JAVA_PUBLISH_REGISTRY = 'incomplete'

	protected static final def VALID_JAVA_EXTENSIONS = ['jar', 'war']

	static def getExistingJavaVersionsFromNexus(jenkins, mvnCoords) {
		// Beware typing wrongly `returnStdout` as `returnStdOut`, which return null object.
		def versionsFromNexus = jenkins.sh(
			returnStdout: true,
			script: """
				${NEXUS2_SEARCH}?g=${mvnCoords.groupId}&a=${mvnCoords.artifactId}' \
				| jq -r '..|select(has("version"))?|.version' | sort -gr
			"""
		).split("\r?\n")

		return versionsFromNexus
	}

	static boolean doesJavaArtifactCommitExistOnNexus(jenkins, mvnCoords, commitSHA1) {
		def versionsFromNexus = getExistingJavaVersionsFromNexus(jenkins, mvnCoords)

		if (versionsFromNexus.join(',').contains(commitSHA1)) {
			return true
		}
		return false
	}

	static def getLatestJavaVersionFromNexus(jenkins, mvnCoords, commitSHA1) {
		def versionsFromNexus = getExistingJavaVersionsFromNexus(jenkins, mvnCoords)

		def matchedCommits = versionsFromNexus.findAll { a ->
			[commitSHA1].any { a.contains(it) }
		}

		jenkins.echo "Artifact(s) with same commit found:\n${matchedCommits}"

		assert(matchedCommits.size() > 0)

		// Return only one last updated (most recent) item as one or more matched commits may exist.
		return matchedCommits.first()
	}

	// Check metadata of latest artifact matches given maven coordinates.
	static def getMavenCoordsOfCommitOnNexus(jenkins, mvnCoords, matchedCommit) {
		// Beware typing wrongly `returnStdout` as `returnStdOut`, which return null object.
		def mavenCoordsFromRecentCommit = jenkins.sh(
			returnStdout: true,
			script: """
				${NEXUS2_SEARCH}?g=${mvnCoords.groupId}&a=${mvnCoords.artifactId}&v=${matchedCommit}' \
				| jq -r '..|select(.version =="${matchedCommit}")?'
			"""
		).trim()

		JsonSlurper jsonSlurper = new JsonSlurper()
		def parsedMavenCoords = jsonSlurper.parseText(mavenCoordsFromRecentCommit)

		def listArtifactsLinks = parsedMavenCoords.artifactHits.artifactLinks.flatten()

		def filteredArtifactsLinks = listArtifactsLinks.findAll {
			VALID_JAVA_EXTENSIONS.contains(it['extension']) && it['classifier'] == mvnCoords.classifier
		}.collect { it }

		def parsedClassifier, parsedExtension

		// Priority 1: Use valid executable if both {classifier, extension} keys are matched.
		// Get map that fits given set of keys.
		def filteredKeysArtifactsLinks = filteredArtifactsLinks.findAll {
			it.containsKey('classifier') && it.containsKey('extension')
		}.collect { it }

		// Check if map containing both {classifier, extension} keys altogether exist.
		if (filteredKeysArtifactsLinks.size() > 0) {
			// Priority 1: Use valid executable if both {classifier, extension} keys are matched.
			parsedClassifier = filteredKeysArtifactsLinks['classifier'].first()
			parsedExtension = filteredKeysArtifactsLinks['extension'].first()
		} else {
			// Priority 2: Use valid executable if only {extension} key are matched.
			// Use default value from maven coordinates when classifier not found.
			parsedClassifier = mvnCoords.classifier
			// Remove null items using `- null` notation. https://stackoverflow.com/a/5270663
			// Return only one last updated (most recent) item as one or more matched commits may exist.
			parsedExtension = (filteredArtifactsLinks
				.findAll {
					it.containsKey('extension')
				}.collect{ it }.extension.unique() - null).first()
		}

		def mvnData = [
				groupId    : parsedMavenCoords.groupId,
				artifactId : parsedMavenCoords.artifactId,
				version    : parsedMavenCoords.version,
				classifier : parsedClassifier,
				packaging  : parsedExtension
		]

		return mvnData
	}

	static def deployMaven(jenkins, buildData) {
		String deployCmd = 'mvn'
		deployCmd += ' --settings /var/jenkins_home/.m2/settings.xml'
		deployCmd += ' -U clean install -B'
		// Deploy deployable
		jenkins.dir(new File(buildData.applicationFile).parent) {
			jenkins.sh 'mvn deploy -B'
		}

		String deployArgs = "deploy-jar"
		deployArgs += " -a ${buildData.module.artifactId}"
		deployArgs += " -g ${buildData.module.groupId}"
		deployArgs += " -v ${buildData.module.version}"
		deployArgs += " -p ${buildData.module.packaging}"
		deployArgs += " -c ${buildData.module.classifier}"
		jenkins.echo "Deploy arguments: '${deployArgs}'"

		return [deployArgs: deployArgs]
	}
}
