def executeRender(osName, gpuName, Map options) {
    currentBuild.result = 'SUCCESS'
    
    String tool = options['Tool'].split(':')[0].trim()
    String version = options['Tool'].split(':')[1].trim()
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
						def exists = fileExists "..\\..\\RenderServiceStorage\\${options.sceneName}"
						if (exists) {
							print("Scene is copying from Render Service Storage on this PC")
							bat """
								copy "..\\..\\RenderServiceStorage\\${options.sceneName}" "${options.sceneName}"
							"""
						} else {
							python3("${CIS_TOOLS}\\${options.cis_tools}\\download_scene.py --url \"${options.django_url}\" --id ${options.Scene} --scene_name \"${options.sceneName}\"")
							bat """
								copy "${options.sceneName}" "..\\..\\RenderServiceStorage"
							"""
						}
					} catch(e) {
						print e
						fail_reason = "Downloading failed"
					}
					
					// unzip
					try {
						if ("${options.sceneName}".endsWith('.zip') || "${options.sceneName}".endsWith('.7z')) {
							bat """
								7z x "${options.sceneName}"
							"""
						}
					} catch(e) {
						print e
						fail_reason = "Incorrect zip file"
					}
					
					switch(tool) {
						case 'Blender':  
							// copy necessary scripts for render
							bat """
								copy "${CIS_TOOLS}\\${options.cis_tools}\\blender_render.py" "."
								copy "${CIS_TOOLS}\\${options.cis_tools}\\launch_blender.py" "."
							"""
							// Launch render
							try {
								python3("launch_blender.py --tool ${version} --django_ip \"${options.django_url}/\" --id ${id} --build_number ${currentBuild.number} --min_samples ${options.Min_Samples} --max_samples ${options.Max_Samples} --noise_threshold ${options.Noise_threshold} --width ${options.Width} --height ${options.Height} --startFrame ${options.startFrame} --endFrame ${options.endFrame} ")
							} catch(e) {
								print e
								// if status == failure then copy full path and send to slack
								bat """
									mkdir "..\\..\\RenderServiceStorage\\failed_${options.sceneName}_${id}_${currentBuild.number}"
									copy "*" "..\\..\\RenderServiceStorage\\failed_${options.sceneName}_${id}_${currentBuild.number}"
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

	    boolean PRODUCTION = false

	    if (PRODUCTION) {
		options['django_url'] = "https://render.cis.luxoft.com"
		options['plugin_storage'] = "https://render.cis.luxoft.com/media/plugins/"
		options['cis_tools'] = "RenderServiceScripts"
		options['jenkins_job'] = "RenderServiceRenderJob"
	    } else {
		options['django_url'] = "172.26.157.251:84"
		options['plugin_storage'] = "https://testrender.cis.luxoft.com/media/plugins/"
		options['cis_tools'] = "RenderServiceScriptsDebug"
		options['jenkins_job'] = "RenderServiceRenderJobDebug"
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
			echo "Scheduling Render ${osName}:${deviceName}"
			testTasks["Render-${osName}-${deviceName}"] = {
				node("${osName} && TestRenderService && ${renderDevice}") {
					stage("Render") {
						timeout(time: 65, unit: 'MINUTES') {
							ws("WS/${options.PRJ_NAME}_Render") {
								executeRender(osName, deviceName, options)
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
    
def call(String PCs = '',
    String id = '',
    String Tool = '',
    String Scene = '',  
    String sceneName = '',
    String Min_samples = '',
    String Max_samples = '',
    String Noise_threshold = '',
    String startFrame = '',
    String endFrame = '',
    String Width = '',
    String Height = ''
    ) {
	String PRJ_ROOT='RenderServiceRenderJobDebug'
	String PRJ_NAME='RenderServiceRenderJobDebug'  
	main(PCs,[
	    enableNotifications:false,
	    PRJ_NAME:PRJ_NAME,
	    PRJ_ROOT:PRJ_ROOT,
	    id:id,
	    Tool:Tool,
	    Scene:Scene,
	    sceneName:sceneName,
	    Min_Samples:Min_Samples,
	    Max_Samples:Max_Samples,
	    Noise_threshold:Noise_threshold,
	    startFrame:startFrame,
	    endFrame:endFrame,
	    Width:Width,
	    Height:Height
	    ])
    }
