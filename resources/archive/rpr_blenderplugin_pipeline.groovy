
def executeGenTestRefCommand(String osName, Map options)
{
    executeTestCommand(osName, options)
    
    try
    {   
        //for update existing manifest file
        receiveFiles("${options.REF_PATH_PROFILE}/baseline_manifest.json", './Work/Baseline/')
    }
    catch(e)
    {
        println("baseline_manifest.json not found")
    }
    
    dir('scripts')
    {
        switch(osName)
        {
        case 'Windows':
            bat """
            make_results_baseline.bat
            """
            break;
        case 'OSX':
            sh """
            ./make_results_baseline.sh
            """
            break;
        default:
            sh """
            ./make_results_baseline.sh
            """
        }
    }
}

def installPlugin(String osName, Map options)
{
    switch(osName)
    {
    case 'Windows':
        // remove installed plugin
        try
        {
            powershell"""
            \$uninstall = Get-WmiObject -Class Win32_Product -Filter "Name = 'Radeon ProRender for Blender'"
            if (\$uninstall) {
            Write "Uninstalling..."
            \$uninstall = \$uninstall.IdentifyingNumber
            start-process "msiexec.exe" -arg "/X \$uninstall /qn /quiet /L+ie ${options.stageName}.uninstall.log /norestart" -Wait
            }else{
            Write "Plugin not found"}
            """
        }
        catch(e)
        {
            println("Error while deinstall plugin")
            println(e.toString())
            println(e.getMessage())
        }
        // install new plugin
        dir('temp/install_plugin')
		{
			bat """
			IF EXIST "${CIS_TOOLS}\\..\\PluginsBinaries" (
				forfiles /p "${CIS_TOOLS}\\..\\PluginsBinaries" /s /d -2 /c "cmd /c del @file"
                powershell -c "\$folderSize = (Get-ChildItem -Recurse \"${CIS_TOOLS}\\..\\PluginsBinaries\" | Measure-Object -Property Length -Sum).Sum / 1GB; if (\$folderSize -ge 10) {Remove-Item -Recurse -Force \"${CIS_TOOLS}\\..\\PluginsBinaries\";};"
			)
			"""
            
            if(!(fileExists("${CIS_TOOLS}/../PluginsBinaries/${options.pluginWinSha}.msi")))
            {
                unstash 'appWindows'
                bat """
                IF NOT EXIST "${CIS_TOOLS}\\..\\PluginsBinaries" mkdir "${CIS_TOOLS}\\..\\PluginsBinaries"
                rename RadeonProRenderBlender.msi ${options.pluginWinSha}.msi
                copy ${options.pluginWinSha}.msi "${CIS_TOOLS}\\..\\PluginsBinaries\\${options.pluginWinSha}.msi"
                """
            }
            else
            {
                bat """
                copy "${CIS_TOOLS}\\..\\PluginsBinaries\\${options.pluginWinSha}.msi" ${options.pluginWinSha}.msi
                """
            }

            bat """
            msiexec /i "${options.pluginWinSha}.msi" /quiet /qn PIDKEY=${env.RPR_PLUGIN_KEY} /L+ie ../../${options.stageName}.install.log /norestart
            """

            // duct tape for plugin registration
            try
            {
                bat"""
                echo "----------DUCT TAPE. Try adding addon from blender" >>../../${options.stageName}.install.log
                """

                bat """
                echo import bpy >> registerRPRinBlender.py
                echo import os >> registerRPRinBlender.py
                echo addon_path = "C:\\Program Files\\AMD\\RadeonProRenderPlugins\\Blender\\\\addon.zip" >> registerRPRinBlender.py
                echo bpy.ops.wm.addon_install(filepath=addon_path) >> registerRPRinBlender.py
                echo bpy.ops.wm.addon_enable(module="rprblender") >> registerRPRinBlender.py
                echo bpy.ops.wm.save_userpref() >> registerRPRinBlender.py

                "C:\\Program Files\\Blender Foundation\\Blender\\blender.exe" -b -P registerRPRinBlender.py >>../../${options.stageName}.install.log 2>&1
                """
            }
            catch(e)
            {
                echo "Error during rpr register"
                println(e.toString())
                println(e.getMessage())
            }
        }

        //new matlib migration
        try
        {
            try
            {
                powershell"""
                \$uninstall = Get-WmiObject -Class Win32_Product -Filter "Name = 'Radeon ProRender Material Library'"
                if (\$uninstall) {
                Write "Uninstalling..."
                \$uninstall = \$uninstall.IdentifyingNumber
                start-process "msiexec.exe" -arg "/X \$uninstall /qn /quiet /L+ie ${STAGE_NAME}.matlib.uninstall.log /norestart" -Wait
                }else{
                Write "Plugin not found"}
                """
            }
            catch(e)
            {
                echo "Error while deinstall plugin"
                echo e.toString()
            }
            
            receiveFiles("/bin_storage/RadeonProMaterialLibrary.msi", "/mnt/c/TestResources/")
            bat """
            msiexec /i "C:\\TestResources\\RadeonProMaterialLibrary.msi" /quiet /L+ie ${STAGE_NAME}.matlib.install.log /norestart
            """
        }
        catch(e)
        {
            println(e.getMessage())
            println(e.toString())
        }
        break
    case 'OSX':
        // TODO: make implicit plugin deletion
        dir('temp/install_plugin')
        {

            // remove old files
            sh """
            if [ -d "${CIS_TOOLS}\\..\\PluginsBinaries" ]; then
                find "${CIS_TOOLS}/../PluginsBinaries" -mtime +2 -delete
                find "${CIS_TOOLS}/../PluginsBinaries" -size +50G -delete
            fi
            """

            //if need unstask new installer
            if(!(fileExists("${CIS_TOOLS}/../PluginsBinaries/${options.pluginOSXSha}.dmg")))
            {
                unstash "app${osName}"
                sh """
                mkdir -p "${CIS_TOOLS}/../PluginsBinaries"
                mv RadeonProRenderBlender.dmg "${CIS_TOOLS}/../PluginsBinaries/${options.pluginOSXSha}.dmg"
                """
            }

            sh"""
            $CIS_TOOLS/installBlenderPlugin.sh ${CIS_TOOLS}/../PluginsBinaries/${options.pluginOSXSha}.dmg >>../../${options.stageName}.install.log 2>&1
            """
        }
        break
    default:
        
        dir('temp/install_plugin')
        {
            // remove installed plugin
            try
            {
                sh"""
                /home/user/.local/share/rprblender/uninstall.py /home/user/Desktop/blender-2.79-linux-glibc219-x86_64/ >>../../${options.stageName}.uninstall.log 2>&1
                """
            }

            catch(e)
            {
                echo "Error while deinstall plugin"
                println(e.toString())
                println(e.getMessage())
            }


            // remove old files
            sh """
            if [ -d "${CIS_TOOLS}\\..\\PluginsBinaries" ]; then
                find "${CIS_TOOLS}/../PluginsBinaries" -mtime +2 -delete
                find "${CIS_TOOLS}/../PluginsBinaries" -size +50G -delete
            fi
            """

            //if need unstask new installer
        	if(!(fileExists("${CIS_TOOLS}/../PluginsBinaries/${options.pluginUbuntuSha}.run")))
            {
                unstash "app${osName}"
                sh """
                mkdir -p "${CIS_TOOLS}/../PluginsBinaries"
                chmod +x RadeonProRenderBlender.run
                mv RadeonProRenderBlender.run "${CIS_TOOLS}/../PluginsBinaries/${options.pluginUbuntuSha}.run"
                """
            }

            // install plugin
            sh """
            #!/bin/bash
            printf "${env.RPR_PLUGIN_KEY}\nq\n\ny\ny\n" > input.txt
            exec 0<input.txt
            exec &>${env.WORKSPACE}/${options.stageName}.install.log
            ${CIS_TOOLS}/../PluginsBinaries/${options.pluginUbuntuSha}.run --nox11 --noprogress ~/Desktop/blender-2.79-linux-glibc219-x86_64 >> ../../${options.stageName}.install.log 2>&1
            """
        }
    }
}

