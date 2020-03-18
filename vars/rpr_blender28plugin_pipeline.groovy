import RBSProduction
import RBSDevelopment


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
            echo "Error while deinstall plugin"
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
            msiexec /i "${options.pluginWinSha}.msi" /quiet /qn BLENDER_282_INSTALL_FOLDER="C:\\Program Files\\Blender Foundation\\Blender 2.82" /L+ie ../../${options.stageName}.install.log /norestart
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
                echo bpy.ops.preferences.addon_install(filepath=addon_path) >> registerRPRinBlender.py
                echo bpy.ops.preferences.addon_enable(module="rprblender") >> registerRPRinBlender.py
                echo bpy.ops.wm.save_userpref() >> registerRPRinBlender.py

                "C:\\Program Files\\Blender Foundation\\Blender 2.82\\blender.exe" -b -P registerRPRinBlender.py >>../../${options.stageName}.install.log 2>&1
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
        // TODO: implement matlib install
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
                /home/user/.local/share/rprblender/uninstall.py /home/user/Desktop/Blender2.82/ >>../../${options.stageName}.uninstall.log 2>&1
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
            printf "y\nq\n\ny\ny\n" > input.txt
            exec 0<input.txt
            exec &>${env.WORKSPACE}/${options.stageName}.install.log
            ${CIS_TOOLS}/../PluginsBinaries/${options.pluginUbuntuSha}.run --nox11 --noprogress ~/Desktop/Blender2.82 >> ../../${options.stageName}.install.log 2>&1
            """

            // install matlib
            receiveFiles("/bin_storage/RadeonProRenderMaterialLibraryInstaller_2.0.run", "${CIS_TOOLS}/../TestResources/")
            
            sh """
            #!/bin/bash
            ${CIS_TOOLS}/../TestResources/RadeonProRenderMaterialLibraryInstaller_2.0.run --nox11 -- --just-do-it >> ../../${options.stageName}.matlib.install.log 2>&1
            """
        }
    }
}

def buildRenderCache(String osName)
{
    switch(osName)
    {
        case 'Windows':
            // FIX: relative path to blender.exe
            bat '"C:\\Program Files\\Blender Foundation\\Blender 2.82\\blender.exe" -b -E RPR -f 0'
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
    if (!options['skipBuild']) {
        try {
            timeout(time: "30", unit: 'MINUTES') {
                installPlugin(osName, options)
            }
            timeout(time: "3", unit: 'MINUTES') {
                buildRenderCache(osName)
            }
        }
        catch(e) {
            println(e.toString())
            println("ERROR during plugin installation or cache building")
        }
    }

    switch(osName)
    {
    case 'Windows':
        dir('scripts')
        {
            bat """
            run.bat ${options.renderDevice} ${options.testsPackage} \"${options.tests}\" ${options.resX} ${options.resY} ${options.SPU} ${options.iter} ${options.theshold} >> ..\\${options.stageName}.log  2>&1
            """
        }
        break;
    case 'OSX':
        dir("scripts")
        {
            sh """
            ./run.sh ${options.renderDevice} ${options.testsPackage} \"${options.tests}\" ${options.resX} ${options.resY} ${options.SPU} ${options.iter} ${options.theshold} >> ../${options.stageName}.log 2>&1
            """
        }
        break;
    default:
        dir("scripts")
        {
            sh """
            ./run.sh ${options.renderDevice} ${options.testsPackage} \"${options.tests}\" ${options.resX} ${options.resY} ${options.SPU} ${options.iter} ${options.theshold} >> ../${options.stageName}.log 2>&1
            """
        }
    }
}

def executeTests(String osName, String asicName, Map options)
{
    cleanWs(deleteDirs: true, disableDeferredWipeout: true)
    try {
        checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_blender.git')

        // setTester in rbs
        if (options.sendToRBS) {
            options.rbs_prod.setTester(options)
            options.rbs_dev.setTester(options)
        }

        downloadAssets("${options.PRJ_ROOT}/${options.PRJ_NAME}/Blender2.8Assets/", 'Blender2.8Assets')

        String REF_PATH_PROFILE="${options.REF_PATH}/${asicName}-${osName}"
        String JOB_PATH_PROFILE="${options.JOB_PATH}/${asicName}-${osName}"

        options.REF_PATH_PROFILE = REF_PATH_PROFILE

        outputEnvironmentInfo(osName, options.stageName)

        if(options['updateRefs'])
        {
            executeGenTestRefCommand(osName, options)
            sendFiles('./Work/Baseline/', REF_PATH_PROFILE)
        }
        else{
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
        if (!options.splitTestsExecution) {
            currentBuild.result = "FAILED"
            throw e
        }
    }
    finally {
        archiveArtifacts "*.log"
        echo "Stashing test results to : ${options.testResultsName}"
        dir('Work')
        {
            stash includes: '**/*', name: "${options.testResultsName}", allowEmpty: true

            try {
                def sessionReport = readJSON file: 'Results/Blender28/session_report.json'
                // if none launched tests - mark build failed
                if (sessionReport.summary.total == 0) {
                    options.failureMessage = "Noone test was finished for: ${asicName}-${osName}"
                    currentBuild.result = "FAILED"
                }

                if (options.sendToRBS)
                {
                    options.rbs_prod.sendSuiteResult(sessionReport, options)
                    options.rbs_dev.sendSuiteResult(sessionReport, options)
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
    dir('RadeonProRenderBlenderAddon\\BlenderPkg')
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
        bat 'rename addon.zip addon_Win.zip'

        archiveArtifacts "RadeonProRender*.msi"
        String BUILD_NAME = branch_postfix ? "RadeonProRenderBlender_${options.pluginVersion}.(${branch_postfix}).msi" : "RadeonProRenderBlender_${options.pluginVersion}.msi"
        rtp nullAction: '1', parserName: 'HTML', stableText: """<h3><a href="${BUILD_URL}/artifact/${BUILD_NAME}">[BUILD: ${BUILD_ID}] ${BUILD_NAME}</a></h3>"""
        archiveArtifacts "addon_Win.zip"

        bat '''
        for /r %%i in (RadeonProRender*.msi) do copy %%i RadeonProRenderBlender.msi
        '''

        stash includes: 'RadeonProRenderBlender.msi', name: 'appWindows'
        options.pluginWinSha = sha1 'RadeonProRenderBlender.msi'
    }
}

def executeBuildOSX(Map options)
{
    dir('RadeonProRenderBlenderAddon')
    {
        sh """
        ./build_osx.sh /usr/bin/castxml >> ../${STAGE_NAME}.log  2>&1
        """
    }
    
    dir('RadeonProRenderBlenderAddon/BlenderPkg')
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
            sh 'cp ./dist/Blender/addon/addon.zip ./addon_OSX.zip'

            archiveArtifacts "RadeonProRender*.dmg"
            String BUILD_NAME = branch_postfix ? "RadeonProRenderBlender_${options.pluginVersion}.(${branch_postfix}).dmg" : "RadeonProRenderBlender_${options.pluginVersion}.dmg"
            rtp nullAction: '1', parserName: 'HTML', stableText: """<h3><a href="${BUILD_URL}/artifact/${BUILD_NAME}">[BUILD: ${BUILD_ID}] ${BUILD_NAME}</a></h3>"""
            archiveArtifacts "addon_OSX.zip"
        }
        stash includes: 'RadeonProRenderBlender.dmg', name: "appOSX"
        options.pluginOSXSha = sha1 'RadeonProRenderBlender.dmg'
    }
}

def executeBuildLinux(Map options, String osName)
{
    dir('RadeonProRenderBlenderAddon')
    {
        sh """
        ./build.sh /usr/bin/castxml >> ../${STAGE_NAME}.log  2>&1
        """
    }
    dir('RadeonProRenderBlenderAddon/BlenderPkg')
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
            sh 'cp ./dist/addon/addon.zip ./addon_Ubuntu.zip'

            archiveArtifacts "RadeonProRender*.run"
            String BUILD_NAME = branch_postfix ? "RadeonProRenderForBlender_${options.pluginVersion}.(${branch_postfix}).run" : "RadeonProRenderForBlender_${options.pluginVersion}.run"
            rtp nullAction: '1', parserName: 'HTML', stableText: """<h3><a href="${BUILD_URL}/artifact/${BUILD_NAME}">[BUILD: ${BUILD_ID}] ${BUILD_NAME}</a></h3>"""
            archiveArtifacts "addon_Ubuntu.zip"

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
        dir('RadeonProRenderPkgPlugin')
        {
            deleteDir()
        }

        switch(osName)
        {
            case 'Windows':
                outputEnvironmentInfo(osName)
                executeBuildWindows(options);
                break;
            case 'OSX':
                if(!fileExists("python3"))
                {
                    sh "ln -s /usr/local/bin/python3.7 python3"
                }
                withEnv(["PATH=$WORKSPACE:$PATH"])
                {
                    outputEnvironmentInfo(osName);
                    executeBuildOSX(options);
                 }
                break;
            default:
                if(!fileExists("python3"))
                {
                    sh "ln -s /usr/bin/python3.7 python3"
                }
                withEnv(["PATH=$PWD:$PATH"])
                {
                    outputEnvironmentInfo(osName);
                    executeBuildLinux(options, osName);
                }
        }
    }
    catch (e) {
        options.failureMessage = "Error during build ${osName}"
        options.failureError = e.getMessage()
        currentBuild.result = "FAILED"
        if (options.sendToRBS)
        {
            try {
                options.rbs_prod.setFailureStatus()
                options.rbs_dev.setFailureStatus()
            } catch (err) {
                println(err)
            }
        }
        throw e
    }
    finally {
        archiveArtifacts "*.log"
    }
}

def executePreBuild(Map options)
{
    currentBuild.description = ""
    ['projectBranch'].each
    {
        if(options[it] != 'master' && options[it] != "")
        {
            currentBuild.description += "<b>${it}:</b> ${options[it]}<br/>"
        }
    }

    dir('RadeonProRenderBlenderAddon')
    {
        checkOutBranchOrScm(options['projectBranch'], 'git@github.com:Radeon-Pro/RadeonProRenderBlenderAddon.git', true)

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
            if("${BRANCH_NAME}" == "master" && "${AUTHOR_NAME}" != "radeonprorender")
            {
                options.testsPackage = "master"
                echo "Incrementing version of change made by ${AUTHOR_NAME}."

                String currentversion=version_read("${env.WORKSPACE}\\RadeonProRenderBlenderAddon\\src\\rprblender\\__init__.py", '"version": (', ', ')
                echo "currentversion ${currentversion}"

                new_version=version_inc(currentversion, 3, ', ')
                echo "new_version ${new_version}"

                version_write("${env.WORKSPACE}\\RadeonProRenderBlenderAddon\\src\\rprblender\\__init__.py", '"version": (', new_version, ', ')

                String updatedversion=version_read("${env.WORKSPACE}\\RadeonProRenderBlenderAddon\\src\\rprblender\\__init__.py", '"version": (', ', ', "true")
                echo "updatedversion ${updatedversion}"

                bat """
                    git add src/rprblender/__init__.py
                    git commit -m "buildmaster: version update to ${updatedversion}"
                    git push origin HEAD:master
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

                if("${BRANCH_NAME}" == "master")
                {
                   echo "rebuild master"
                   options['executeBuild'] = true
                   options['executeTests'] = true
                   options.testsPackage = "master"
                }
            }
        }
        options.pluginVersion = version_read("${env.WORKSPACE}\\RadeonProRenderBlenderAddon\\src\\rprblender\\__init__.py", '"version": (', ', ').replace(', ', '.')
    }
    if(env.CHANGE_URL)
    {
        //TODO: fix sha for PR
        //options.comitSHA = bat ( script: "git log --format=%%H HEAD~1 -1", returnStdout: true ).split('\r\n')[2].trim()
        options.AUTHOR_NAME = env.CHANGE_AUTHOR_DISPLAY_NAME
        if (env.CHANGE_TARGET != 'master') {
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

    if (env.BRANCH_NAME && env.BRANCH_NAME == "master") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '25']]]);
    } else if (env.BRANCH_NAME && BRANCH_NAME != "master") {
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

    
    def tests = []
    options.groupsRBS = []
    if(options.testsPackage != "none")
    {
        dir('jobs_test_blender')
        {
            checkOutBranchOrScm(options['testsBranch'], 'https://github.com/luxteam/jobs_test_blender.git')
            // json means custom test suite. Split doesn't supported
            if(options.testsPackage.endsWith('.json'))
            {
                def testsByJson = readJSON file: "jobs/${options.testsPackage}"
                testsByJson.each() {
                    options.groupsRBS << "${it.key}"
                }
                options.splitTestsExecution = false
            }
            else {
                // options.splitTestsExecution = false
                String tempTests = readFile("jobs/${options.testsPackage}")
                tempTests.split("\n").each {
                    // TODO: fix: duck tape - error with line ending
                    tests << "${it.replaceAll("[^a-zA-Z0-9_]+","")}"
                }
                options.tests = tests
                options.testsPackage = "none"
                options.groupsRBS = tests
            }
        }
    }
    else {
        options.tests.split(" ").each()
        {
            tests << "${it}"
        }
        options.tests = tests
        options.groupsRBS = tests
    }

    if(options.splitTestsExecution) {
        options.testsList = options.tests
    }
    else {
        options.testsList = ['']
        options.tests = tests.join(" ")
    }

    if (options.sendToRBS)
    {
        try
        {
            options.rbs_prod.startBuild(options)
            options.rbs_dev.startBuild(options)
        }
        catch (e)
        {
            println(e.toString())
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

            String branchName = env.BRANCH_NAME ?: options.projectBranch
            try {
                withEnv(["JOB_STARTED_TIME=${options.JOB_STARTED_TIME}"])
                {
                    dir("jobs_launcher") {
                        bat """
                        build_reports.bat ..\\summaryTestResults "${escapeCharsByUnicode('Blender 2.82')}" ${options.commitSHA} ${branchName} \"${escapeCharsByUnicode(options.commitMessage)}\"
                        """
                    }
                }
            } catch(e) {
                println("ERROR during report building")
                println(e.toString())
                println(e.getMessage())
            }

            try
            {
                dir("jobs_launcher") {
                    bat "get_status.bat ..\\summaryTestResults"
                }
            }
            catch(e)
            {
                println("ERROR during slack status generation")
                println(e.toString())
                println(e.getMessage())
            }

            try
            {
                def summaryReport = readJSON file: 'summaryTestResults/summary_status.json'
                if (summaryReport.error > 0) {
                    println("Some tests crashed")
                    currentBuild.result="FAILED"
                }
                else if (summaryReport.failed > 0) {
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

            if (options.sendToRBS) {
                try {
                    String status = currentBuild.result ?: 'SUCCESSFUL'
                    options.rbs_prod.finishBuild(options, status)
                    options.rbs_dev.finishBuild(options, status)
                }
                catch (e){
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
    String testsBranch = "master",
    String platforms = 'Windows:AMD_RXVEGA,AMD_WX9100,AMD_WX7100,NVIDIA_GF1080TI;Ubuntu18:AMD_RadeonVII;OSX:AMD_RXVEGA',
    Boolean updateRefs = false,
    Boolean enableNotifications = true,
    Boolean incrementVersion = true,
    Boolean skipBuild = false,
    String renderDevice = "gpu",
    String testsPackage = "",
    String tests = "",
    Boolean forceBuild = false,
    Boolean splitTestsExecution = false,
    Boolean sendToRBS = true,
    String resX = '0',
    String resY = '0',
    String SPU = '25',
    String iter = '50',
    String theshold = '0.05')
{
    resX = (resX == 'Default') ? '0' : resX
    resY = (resY == 'Default') ? '0' : resY
    SPU = (SPU == 'Default') ? '25' : SPU
    iter = (iter == 'Default') ? '50' : iter
    theshold = (theshold == 'Default') ? '0.05' : theshold
    try
    {
        String PRJ_NAME="RadeonProRenderBlender2.8Plugin"
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

        rbs_prod = new RBSProduction(this, "Blender28", env.JOB_NAME, env)
        rbs_dev = new RBSDevelopment(this, "Blender28", env.JOB_NAME, env)

        multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy,
                               [projectBranch:projectBranch,
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
                                splitTestsExecution:splitTestsExecution,
                                sendToRBS: sendToRBS,
                                gpusCount:gpusCount,
                                TEST_TIMEOUT:660,
                                DEPLOY_TIMEOUT:150,
                                TESTER_TAG:"Blender2.8",
                                BUILDER_TAG:"BuildBlender2.8",
                                rbs_dev: rbs_dev,
                                rbs_prod: rbs_prod,
                                resX: resX,
                                resY: resY,
                                SPU: SPU,
                                iter: iter,
                                theshold: theshold
                                ])
    }
    catch(e)
    {
        currentBuild.result = "FAILED"
        failureMessage = "INIT FAILED"
        failureError = e.getMessage()
        println(e.toString());
        println(e.getMessage());

        throw e
    }
}
