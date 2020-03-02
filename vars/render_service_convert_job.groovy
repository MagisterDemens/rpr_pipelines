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
					// download scene, check if it is already downloaded
					try {
						print(python3("${CIS_TOOLS}\\${options.cis_tools}\\send_render_status.py --django_ip \"${options.django_url}/\" --tool \"${tool}\" --status \"Downloading scene\" --id ${id}"))
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
								copy "${scene_name}" "..\\..\\RenderServiceStorage\\${scene_user}"
								if not exist "..\\..\\RenderServiceStorage\\${scene_user}\\" mkdir "..\\..\\RenderServiceStorage\\${scene_user}"
								copy "${scene_name}" "..\\..\\RenderServiceStorage\\${scene_user}\\${scene_name}"
							"""
						}
					} catch(e) {
						print e
						fail_reason = "Downloading failed"
					}
					
					// unzip
					try {
						if ("${scene_name}".endsWith('.zip') || "${scene_name}".endsWith('.7z')) {
							bat """
								7z x "${scene_name}"
							"""
						}
					} catch(e) {
						print e
						fail_reason = "Incorrect zip file"
					}
					
					switch(tool) {
						case 'Maya Redshift':
							// update redshift 
							bat """
								cd "${CIS_TOOLS}\\..\\RenderServiceStorage\\RS2RPRConvertTool" 
								git pull
								cd "..\\..\\WS\\RenderServiceConvertJob"
								copy "${CIS_TOOLS}\\..\\RenderServiceStorage\\RS2RPRConvertTool\\convertRS2RPR.py" "."
							"""
							// copy necessary scripts for render
									bat """
										copy "${CIS_TOOLS}\\${options.cis_tools}\\launch_maya_redshift_conversion.py" "."
										copy "${CIS_TOOLS}\\${options.cis_tools}\\conversion_redshift_render.py" "."
										copy "${CIS_TOOLS}\\${options.cis_tools}\\conversion_rpr_render.py" "."
									"""
							// Launch render
							try {
								python3("launch_maya_redshift_conversion.py --tool ${version} --django_ip \"${options.django_url}/\" --id ${id} --build_number ${currentBuild.number} ")
							} catch(e) {
								print e
								// if status == failure then copy full path and send to slack
								bat """
									mkdir "..\\..\\RenderServiceStorage\\failed_${scene_name}_${id}_${currentBuild.number}"
									copy "*" "..\\..\\RenderServiceStorage\\failed_${scene_name}_${id}_${currentBuild.number}"
								"""
							}
							break;
				
					}   
				} catch(e) {
					currentBuild.result = 'FAILURE'
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
				options['cis_tools'] = "RenderServiceScripts"
				options['jenkins_job'] = "RenderServiceConvertJob"
			} else {
				options['django_url'] = "https://testrender.cis.luxoft.com/convert/jenkins/"
				options['plugin_storage'] = "https://testrender.cis.luxoft.com/media/plugins/"
				options['cis_tools'] = "RenderServiceScripts"
				options['jenkins_job'] = "RenderServiceConvertJob"
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
		
			try {
				echo "Scheduling Convert ${osName}:${deviceName}"
				testTasks["Convert-${osName}-${deviceName}"] = {
					node("${osName} && RenderService && ${renderDevice}") {
						stage("Conversion") {
							timeout(time: 65, unit: 'MINUTES') {
								ws("WS/${options.PRJ_NAME}") {
									executeConvert(osName, deviceName, options)
								}
							}
						}
					}
				}

				parallel testTasks

			} catch(e) {
				println(e.toString());
				println(e.getMessage());
				println(e.getStackTrace());
				currentBuild.result = "FAILED"
				print e
			} 
		}
    
}
	
def call(String Tool = '',
	String Scene = '',  
	String PCs = '',
	String id = '',
	String sceneName = '',
	String sceneUser = ''
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
		sceneUser:sceneUser
		])
	}