def buildRenderCache(String osName)
{
    switch(osName)
    {
        case 'Windows':
            // TODO: FIX: relative path to blender.exe
            bat '"C:\\Program Files\\Blender Foundation\\Blender\\blender.exe" -b -E RPR -f 0'
            break;
        case 'OSX':
            sh "blender -b -E RPR -f 0"
            break;
        default:
            sh "blender -b -E RPR -f 0"
    }
}

def executeTestCommand(String osName, Map options)
{
    if (!options['skipBuild'])
    {
        installPlugin(osName, options)
        buildRenderCache(osName)
    }

    switch(osName)
    {
    case 'Windows':
        dir('scripts')
        {          
            bat """
            run.bat ${options.renderDevice} ${options.testsPackage} \"${options.tests}\">> ../${options.stageName}.log  2>&1
            """
        }
        break;
    case 'OSX':
        dir("scripts")
        {           
            sh """
            ./run.sh ${options.renderDevice} ${options.testsPackage} \"${options.tests}\" >> ../${options.stageName}.log 2>&1
            """
        }
        break;
    default:
        dir("scripts")
        {           
            sh """
            ./run.sh ${options.renderDevice} ${options.testsPackage} \"${options.tests}\" >> ../${options.stageName}.log 2>&1
            """
        }
    }
}

