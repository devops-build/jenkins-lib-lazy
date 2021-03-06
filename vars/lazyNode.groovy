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

def call(stage, index, task, inLabel = null) {
    logger.debug('Retrieving config')
    def config = lazyConfig()

    def name = inLabel ? "${stage}/${index}/${inLabel}" : "${stage}/${index}/${task.on}"
    logger.debug(name, "Detected tasks to be run on ${task.on}")
    def onLabel = task.on
    if (config.onLabels[task.on]) {
        onLabel = config.onLabels[task.on]
        logger.trace(name, "Mapping found for label ${task.on} = ${onLabel}")
    }

    logger.trace(name, "Preparing to branch on agent with label = ${onLabel}")
    def branch = [
        (name): {
            node(label: onLabel) {
                logger.info('Started')
                logger.trace("Env before = ${env.dump()}")
				def msg = 'no error'
                try {
                    logger.debug('Checkout SCM')
                    checkout scm
                    logger.trace("Env after = ${env.dump()}")
                    ansiColor('xterm') {
						withEnv(["LAZY_LABEL=${inLabel ?: onLabel}"]) {
	                        if (task.pre) {
	                            logger.debug('Execute pre closure first')
	                            logger.trace("Post closure = ${task.pre.toString()}")
	                            task.pre.call()
	                        }
	
	                        logger.trace("Processing inLabel = ${inLabel.toString()}")
	                        if (inLabel) {
	                            logger.debug('Docker required - Calling lazyDocker')
	                            lazyDocker(stage, task.run, inLabel, task.args)
	                        } else {
	                            logger.debug('Docker not required - Calling lazyStep')
	                            lazyStep(stage, task.run, task.on).each { step ->
                                    if (config.timestampsLog) {
                                        logger.debug('Enable timestamps for this step')
                                        timestamps {
                                            step()
                                        }
                                    } else {
                                        step()
                                    }
								}
	                        }
	
	                        if (task.post) {
	                            logger.debug('Execute post closure at the end')
	                            logger.trace("Post closure = ${task.post.toString()}")
	                            task.post.call()
	                        }
						}
                    }
					currentBuild.result = Result.SUCCESS.toString()
                } catch (e) {
					currentBuild.result = Result.FAILURE.toString()
					msg = e.toString()
                    error msg
					throw e
                } finally {
                    if (config.cleanWorkspace) {
                        step([$class: 'WsCleanup'])
                    }
                    if (config.xmppTargets) {
                        jabberNotify(targets: config.xmppTargets, extraMessage: "Stage ${stage} ended - ${msg}")
                    }
                }
                logger.info('Finished')
            }
        }
    ]

    logger.trace(name , "Branch block ready = ${branch.toString()}")
    return branch
}
