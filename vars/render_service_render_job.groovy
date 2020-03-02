def executeRender(osName, gpuName, Map options) {
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
						print e
						fail_reason = "Downloading scripts failed"
					}
					// download scene, check if it is already downloaded
					try {
					    // initialize directory RenderServiceStorage
					    dir("..\\..\\RenderServiceStorage"){
					        writeFile file:'test', text:'dir created'
					    }
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
						print e
						fail_reason = "Downloading scene failed"
					}
					switch(tool) {
						case 'Blender':
							// copy necessary scripts for render
							bat """
								copy ".\\render_service_scripts\\blender_render.py" "."
								copy ".\\render_service_scripts\\launch_blender.py" "."
							"""
							// Launch render
							try {
								python3("launch_blender.py --tool ${version} --django_ip \"${options.django_url}/\" --scene_name \"${scene_name}\" --id ${id} --build_number ${currentBuild.number} --min_samples ${options.Min_Samples} --max_samples ${options.Max_Samples} --noise_threshold ${options.Noise_threshold} --height ${options.Height} --width ${options.Width} --startFrame ${options.startFrame} --endFrame ${options.endFrame} ")
							} catch(e) {
								print e
								// if status == failure then copy full path and send to slack
								bat """
									mkdir "..\\..\\RenderServiceStorage\\failed_${scene_name}_${id}_${currentBuild.number}"
									copy "*" "..\\..\\RenderServiceStorage\\failed_${scene_name}_${id}_${currentBuild.number}"
								"""
							}
							break;

						case 'Max':
							// copy necessary scripts for render
							bat """
								copy ".\\render_service_scripts\\max_render.ms" "."
								copy ".\\render_service_scripts\\launch_max.py" "."
							"""
							// Launch render
							try {
								python3("launch_max.py --tool ${version} --django_ip \"${options.django_url}/\" --scene_name \"${scene_name}\" --id ${id} --build_number ${currentBuild.number} --min_samples ${options.Min_Samples} --max_samples ${options.Max_Samples} --noise_threshold ${options.Noise_threshold} --width ${options.Width} --height ${options.Height} --startFrame ${options.startFrame} --endFrame ${options.endFrame} ")
							} catch(e) {
								print e
								// if status == failure then copy full path and send to slack
								bat """
									mkdir "..\\..\\RenderServiceStorage\\failed_${scene_name}_${id}_${currentBuild.number}"
									copy "*" "..\\..\\RenderServiceStorage\\failed_${scene_name}_${id}_${currentBuild.number}"
								"""
							}
							break;

						case 'Maya':
							// copy necessary scripts for render	
							bat """
								copy ".\\render_service_scripts\\maya_render.py" "."
								copy ".\\render_service_scripts\\launch_maya.py" "."
							"""
							// Launch render
							try {
								python3("launch_maya.py --tool ${version} --django_ip \"${options.django_url}/\" --scene_name \"${scene_name}\" --id ${id} --build_number ${currentBuild.number} --min_samples ${options.Min_Samples} --max_samples ${options.Max_Samples} --noise_threshold ${options.Noise_threshold} --width ${options.Width} --height ${options.Height} --startFrame ${options.startFrame} --endFrame ${options.endFrame} ")
							} catch(e) {
								print e
								// if status == failure then copy full path and send to slack
								bat """
									mkdir "..\\..\\RenderServiceStorage\\failed_${scene_name}_${id}_${currentBuild.number}"
									copy "*" "..\\..\\RenderServiceStorage\\failed_${scene_name}_${id}_${currentBuild.number}"
								"""
							}
							break;

						case 'Maya (Redshift)':
							// copy necessary scripts for render	
							bat """
								copy ".\\render_service_scripts\\redshift_render.py" "."
								copy ".\\render_service_scripts\\launch_maya_redshift.py" "."
							"""
							// Launch render
							try {
								python3("launch_maya_redshift.py --tool ${version} --django_ip \"${options.django_url}/\" --id ${id} --build_number ${currentBuild.number} --min_samples ${options.Min_Samples} --max_samples ${options.Max_Samples} --noise_threshold ${options.Noise_threshold} --width ${options.Width} --height ${options.Height} --startFrame ${options.startFrame} --endFrame ${options.endFrame} ")
							} catch(e) {
								print e
								// if status == failure then copy full path and send to slack
								bat """
									mkdir "..\\..\\RenderServiceStorage\\failed_${scene_name}_${id}_${currentBuild.number}"
									copy "*" "..\\..\\RenderServiceStorage\\failed_${scene_name}_${id}_${currentBuild.number}"
								"""
							}
							break;

						case 'Core':
							// copy necessary scripts for render	
							bat """
								copy ".\\render_service_scripts\\find_scene_core.py" "."
								copy ".\\render_service_scripts\\launch_core_render.py" "."
							"""
							// Launch render
							try {
								python3("launch_core_render.py --tool ${version} --django_ip \"${options.django_url}/\" --id ${id} --build_number ${currentBuild.number} --min_samples ${options.Min_Samples} --max_samples ${options.Max_Samples} --noise_threshold ${options.Noise_threshold} --width ${options.Width} --height ${options.Height} --startFrame ${options.startFrame} --endFrame ${options.endFrame} ")
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
					print(python3(".\\render_service_scripts\\send_render_results.py --django_ip \"${options.django_url}/\" --build_number ${currentBuild.number} --status ${currentBuild.result} --fail_reason \"${fail_reason}\" --id ${id}"))
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
		options['django_url'] = "https://render.cis.luxoft.com/render/jenkins/"
		options['plugin_storage'] = "https://render.cis.luxoft.com/media/plugins/"
		options['scripts_branch'] = "master"
	    } else {
		options['django_url'] = "https://testrender.cis.luxoft.com/render/jenkins/"
		options['plugin_storage'] = "https://testrender.cis.luxoft.com/media/plugins/"
		options['scripts_branch'] = "develop"
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
				node("${osName} && RenderService && ${renderDevice}") {
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
    String sceneUser = '',
    String Min_samples = '',
    String Max_samples = '',
    String Noise_threshold = '',
    String startFrame = '',
    String endFrame = '',
    String Width = '',
    String Height = ''
    ) {
	String PRJ_ROOT='RenderServiceRenderJob'
	String PRJ_NAME='RenderServiceRenderJob'  
	main(PCs,[
	    enableNotifications:false,
	    PRJ_NAME:PRJ_NAME,
	    PRJ_ROOT:PRJ_ROOT,
	    id:id,
	    Tool:Tool,
	    Scene:Scene,
	    sceneName:sceneName,
	    sceneUser:sceneUser,
	    Min_Samples:Min_Samples,
	    Max_Samples:Max_Samples,
	    Noise_threshold:Noise_threshold,
	    startFrame:startFrame,
	    endFrame:endFrame,
	    Width:Width,
	    Height:Height
	    ])
    }
