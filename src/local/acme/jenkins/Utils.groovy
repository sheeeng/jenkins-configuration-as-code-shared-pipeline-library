package local.acme.jenkins

import static groovy.json.JsonOutput.prettyPrint
import static groovy.json.JsonOutput.toJson

import com.cloudbees.groovy.cps.NonCPS

class Utils {
	def static requiredBuildDataValues = [
		'_scmData'
	]

	static String extractEffectivePom(String effectivePomString){
		String openingTag = '<project'
		String closingTag = '</project>'
		if (
			effectivePomString == null
			|| effectivePomString.isEmpty()
			|| !(
				effectivePomString.contains(openingTag)
				&& effectivePomString.contains(closingTag)
			)
		) {
			throw new RuntimeException(
				'\n\nERRONEOUS INPUT STRING DOES NOT CONTAIN EXPECTED LINES.\n\n' +
				"Input string:\n-------------------------------------------------\n\n" +
				effectivePomString +
				'-----------------------------------------------------------------\n'
			)
		}

		def startIndex
		def lines = effectivePomString.split("\\r?\\n")
		for (int i = 0; i < lines.size(); i++) {
			if (lines[i].startsWith(openingTag)) {
				startIndex = i
				break
			}
		}

		String xmlLinesToKeep = ''
		for (int i = startIndex; i < lines.size(); i++) {
			if (lines[i] == closingTag) {
				xmlLinesToKeep += lines[i]
				break
			} else {
				xmlLinesToKeep += lines[i] + '\n'
			}
		}

		return xmlLinesToKeep
	}

	static def validatePresence(jenkins, String property, String errorMessage) {
		if (!property || property == null || property.trim().isEmpty()) {
			jenkins.error(errorMessage)
		}
	}

	protected static boolean validateBuildData(def jenkins, Map buildData){
		String msg = "One or more of the following key/value pairs are " +
			"missing from the inputs given to the pipeline-function in your Jenkinsfile!:\n"
		msg += "\n\n${requiredBuildDataValues}\n\n"
		for (key in requiredBuildDataValues) {
			if (!(buildData.find{it.key == key}?.value)) {
				jenkins.error(
					msg + "Jenkinsfile received:\n\n${buildData.dump()}\n\n" + "${prettyPrint(toJson(buildData))}\n\n"
				)
			}
		}
		return true
	}
}
