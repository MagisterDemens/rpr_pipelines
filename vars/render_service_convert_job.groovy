def executeConvert(osName, gpuName, Map options) {
	currentBuild.result = 'SUCCESS'
	
	String tool = options['Tool'].split(':')[0].trim()
	String version = options['Tool'].split(':')[1].trim()
	String scene_name = options['sceneName']
   	String scene_user = options['sceneUser']
	String fail_reason = "Unknown"
	
	timeout(time: 65, unit: 'MINUTES') {
		switch(osName) {
			case 'Windows':
				try {
					// Clean up work folder
					bat '''
						@echo off
						del /q *
						for /d %%x in (*) do @rd /s /q "%%x"
					''' 
					// Download render service scripts
					try {
						    print("Downloading scripts and install requirements")
						    checkOutBranchOrScm(options['scripts_branch'], 'git@github.com:luxteam/render_service_scripts.git')
						    dir(".\\install"){
						       bat '''
							  install_pylibs.bat
						       '''
						    }
					} catch(e) {
						currentBuild.result = 'FAILURE'
						print e
						fail_reason = "Downloading scripts failed"
					}
					// download scene, check if it is already downloaded
					try {
					    // initialize directory RenderServiceStorage
					    bat """
						if not exist "..\\..\\RenderServiceStorage" mkdir "..\\..\\RenderServiceStorage"
					    """
					   
						print(python3(".\\render_service_scripts\\send_render_status.py --django_ip \"${options.django_url}/\" --tool \"${tool}\" --status \"Downloading scene\" --id ${id}"))
						def exists = fileExists "..\\..\\RenderServiceStorage\\${scene_user}\\${scene_name}"
						if (exists) {
							print("Scene is copying from Render Service Storage on this PC")
							bat """
								copy "..\\..\\RenderServiceStorage\\${scene_user}\\${scene_name}" "${scene_name}"
							"""
						} else {
							withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'renderServiceCredentials', usernameVariable: 'DJANGO_USER', passwordVariable: 'DJANGO_PASSWORD']]) {
								bat """
									curl -o "${scene_name}" -u %DJANGO_USER%:%DJANGO_PASSWORD% "${options.Scene}"
								"""
							}
							bat """
							    if not exist "..\\..\\RenderServiceStorage\\${scene_user}\\" mkdir "..\\..\\RenderServiceStorage\\${scene_user}"
								copy "${scene_name}" "..\\..\\RenderServiceStorage\\${scene_user}"
								copy "${scene_name}" "..\\..\\RenderServiceStorage\\${scene_user}\\${scene_name}"
							"""
						}
					} catch(e) {
						currentBuild.result = 'FAILURE'
						print e
						fail_reason = "Downloading scene failed"
					}
					
					
					switch(tool) {
						case 'Maya (Redshift)':
							// update redshift 
							bat """
								if not exist "RS2RPRConvertTool" mkdir "RS2RPRConvertTool"
							"""
							dir("RS2RPRConvertTool"){
								checkOutBranchOrScm(options['convert_branch'], 'git@github.com:luxteam/RS2RPRConvertTool.git')
							}
							// copy necessary scripts for render
									bat """
										copy "render_service_scripts\\launch_maya_redshift_conversion.py" "."
										copy "render_service_scripts\\conversion_redshift_render.py" "."
										copy "render_service_scripts\\conversion_rpr_render.py" "."
									"""
							// Launch render
							try {
								print(python3("launch_maya_redshift_conversion.py --tool ${version} --django_ip \"${options.django_url}/\" --id ${id} --build_number ${currentBuild.number} --scene_name \"${scene_name}\" "))
							} catch(e) {
								print e
								currentBuild.result = 'FAILURE'
								// if status == failure then copy full path and send to slack
								bat """
									mkdir "..\\..\\RenderServiceStorage\\failed_${scene_name}_${id}_${currentBuild.number}"
									copy "*" "..\\..\\RenderServiceStorage\\failed_${scene_name}_${id}_${currentBuild.number}"
								"""
							}
							break;
				
					}   
				} catch(e) {
					print e
					print(python3("${CIS_TOOLS}\\${options.cis_tools}\\send_render_results.py --django_ip \"${options.django_url}/\" --build_number ${currentBuild.number} --status ${currentBuild.result} --fail_reason \"${fail_reason}\" --id ${id}"))
				} 
			  	break;
		}
    }
}