def executeTests(String osName, String asicName, Map options)
{
    try {
        checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_blender.git')
        
        // update assets
        if(isUnix())
        {
            sh """
            ${CIS_TOOLS}/receiveFilesSync.sh ${options.PRJ_ROOT}/${options.PRJ_NAME}/BlenderAssets/ ${CIS_TOOLS}/../TestResources/BlenderAssets
            """
        }
        else
        {
            bat """
            %CIS_TOOLS%\\receiveFilesSync.bat ${options.PRJ_ROOT}/${options.PRJ_NAME}/BlenderAssets/ /mnt/c/TestResources/BlenderAssets
            """
        }

        String REF_PATH_PROFILE="${options.REF_PATH}/${asicName}-${osName}"
        String JOB_PATH_PROFILE="${options.JOB_PATH}/${asicName}-${osName}"

        options.REF_PATH_PROFILE = REF_PATH_PROFILE

        outputEnvironmentInfo(osName, options.stageName)
        
        if(options['updateRefs'])
        {
            executeGenTestRefCommand(osName, options)
            sendFiles('./Work/Baseline/', REF_PATH_PROFILE)
        }
        else
        {
        	// TODO: receivebaseline for json suite
            try {
                receiveFiles("${REF_PATH_PROFILE}/baseline_manifest.json", './Work/Baseline/')
	    	    options.tests.split(" ").each() {
                    receiveFiles("${REF_PATH_PROFILE}/${it}", './Work/Baseline/')
                }
            } catch (e) {println("Baseline doesn't exist.")}

            executeTestCommand(osName, options)
        }
    }
    catch(e) {
        println(e.toString())
        println(e.getMessage())
        options.failureMessage = "Failed during testing: ${asicName}-${osName}"
        options.failureError = e.getMessage()
        currentBuild.result = "FAILED"
        throw e
    }
    finally {
        archiveArtifacts "*.log"
        echo "Stashing test results to : ${options.testResultsName}"
        dir('Work')
        {
            stash includes: '**/*', name: "${options.testResultsName}", allowEmpty: true

            try {
                def sessionReport = readJSON file: 'Results/Blender/session_report.json'
                // if none launched tests - mark build failed
                if (sessionReport.summary.total == 0) {
                    options.failureMessage = "Noone test was finished for: ${asicName}-${osName}"
                    currentBuild.result = "FAILED"
                }

                if (options.sendToRBS)
                {
                    try
                    {
                        def response = httpRequest consoleLogResponseBody: true, httpMode: 'POST', authentication: '847a5a5d-700d-439b-ace1-518f415eb8d8',  url: 'https://rbsdbdev.cis.luxoft.com/api/login', validResponseCodes: '200'
                        println('Status: '+response.status)
                        println('Response: '+response.content)
                        def json_tok = readJSON text: "${response.content}"
                        
                        String report = readFile("Results/Blender/${options.tests}/report_compare.json")
                        report = report.replaceAll("\n", "")

                        writeJSON file: 'temp_machine_info.json', json: sessionReport.machine_info
                        String machine_info = readFile("temp_machine_info.json")
                        machine_info = machine_info.replaceAll("\n", "")
                        String requestData = """{
                           "job": "${BUILD_NUMBER}",
                           "group": "${options.tests}",
                           "tool": "Blender",
                           "branch": "master",
                           "machine_info": ${machine_info},
                           "test_results": ${report}
                           }"""
                        requestData = requestData.replaceAll("\n", "")
                        writeFile encoding: 'UTF-8', file: 'temp_group_report.json', text: requestData

                        if(isUnix())
                        {
                            sh """
                            curl -H "Authorization: token ${json_tok}" -X POST -F file=@temp_group_report.json https://rbsdbdev.cis.luxoft.com/report/group
                            """
                        }
                        else
                        {
                            bat """
                            curl -H "Authorization: token ${json_tok}" -X POST -F file=@temp_group_report.json https://rbsdbdev.cis.luxoft.com/report/group
                            """
                        }
                    }
                    catch(e)
                    {
                        println(e.getMessage())
                    }
                }
            } catch (e) {
                println(e.toString())
                println(e.getMessage())
            }
        }
    }
}

