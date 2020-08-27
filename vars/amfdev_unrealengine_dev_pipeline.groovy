def executeTests(String osName, String asicName, Map options)
{
    unstash "UEWindowsTests"
}

def getPreparedUE(String version, String pluginType, Boolean forceDownloadUE) {
    String targetFolderPath = "${CIS_TOOLS}\\..\\PreparedUE\\UE-${version}"
    String folderName = pluginType == "Standard" ? "UE-${version}" : "UE-${version}-${pluginType}"
    if (!fileExists(targetFolderPath) || forceDownloadUE) {
        println("[INFO] UnrealEngine will be downloaded and configured")
        bat """
            Build.bat Engine ${version} PrepareUE Development >> ..\\PrepareUE.${version}.log 2>&1
        """

        println("[INFO] Prepared UE is ready. Saving it for use in future builds...")
        bat """
            xcopy /s/y/i UE-${version} ${targetFolderPath} >> nul

            rename UE-${version} ${folderName}
        """
    } else {
        println("[INFO] Prepared UnrealEngine found. Copying it...")
        dir(folderName) {
            bat """
                xcopy /s/y/i ${targetFolderPath} . >> nul
            """
        }
    }
}


def generateBuildNameWindows(String ue_version, String build_conf, String vs_ver, String graphics_api) {
    return "${ue_version}_${build_conf}_vs${vs_ver}_${graphics_api}"
}


def executeBuildWindows(Map options)
{
    options.versions.each() { ue_version ->
        options.configurations.each() { build_conf ->
            options.visualStudioVersions.each() { vs_ver ->
                options.graphicsAPI.each() { graphics_api ->

                    println "Current UnrealEngine version: ${ue_version}."
                    println "Current configuration: ${build_conf}."
                    println "Current VS version: ${vs_ver}."
                    println "Current graphics API: ${graphics_api}."

                    win_build_name = generateBuildNameWindows(ue_version, build_conf, vs_ver, graphics_api)

                    try {
                        dir('U\\integration')
                        {
                            getPreparedUE(options['version'], options['pluginType'], options['forceDownloadUE'])
                            bat """
                                Build.bat ${options.targets.join(' ')} ${ue_version} ${options.pluginType} ${build_conf} ${options.testsVariant} ${options.testsName.join(' ')} ${vs_ver} ${graphics_api} ${options.source} Dirty >> ..\\${STAGE_NAME}.${win_build_name}.log 2>&1
                            """

                            if (options.targets.contains("Tests")) {
                                dir ("Deploy\\Tests") {
                                    bat """
                                        rename ${ue_version} ${win_build_name}
                                    """
                                }
                                dir ("Deploy\\Prerequirements") {
                                    bat """
                                        rename ${ue_version} ${win_build_name}
                                    """
                                }
                            }
                        }
                    } catch (FlowInterruptedException e) {
                        println "[INFO] Job was aborted during build stage"
                        throw e
                    } catch (e) {
                        println(e.toString());
                        println(e.getMessage());
                        currentBuild.result = "FAILURE"
                        println "[ERROR] Failed to build UE on Windows"
                    } finally {
                        bat """
                            if exist ${win_build_name} rmdir /Q /S ${win_build_name}
                        """
                    }
                }
            }
        }
    }
    if (options.targets.contains("Tests")) {
        dir ("U\\integration") {
            if (fileExists("Deploy\\Tests")) {
                zip archive: true, dir: "Deploy", glob: '', zipFile: "WindowsTests.zip"

                stash includes: "WindowsTests.zip", name: "UEWindowsTests"
            } else {
                println "[ERROR] Can't find folder with tests!"
                currentBuild.result = "FAILURE"
            }
        }
    }
}

def executeBuild(String osName, Map options)
{
    try {        
        dir('U')
        {
            checkOutBranchOrScm(options['projectBranch'], 'git@github.com:luxteam/UnrealEngine_dev.git')
        }
        
        outputEnvironmentInfo(osName)

        switch(osName)
        {
        case 'Windows': 
            executeBuildWindows(options); 
            break;
        case 'OSX':
            println("[WARNING] OSX is not supported")
            break;
        default: 
            println("[WARNING] ${osName} is not supported")
        }
    }
    catch (e) {
        throw e
    }
    finally {
        archiveArtifacts artifacts: "U/*.log", allowEmptyArchive: true
        archiveArtifacts "U/integration/Logs/**/*.*"
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
}

def executeDeploy(Map options, List platformList, List testResultList)
{
    // TODO: implement deploy stage
}


def call(String projectBranch = "",
         String platforms = 'Windows',
         String targets = '',
         String versions = '',
         String pluginType = '',
         String configurations = '',
         String testsVariant = '',
         String testsName = '',
         String visualStudioVersions = '',
         String graphicsAPI = '',
         String pluginRepository = '',
         Boolean forceDownloadUE = false,
         Boolean enableNotifications = false) {
    try
    {
        String PRJ_NAME="UE"
        String PRJ_ROOT="gpuopen"

        targets = targets.split(',')
        versions = versions.split(',')
        configurations = configurations.split(',')
        testsName = testsName.split(',')
        visualStudioVersions = visualStudioVersions.split(',')
        graphicsAPI = graphicsAPI.split(',')

        println "Targets: ${targets}"
        println "Versions: ${versions}"
        println "Plugin type: ${pluginType}"
        println "Configuration: ${configurations}"
        println "Tests variant: ${testsVariant}"
        println "Tests name: ${testsName}"
        println "Visual Studio version: ${visualStudioVersions}"
        println "Graphics API: ${graphicsAPI}"

        for (int i = 0; i < graphicsAPI.length; i++) {
            // DX11 is selected if 'Vulkan' value isn't specified. There isn't special key for DX11
            if (graphicsAPI[i].contains("DX11")) {
                graphicsAPI[i] = " "
                break
            }
        }
        if (pluginType == "Standard") {
            // set empty value for graphics api
            graphicsAPI = [" "]
        }

        String source = ""
        if (pluginRepository.contains("amfdev")) {
            source = "Clone"
        } else if (pluginRepository.contains("GPUOpen")) {
            source = "Origin"
        }

        multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy,
                               [projectBranch:projectBranch,
                                targets:targets,
                                version:version,
                                pluginType:pluginType,
                                configurations:configurations,
                                testsVariant:testsVariant,
                                testsName:testsName,
                                visualStudioVersions:visualStudioVersions,
                                graphicsAPI:graphicsAPI,
                                source:source,
                                forceDownloadUE:forceDownloadUE,
                                enableNotifications:enableNotifications,
                                PRJ_NAME:PRJ_NAME,
                                PRJ_ROOT:PRJ_ROOT,
                                BUILDER_TAG:'BuilderU',
                                BUILD_TIMEOUT:240,
                                executeBuild:true,
                                executeTests:false])
    }
    catch(e) {
        currentBuild.result = "FAILED"
        println(e.toString());
        println(e.getMessage());
        throw e
    }
}
