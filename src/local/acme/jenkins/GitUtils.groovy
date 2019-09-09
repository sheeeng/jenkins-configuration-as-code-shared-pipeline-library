package local.acme.jenkins;

import com.cloudbees.groovy.cps.NonCPS;

class GitUtils {
	/**
	Adapted from https://wiki.jenkins.io/display/JENKINS/Git+Tag+Message+Plugin
	*/
	static String getCommitId(jenkins, String ref = null, String repoPath = '.') {
		ref = ref ?: 'HEAD'
		String commitId = null

		jenkins.dir(repoPath) {
			commitId = jenkins.sh(
				script: "git rev-parse ${ref}",
				returnStdout: true
			).trim()
		}
		return commitId
	}

	/**
	Adapted from https://wiki.jenkins.io/display/JENKINS/Git+Tag+Message+Plugin
	*/
	static String getShortCommitId(jenkins, String ref = null, String repoPath = '.') {
		ref = ref ?: 'HEAD'
		String commitId = null

		jenkins.dir(repoPath) {
			commitId = jenkins.sh(
				script: "git rev-parse --short ${ref}",
				returnStdout: true
			).trim()
		}
		return commitId
	}

	/**
	Stolen from: https://wiki.jenkins.io/display/JENKINS/Git+Tag+Message+Plugin
	@return A list of tag's names that point to `commitId`.
	*/
	static String gitTagsOnCommit(jenkins, String commitId) {
		String tags = jenkins.sh(
			script: "git tag --points-at ${commitId}",
			returnStdout: true
		).trim()
		return tags.split('\n')
	}

	/**
	Adapted from https://wiki.jenkins.io/display/JENKINS/Git+Tag+Message+Plugin
	@return The tag message, or `null` if the current commit isn't a tag.
	*/
	static String gitTagMessage(jenkins, String tag) {
		String message = jenkins.sh(
			script: """
				git for-each-ref \
				--format="%(content:body)" \
				refs/tags/${tag}
			""".trim().stripIndent(),
			returnStdout: true
		).trim()
		return message
	}

	static String gitTagType(jenkins, String tag) {
		String tagTypestring = jenkins.sh(
			script: """
				git for-each-ref \
				refs/tags/${tag}
			""".trim().stripIndent(),
			returnStdout: true
		).trim()
		assert tagTypestring.split('\n').size() <= 1
		return tagTypestring
	}

	@NonCPS
	static boolean isAnnotatedTag(String tagTypestring, String tagName) {
		assert tagTypestring.split('\n').size() == 1

		// Regexp for "anything once or more than once"
		tagName = tagName ?: '.+'
		return tagTypestring ==~ """
			(?x)			# Multi-line regexp flag
			^				# Start of the string/line
			[a-z0-9]{40}	# The tag's sha1
			\\s+			# Whitespace (often tab or space(s))
			tag				# That it's actually an annotated tag
			\\s+			# The tag's sha1
			refs/tags/		# That it's an annotated tag we're after
			${tagName}		# The name of the tag
			\$				# End of the input string/line
		""".trim().stripIndent()
	}
}