def executeBuildWindows(Map options)
{
    dir('RadeonProRenderPkgPlugin\\BlenderPkg')
    {
        bat """
        build_win_installer.cmd >> ../../${STAGE_NAME}.log  2>&1
        """
        
        String branch_postfix = ""
        if(env.BRANCH_NAME && BRANCH_NAME != "master")
        {
            branch_postfix = BRANCH_NAME.replace('/', '-')
        }
        if(env.Branch && Branch != "master")
        {
            branch_postfix = Branch.replace('/', '-')
        }
        if(branch_postfix)
        {
            bat """
            rename RadeonProRender*msi *.(${branch_postfix}).msi
            """
        }

        archiveArtifacts "RadeonProRender*.msi"
        archiveArtifacts "addon.zip"

        bat '''
        for /r %%i in (RadeonProRender*.msi) do copy %%i RadeonProRenderBlender.msi
        '''
        
        stash includes: 'RadeonProRenderBlender.msi', name: 'appWindows'
        options.pluginWinSha = sha1 'RadeonProRenderBlender.msi'
    }
}

def executeBuildOSX(Map options)
{
    dir('RadeonProRenderBlenderAddon/ThirdParty')
    {
        sh '''
            ThirdPartyDir="../../RadeonProRenderThirdPartyComponents"

            if [ -d "$ThirdPartyDir" ]; then
                echo Updating $ThirdPartyDir

                rm -rf AxfPackage
                rm -rf "Expat 2.1.0"
                rm -rf OpenCL
                rm -rf OpenColorIO
                rm -rf "RadeonProImageProcessing"
                rm -rf "RadeonProRender SDK"
                rm -rf RadeonProRender-GLTF
                rm -rf ffmpeg
                rm -rf glew
                rm -rf json
                rm -rf oiio
                rm -rf oiio-mac
                rm -rf synColor

                cp -r $ThirdPartyDir/AxfPackage AxfPackage
                cp -r "$ThirdPartyDir/Expat 2.1.0" "Expat 2.1.0"
                cp -r $ThirdPartyDir/OpenCL OpenCL
                cp -r $ThirdPartyDir/OpenColorIO OpenColorIO
                cp -r $ThirdPartyDir/RadeonProImageProcessing RadeonProImageProcessing
                cp -r "$ThirdPartyDir/RadeonProRender SDK" "RadeonProRender SDK"
                cp -r $ThirdPartyDir/RadeonProRender-GLTF RadeonProRender-GLTF
                cp -r $ThirdPartyDir/ffmpeg ffmpeg
                cp -r $ThirdPartyDir/glew glew
                cp -r $ThirdPartyDir/json json
                cp -r $ThirdPartyDir/oiio oiio
                cp -r $ThirdPartyDir/oiio-mac oiio-mac
                cp -r $ThirdPartyDir/synColor synColor

            else
                echo Cannot update as $ThirdPartyDir missing
            fi
            '''                                    
    }
    
    dir('RadeonProRenderBlenderAddon')
    {
        sh """
        ./build_osx.sh /usr/bin/castxml >> ../${STAGE_NAME}.log  2>&1
        """
    }
    dir('RadeonProRenderPkgPlugin/BlenderPkg')
    {
        sh """
        ./build_osx_installer.sh >> ../../${STAGE_NAME}.log  2>&1
        """
        
        dir('.installer_build')
        {
            String branch_postfix = ""
            if(env.BRANCH_NAME && BRANCH_NAME != "master")
            {
                branch_postfix = BRANCH_NAME.replace('/', '-')
            }
            if(env.Branch && Branch != "master")
            {
                branch_postfix = Branch.replace('/', '-')
            }
            if(branch_postfix)
            {
                sh"""
                for i in RadeonProRender*; do name="\${i%.*}"; mv "\$i" "\${name}.(${branch_postfix})\${i#\$name}"; done
                """
            }
            sh 'cp RadeonProRender*.dmg ../RadeonProRenderBlender.dmg'
            
            archiveArtifacts "RadeonProRender*.dmg"
            sh 'cp RadeonProRender*.dmg ../RadeonProRenderBlender.dmg'
        }
        stash includes: 'RadeonProRenderBlender.dmg', name: "appOSX"
        options.pluginOSXSha = sha1 'RadeonProRenderBlender.dmg'
    }
}

