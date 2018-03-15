#!groovy

/*
 * This work is protected under copyright law in the Kingdom of
 * The Netherlands. The rules of the Berne Convention for the
 * Protection of Literary and Artistic Works apply.
 * Digital Me B.V. is the copyright owner.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import groovy.transform.Field
import org.jenkins.ci.lazy.Logger

@Field private logger = new Logger(this)

// Function to prepare and list shell scripts (copy from lib to workspace if needed)
def listScripts(stage, scripts, target) {
	logger.debug("listScripts/${stage}/${target}", 'Retrieving config')
	def config = lazyConfig()

	def scriptList = []

	logger.debug("listScripts/${stage}/${target}", 'Enter sub-folder where Dockerfiles and scripts are located')
	dir("${config.sdir}/${stage}") {
		scripts.each { script ->
			def dstScript = "${script}"	// Default main script location
			logger.debug("listScripts/${stage}/${target}", 'Lookup fo the relevant main script in sub workspace first')
			// TODO: Rework to use fileExists?
			def srcScript = sh(
				returnStdout: true,
				script: "ls -1 ${target}.${script} 2> /dev/null || ls -1 ${script} 2> /dev/null || echo"
			).trim()

			if (srcScript != null && srcScript != '') {
				logger.debug("listScripts/${stage}/${target}", 'Use script from workspace since existing')
				dstScript = srcScript
			} else {
				logger.debug("listScripts/${stage}/${target}", 'Extract main script from shared lib')
				def contentscript = ''
				try {
					contentscript = libraryResource("${config.sdir}/${stage}/${target}.${script}")
				} catch (hudson.AbortException e) {
					contentscript = libraryResource("${config.sdir}/${stage}/${script}")
				}

				logger.debug("listScripts/${stage}/${target}", 'Write the selected Dockerfile to workspace sub-folder')
				writeFile(
					file: dstScript,
					text: contentscript
				)

				logger.trace("listScripts/${stage}/${target}", "Change mode of ${dstScript} to allow execution")
				sh "chmod +x ${dstScript}"
			}

			logger.debug("listScripts/${stage}/${target}", 'Add the path to the script in the final list to be returned')
			logger.debug("listScripts/${stage}/${target}", "Script list content is now = ${scriptList.toString()}")
			scriptList += dstScript
		}
	}

	logger.trace('listScripts', "Found ${scriptList.size()} shell tasks = ${scriptList}")
	
	return scriptList
}

// Parse task as Closure of (list of) String and return a List of step(s)
def call (stage, task, target) {
	logger.debug("${stage}/${target}", 'Retrieving config')
	def config = lazyConfig()

	logger.debug("${stage}/${target}", 'Collect steps to be resolved')
	def steps = []
	if (task instanceof Closure) {
		logger.trace("${stage}/${target}", "Task is a Closure = ${task.toString()}")
		// If task is a Closure, just add it in the step list
		steps += task
	} else {
		logger.debug("${stage}/${target}", 'Prepare shell scripts from (list of) String')
		def scripts = []
		if (task instanceof List) {
			logger.trace("${stage}/${target}", "Task is a List (${task.toString()})")
			scripts = listScripts(stage, task, target)
		} else if (task instanceof String) {
			logger.trace("${stage}/${target}", "Task is a String (${task.toString()})")
			scripts = listScripts(stage, [ task ], target)
		} else {
			logger.error("${stage}/${target}", 'Give up if not a Closure, not a List and not a String!')
			def err = "${stage}/${target} No idea what to do with task = ${task.toString()}"
			logger.fatal(err)
			error err.toString()
		}

		// Collect all scripts as shell steps
		logger.trace("${stage}/${target}", "Task was referring to {scripts.size()} scripts (${scripts})")
		scripts.each { script ->
			steps += { sh "${config.sdir}/${stage}/${script}" }
		}
	}
	logger.trace("${stage}/${target}", "Task has been converted in {steps.size()} steps (${steps.toString()})")

	logger.debug("${stage}/${target}", 'Return list of resolved steps')
	return steps
}