def main(String PCs, Map options) {
	


		timestamps {
			String PRJ_PATH="${options.PRJ_ROOT}/${options.PRJ_NAME}"
			String JOB_PATH="${PRJ_PATH}/${JOB_NAME}/Build-${BUILD_ID}".replace('%2F', '_')
			options['PRJ_PATH']="${PRJ_PATH}"
			options['JOB_PATH']="${JOB_PATH}"

			boolean PRODUCTION = true

			if (PRODUCTION) {
				options['django_url'] = "https://render.cis.luxoft.com/convert/jenkins/"
				options['plugin_storage'] = "https://render.cis.luxoft.com/media/plugins/"
				options['scripts_branch'] = "master"
				options['convert_branch'] = "master"
			} else {
				options['django_url'] = "https://testrender.cis.luxoft.com/convert/jenkins/"
				options['plugin_storage'] = "https://testrender.cis.luxoft.com/media/plugins/"
				options['scripts_branch'] = "master"
				options['convert_branch'] = "master"
			}

			def testTasks = [:]
			List tokens = PCs.tokenize(':')
			String osName = tokens.get(0)
			String deviceName = tokens.get(1)

			String renderDevice = ""
		        if (deviceName == "ANY") {
				String tool = options['Tool'].split(':')[0].trim()
				renderDevice = tool
		        } else {
				renderDevice = "gpu${deviceName}"
	    		}
		
			startConvert(osName, deviceName, renderDevice, options)
		}
    
}

def startConvert(osName, deviceName, renderDevice, options) {
	def labels = "${osName} && RenderService && ${renderDevice}"
	def nodesCount = getNodesCount(labels)
	boolean successfullyDone = false

	print("${options.maxAttempts}")
	def maxAttempts = "${options.maxAttempts}".toInteger()
	def testTasks = [:]
	def currentLabels = labels
	for (int attemptNum = 1; attemptNum <= maxAttempts && attemptNum <= nodesCount; attemptNum++) {
		def currentNodeName = ""

		echo "Scheduling Convert ${osName}:${deviceName}. Attempt #${attemptNum}"
		testTasks["Convert-${osName}-${deviceName}"] = {
			node(currentLabels) {
				stage("Conversion") {
					timeout(time: 65, unit: 'MINUTES') {
						ws("WS/${options.PRJ_NAME}") {
							currentNodeName =  "${env.NODE_NAME}"
							try {
								executeConvert(osName, deviceName, options)
								successfullyDone = true
							} catch (e) {
								println(e.toString());
								println(e.getMessage());
								println(e.getStackTrace());
								print e

								//Exclude failed node name
								currentLabels = currentLabels + " && !" + currentNodeName
						    	println(currentLabels)
						    	currentBuild.result = 'SUCCESS'
							}
						}
					}
				}
			}
		}

		parallel testTasks	
	    
		if (successfullyDone) {
			break
		}
	}

	if (!successfullyDone) {
		throw new Exception("Job was failed by all used nodes!")
	}
}

def getNodesCount(labels) {
	def nodes = jenkins.model.Jenkins.instance.getLabel(labels).getNodes()
	def nodesCount = 0
	for (int i = 0; i < nodes.size(); i++) {
		if (nodes[i].toComputer().isOnline()) {
			nodesCount++
		}
	}

	return nodesCount
}
	
def call(String Tool = '',
	String Scene = '',  
	String PCs = '',
	String id = '',
	String sceneName = '',
	String sceneUser = '',
	String maxAttempts = ''
	) {
	String PRJ_ROOT='RenderServiceConvertJob'
	String PRJ_NAME='RenderServiceConvertJob'  
	main(PCs,[
		enableNotifications:false,
		PRJ_NAME:PRJ_NAME,
		PRJ_ROOT:PRJ_ROOT,
		Tool:Tool,
		Scene:Scene,
		id:id,
		sceneName:sceneName,
		sceneUser:sceneUser,
		maxAttempts:maxAttempts
		])
	}