def executeBuildLinux(Map options, String osName)
{
    dir('RadeonProRenderBlenderAddon/ThirdParty')
    {
        sh '''
            ThirdPartyDir="../../RadeonProRenderThirdPartyComponents"

            if [ -d "$ThirdPartyDir" ]; then
                echo Updating $ThirdPartyDir

                rm -rf AxfPackage
                rm -rf "Expat 2.1.0"
                rm -rf OpenCL
                rm -rf OpenColorIO
                rm -rf "RadeonProImageProcessing"
                rm -rf "RadeonProRender SDK"
                rm -rf RadeonProRender-GLTF
                rm -rf ffmpeg
                rm -rf glew
                rm -rf json
                rm -rf oiio
                rm -rf oiio-mac
                rm -rf synColor

                cp -r $ThirdPartyDir/AxfPackage AxfPackage
                cp -r "$ThirdPartyDir/Expat 2.1.0" "Expat 2.1.0"
                cp -r $ThirdPartyDir/OpenCL OpenCL
                cp -r $ThirdPartyDir/OpenColorIO OpenColorIO
                cp -r $ThirdPartyDir/RadeonProImageProcessing RadeonProImageProcessing
                cp -r "$ThirdPartyDir/RadeonProRender SDK" "RadeonProRender SDK"
                cp -r $ThirdPartyDir/RadeonProRender-GLTF RadeonProRender-GLTF
                cp -r $ThirdPartyDir/ffmpeg ffmpeg
                cp -r $ThirdPartyDir/glew glew
                cp -r $ThirdPartyDir/json json
                cp -r $ThirdPartyDir/oiio oiio
                cp -r $ThirdPartyDir/oiio-mac oiio-mac
                cp -r $ThirdPartyDir/synColor synColor

            else
                echo Cannot update as $ThirdPartyDir missing
            fi
        '''
    }
    
    dir('RadeonProRenderBlenderAddon')
    {
        sh """
        ./build.sh /usr/bin/castxml >> ../${STAGE_NAME}.log  2>&1
        """
    }
    dir('RadeonProRenderPkgPlugin/BlenderPkg')
    {
        sh """
        ./build_linux_installer.sh >> ../../${STAGE_NAME}.log  2>&1
        """

        dir('.installer_build')
        {
            String branch_postfix = ""
            if(env.BRANCH_NAME && BRANCH_NAME != "master")
            {
                branch_postfix = BRANCH_NAME.replace('/', '-')
            }
            if(env.Branch && Branch != "master")
            {
                branch_postfix = Branch.replace('/', '-')
            }
            if(branch_postfix)
            {
                sh"""
                for i in RadeonProRender*; do name="\${i%.*}"; mv "\$i" "\${name}.(${branch_postfix})\${i#\$name}"; done
                """
            }

            archiveArtifacts "RadeonProRender*.run"
            sh 'cp RadeonProRender*.run ../RadeonProRenderBlender.run'
        }
        stash includes: 'RadeonProRenderBlender.run', name: "app${osName}"
        options.pluginUbuntuSha = sha1 'RadeonProRenderBlender.run'
    }
}

def executeBuild(String osName, Map options)
{
    try {        
        dir('RadeonProRenderBlenderAddon')
        {
            checkOutBranchOrScm(options['projectBranch'], 'https://github.com/Radeon-Pro/RadeonProRenderBlenderAddon.git')
        }
        dir('RadeonProRenderThirdPartyComponents')
        {
            checkOutBranchOrScm(options['thirdpartyBranch'], 'https://github.com/Radeon-Pro/RadeonProRenderThirdPartyComponents.git')
        }
        dir('RadeonProRenderPkgPlugin')
        {
            checkOutBranchOrScm(options['packageBranch'], 'https://github.com/Radeon-Pro/RadeonProRenderPkgPlugin.git')
        }
        outputEnvironmentInfo(osName)

        switch(osName)
        {
        case 'Windows': 
            executeBuildWindows(options); 
            break;
        case 'OSX':
            executeBuildOSX(options);
            break;
        default: 
            executeBuildLinux(options, osName);
        }
    }
    catch (e) {
        options.failureMessage = "Error during build ${osName}"
        options.failureError = e.getMessage()
        currentBuild.result = "FAILED"
        throw e
    }
    finally {
        archiveArtifacts "*.log"
    }                        
}

def executePreBuild(Map options)
{
    currentBuild.description = ""
    ['projectBranch', 'packageBranch'].each
    {
        if(options[it] != 'blender_2.7' && options[it] != "")
        {
            currentBuild.description += "<b>${it}:</b> ${options[it]}<br/>"
        }
    }
    if(options['thirdpartyBranch'] != 'master') 
    {
        currentBuild.description += "<b>thirdpartyBranch:</b> ${options['thirdpartyBranch']}<br/>"
    }
    
    dir('RadeonProRenderBlenderAddon')
    {
        checkOutBranchOrScm(options['projectBranch'], 'git@github.com:Radeon-Pro/RadeonProRenderBlenderAddon.git')

        AUTHOR_NAME = bat (
                script: "git show -s --format=%%an HEAD ",
                returnStdout: true
                ).split('\r\n')[2].trim()

        echo "The last commit was written by ${AUTHOR_NAME}."
        options.AUTHOR_NAME = AUTHOR_NAME
        
        commitMessage = bat ( script: "git log --format=%%B -n 1", returnStdout: true )
        echo "Commit message: ${commitMessage}"
        
        options.commitMessage = commitMessage.split('\r\n')[2].trim()
        echo "Opt.: ${options.commitMessage}"
        options['commitSHA'] = bat(script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()
                
        if(options['incrementVersion'])
        {
            if("${BRANCH_NAME}" == "blender_2.7" && "${AUTHOR_NAME}" != "radeonprorender")
            {
                options.testsPackage = "master"
                echo "Incrementing version of change made by ${AUTHOR_NAME}."

                String currentversion=version_read('src/rprblender/__init__.py', '"version": (', ', ')
                echo "currentversion ${currentversion}"

                new_version=version_inc(currentversion, 3, ', ')
                echo "new_version ${new_version}"

                version_write('src/rprblender/__init__.py', '"version": (', new_version, ', ')

                String updatedversion=version_read('src/rprblender/__init__.py', '"version": (', ', ', "true")
                echo "updatedversion ${updatedversion}"                    
                
                bat """
                    git add src/rprblender/__init__.py
                    git commit -m "buildmaster: version update to ${updatedversion}"
                    git push origin HEAD:blender_2.7
                   """ 
                
                //get commit's sha which have to be build
                options['projectBranch'] = bat ( script: "git log --format=%%H -1 ",
                                    returnStdout: true
                                    ).split('\r\n')[2].trim()

                options['executeBuild'] = true
                options['executeTests'] = true
            }
            else
            {
                options.testsPackage = "smoke"
                if(commitMessage.contains("CIS:BUILD"))
                {
                    options['executeBuild'] = true
                }

                if(commitMessage.contains("CIS:TESTS"))
                {
                    options['executeBuild'] = true
                    options['executeTests'] = true
                }

                if (env.CHANGE_URL)
                {
                    echo "branch was detected as Pull Request"
                    options['executeBuild'] = true
                    options['executeTests'] = true
                    options.testsPackage = "PR"
                }
                
                if("${BRANCH_NAME}" == "blender_2.7") 
                {
                   echo "rebuild master"
                   options['executeBuild'] = true
                   options['executeTests'] = true
                   options.testsPackage = "master"
                }
            }
        }
        options.pluginVersion = version_read('src/rprblender/__init__.py', '"version": (', ', ').replace(', ', '.')
    }
    if(env.CHANGE_URL)
    {
        //TODO: fix sha for PR
    	//options.comitSHA = bat ( script: "git log --format=%%H HEAD~1 -1", returnStdout: true ).split('\r\n')[2].trim()
        options.AUTHOR_NAME = env.CHANGE_AUTHOR_DISPLAY_NAME
    	if (env.CHANGE_TARGET != 'blender_2.7') {
			options['executeBuild'] = false
			options['executeTests'] = false
		}
        options.commitMessage = env.CHANGE_TITLE
    }
    // if manual job
    if(options['forceBuild'])
    {
        options['executeBuild'] = true
        options['executeTests'] = true
    }

    currentBuild.description += "<b>Version:</b> ${options.pluginVersion}<br/>"
    if(!env.CHANGE_URL)
    {
        currentBuild.description += "<b>Commit author:</b> ${options.AUTHOR_NAME}<br/>"
        currentBuild.description += "<b>Commit message:</b> ${options.commitMessage}<br/>"
    }
    
    if (env.BRANCH_NAME && env.BRANCH_NAME == "blender_2.7") {
        properties([[$class: 'BuildDiscarderProperty', strategy: 	
                         [$class: 'LogRotator', artifactDaysToKeepStr: '', 	
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '25']]]);
    } else if (env.BRANCH_NAME && BRANCH_NAME != "blender_2.7") {
        properties([[$class: 'BuildDiscarderProperty', strategy: 	
                         [$class: 'LogRotator', artifactDaysToKeepStr: '', 	
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '3']]]);
    } else if (env.JOB_NAME == "RadeonProRenderBlenderPlugin-WeeklyFull") {
        properties([[$class: 'BuildDiscarderProperty', strategy: 	
                         [$class: 'LogRotator', artifactDaysToKeepStr: '', 	
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '60']]]);
    } else {
        properties([[$class: 'BuildDiscarderProperty', strategy: 	
                         [$class: 'LogRotator', artifactDaysToKeepStr: '', 	
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '20']]]);
    }

    if(options.splitTestsExectuion)
    {
        def tests = []
        if(options.testsPackage != "none")
        {
            dir('jobs_test_blender')
            {
                checkOutBranchOrScm(options['testsBranch'], 'https://github.com/luxteam/jobs_test_blender.git')
                // json means custom test suite. Split doesn't supported
                if(options.testsPackage.endsWith('.json'))
                {
                    options.testsList = ['']
                }
                // options.splitTestsExecution = false
                String tempTests = readFile("jobs/${options.testsPackage}")
                tempTests.split("\n").each {
                    // TODO: fix: duck tape - error with line ending
                    tests << "${it.replaceAll("[^a-zA-Z0-9_]+","")}"
                }
                options.testsList = tests
                options.testsPackage = "none"
            }
        }
        else
        {
            options.tests.split(" ").each()
            {
                tests << "${it}"
            }
            options.testsList = tests
        }
    }
    else
    {
        options.testsList = ['']
    }

    if (options.sendToRBS)
    {
        try
        {
            def response = httpRequest consoleLogResponseBody: true, httpMode: 'POST', authentication: '847a5a5d-700d-439b-ace1-518f415eb8d8',  url: 'https://rbsdbdev.cis.luxoft.com/api/login', validResponseCodes: '200'
            println('Status: '+response.status)
            println('Response: '+response.content)
            def json_tok = readJSON text: "${response.content}"
            
            String branchName = env.BRANCH_NAME ?: options.projectBranch
            if (branchName == "blender_2.7")
            {
                branchName = "master"
            }
            def testsList = options.testsList

            String requestData = """{"name": "${env.BUILD_NUMBER}", "primary_time": "${options.JOB_STARTED_TIME}", "branch": "${branchName}", "tool": "Blender", "groups": ["${testsList.join(",")}"], "count_test_machine" : ${options.gpusCount}}"""
            def response1 = httpRequest acceptType: 'APPLICATION_JSON', consoleLogResponseBody: true, contentType: 'APPLICATION_JSON', customHeaders: [[name: 'Authorization', value: "Token ${json_tok.token}"]], httpMode: 'POST', ignoreSslErrors: true, url: "https://rbsdbdev.cis.luxoft.com/report/job?data=${java.net.URLEncoder.encode(requestData, 'UTF-8')}", validResponseCodes: '200'
            echo "Status: ${response1.status}\nContent: ${response1.content}"
        }
        catch(e)
        {
            println(e.getMessage())
        }     
    }
}

def executeDeploy(Map options, List platformList, List testResultList)
{
    try {
        if(options['executeTests'] && testResultList)
        {
            checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_blender.git')
            
            dir("summaryTestResults")
            {
                testResultList.each()
                {
                    dir("$it".replace("testResult-", ""))
                    {
                        try
                        {
                            unstash "$it"
                        }
                        catch(e)
                        {
                            echo "Can't unstash ${it}"
                            println(e.toString())
                            println(e.getMessage())
                        }
                    }
                }
            }

            dir("jobs_launcher") {
                String branchName = env.BRANCH_NAME ?: options.projectBranch

                try {
                    withEnv(["JOB_STARTED_TIME=${options.JOB_STARTED_TIME}"])
                    {
                        bat """
                        build_reports.bat ..\\summaryTestResults "${escapeCharsByUnicode('Blender 2.79')}" ${options.commitSHA} ${branchName} \"${escapeCharsByUnicode(options.commitMessage)}\"
                        """
                    }
                } catch(e) {
                    println("ERROR during report building")
                    println(e.toString())
                    println(e.getMessage())
                }

                try
                {
                    bat "get_status.bat ..\\summaryTestResults"
                }
                catch(e)
                {
                    println("ERROR during slack status generation")
                    println(e.toString())
                    println(e.getMessage())   
                }
            }
            
            try
            {
                def summaryReport = readJSON file: 'summaryTestResults/summary_status.json'
                if (summaryReport.failed > 0 || summaryReport.error > 0)
                {
                    println("Some tests failed")
                    currentBuild.result="UNSTABLE"
                }
            }
            catch(e)
            {
                println(e.toString())
                println(e.getMessage())
                println("CAN'T GET TESTS STATUS")
                currentBuild.result="UNSTABLE"
            }

            try
            {
                options.testsStatus = readFile("summaryTestResults/slack_status.json")
            }
            catch(e)
            {
                println(e.toString())
                println(e.getMessage())
                options.testsStatus = ""
            } 

            publishHTML([allowMissing: false,
                         alwaysLinkToLastBuild: false,
                         keepAll: true,
                         reportDir: 'summaryTestResults',
                         reportFiles: 'summary_report.html, performance_report.html, compare_report.html',
                         // TODO: custom reportName (issues with escaping)
                         reportName: 'Test Report',
                         reportTitles: 'Summary Report, Performance Report, Compare Report'])

            if (options.sendToRBS)
            {
                try
                {
                    def response = httpRequest consoleLogResponseBody: true, httpMode: 'POST', authentication: '847a5a5d-700d-439b-ace1-518f415eb8d8',  url: 'https://rbsdbdev.cis.luxoft.com/api/login', validResponseCodes: '200'
                    println('Status: '+response.status)
                    println('Response: '+response.content)
                    def json_tok = readJSON text: "${response.content}"
                    
                    String branchName = env.BRANCH_NAME ?: options.projectBranch
                    if (branchName == "blender_2.7")
                    {
                        branchName = "master"
                    }
                    def testsList = options.testsList

                    String requestData = """{"name" : "${env.BUILD_NUMBER}", "branch": "${branchName}", "tool": "Blender", "status": "${currentBuild.result}"}"""
                    def response1 = httpRequest acceptType: 'APPLICATION_JSON', consoleLogResponseBody: true, contentType: 'APPLICATION_JSON', customHeaders: [[name: 'Authorization', value: "Token ${json_tok.token}"]], httpMode: 'POST', ignoreSslErrors: true, url: "https://rbsdbdev.cis.luxoft.com/report/end?data=${java.net.URLEncoder.encode(requestData, 'UTF-8')}", validResponseCodes: '200'
                    echo "Status: ${response1.status}\nContent: ${response1.content}"
                }
                catch(e)
                {
                    println(e.getMessage())
                }     
            }
        }
    }
    catch(e)
    {
        println(e.toString());
        println(e.getMessage());
        throw e
    }
}

def call(String projectBranch = "",
    String thirdpartyBranch = "master",
    String packageBranch = "blender_2.7",
    String testsBranch = "master",
    String platforms = 'Windows:AMD_RXVEGA,AMD_WX9100,AMD_WX7100,NVIDIA_GF1080TI;Ubuntu:AMD_WX7100;OSX:AMD_WX9100',
    Boolean updateRefs = false,
    Boolean enableNotifications = true,
    Boolean incrementVersion = true,
    Boolean skipBuild = false,
    String renderDevice = "gpu",
    String testsPackage = "",
    String tests = "",
    Boolean forceBuild = false,
    Boolean splitTestsExectuion = true,
    Boolean sendToRBS = false)
{
    try
    {
        // if build doesn't contain tests - keep this build forever
        if (tests == "" && testsPackage == "none") { currentBuild.setKeepLog(true) }
        String PRJ_NAME="RadeonProRenderBlenderPlugin"
        String PRJ_ROOT="rpr-plugins"

        gpusCount = 0
        platforms.split(';').each()
        { platform ->
            List tokens = platform.tokenize(':')
            if (tokens.size() > 1)
            {
                gpuNames = tokens.get(1)
                gpuNames.split(',').each()
                {
                    gpusCount += 1
                }
            }
        }

        multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy, 
                               [projectBranch:projectBranch, 
                                thirdpartyBranch:thirdpartyBranch, 
                                packageBranch:packageBranch, 
                                testsBranch:testsBranch, 
                                updateRefs:updateRefs, 
                                enableNotifications:enableNotifications,
                                PRJ_NAME:PRJ_NAME,
                                PRJ_ROOT:PRJ_ROOT,
                                incrementVersion:incrementVersion,
                                skipBuild:skipBuild,
                                renderDevice:renderDevice,
                                testsPackage:testsPackage,
                                tests:tests,
                                forceBuild:forceBuild,
                                reportName:'Test_20Report',
                                splitTestsExectuion:splitTestsExectuion,
                                TEST_TIMEOUT:540,
                                sendToRBS:sendToRBS,
                                gpusCount:gpusCount])
    }
    catch(e)
    {
        currentBuild.result = "INIT FAILED"
        options.failureMessage = "INIT FAILED"
        options.failureError = e.getMessage()
        println(e.toString());
        println(e.getMessage());
        
        throw e
    }
}
