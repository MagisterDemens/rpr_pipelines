import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

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
    if (!graphics_api.trim()) {
        graphics_api = "DX11"
    }
    return "${ue_version}_${build_conf}_vs${vs_ver}_${graphics_api}"
}


def executeBuildWindows(Map options)
{
    options.versions.each() { ue_version ->
        options.buildConfigurations.each() { build_conf ->
            options.visualStudioVersions.each() { vs_ver ->
                options.graphicsAPI.each() { graphics_api ->

                    println "Current UnrealEngine version: ${ue_version}."
                    println "Current build configuration: ${build_conf}."
                    println "Current VS version: ${vs_ver}."
                    println "Current graphics API: ${graphics_api}."

                    win_build_name = generateBuildNameWindows(ue_version, build_conf, vs_ver, graphics_api)

                    if (graphics_api == "DX11") {
                        graphics_api = " "
                    }

                    try {
                        dir("U\\integration")
                        {
                            getPreparedUE(ue_version, options['pluginType'], options['forceDownloadUE'])
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
                        String folderName = options['pluginType'] == "Standard" ? "UE-${version}" : "UE-${version}-${options.pluginType}"
                        dir("U\\integration") {
                            bat """
                                if exist ${folderName} rmdir /Q /S ${folderName}
                            """
                        }
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
    // manual job
    if (options.forceBuild) {
        options.executeBuild = true
        options.executeTests = true
    // auto job
    } else {
        // TODO: impelement initialization for auto jobs
    }

    if(!env.CHANGE_URL){

        currentBuild.description = ""
        ['projectBranch'].each
        {
            if(options[it] != 'master' && options[it] != "")
            {
                currentBuild.description += "<b>${it}:</b> ${options[it]}<br/>"
            }
        }

        checkOutBranchOrScm(options['projectBranch'], 'git@github.com:luxteam/UnrealEngine_dev.git', true)

        options.commitAuthor = bat (script: "git show -s --format=%%an HEAD ",returnStdout: true).split('\r\n')[2].trim()
        options.commitMessage = bat (script: "git log --format=%%B -n 1", returnStdout: true).split('\r\n')[2].trim()
        options.commitSHA = bat(script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()

        println "The last commit was written by ${options.commitAuthor}."
        println "Commit message: ${options.commitMessage}"
        println "Commit SHA: ${options.commitSHA}"

        currentBuild.description += "<b>Commit author:</b> ${options.commitAuthor}<br/>"
        currentBuild.description += "<b>Commit message:</b> ${options.commitMessage}<br/>"
        currentBuild.description += "<b>Commit SHA:</b> ${options.commitSHA}<br/>"
        
        if (options.incrementVersion) {
            // TODO implement incrementing of version 
        }
    }

    if (env.BRANCH_NAME && env.BRANCH_NAME == "master") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '25']]]);
    } else if (env.BRANCH_NAME && BRANCH_NAME != "master") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '25']]]);
    } else if (env.CHANGE_URL ) {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10']]]);
    } else {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '20']]]);
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
         String buildConfigurations = '',
         String testsVariant = '',
         String testsName = '',
         String visualStudioVersions = '',
         String graphicsAPI = '',
         String pluginRepository = '',
         Boolean forceDownloadUE = false,
         Boolean forceBuild = false,
         Boolean enableNotifications = false) {
    try
    {
        String PRJ_NAME="UE"
        String PRJ_ROOT="gpuopen"

        targets = targets.split(', ')
        versions = versions.split(',')
        buildConfigurations = buildConfigurations.split(',')
        testsName = testsName.split(',')
        visualStudioVersions = visualStudioVersions.split(',')
        graphicsAPI = graphicsAPI.split(',')

        println "Targets: ${targets}"
        println "Versions: ${versions}"
        println "Plugin type: ${pluginType}"
        println "Build configurations: ${buildConfigurations}"
        println "Tests variant: ${testsVariant}"
        println "Tests name: ${testsName}"
        println "Visual Studio version: ${visualStudioVersions}"
        println "Graphics API: ${graphicsAPI}"

        String source = ""
        if (pluginRepository.contains("amfdev")) {
            source = "Clone"
        } else if (pluginRepository.contains("GPUOpen")) {
            source = "Origin"
        }

        multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy,
                               [projectBranch:projectBranch,
                                targets:targets,
                                versions:versions,
                                pluginType:pluginType,
                                buildConfigurations:buildConfigurations,
                                testsVariant:testsVariant,
                                testsName:testsName,
                                visualStudioVersions:visualStudioVersions,
                                graphicsAPI:graphicsAPI,
                                source:source,
                                forceDownloadUE:forceDownloadUE,
                                forceBuild:forceBuild,
                                enableNotifications:enableNotifications,
                                PRJ_NAME:PRJ_NAME,
                                PRJ_ROOT:PRJ_ROOT,
                                BUILDER_TAG:'BuilderU',
                                BUILD_TIMEOUT:240])
    }
    catch(e) {
        currentBuild.result = "FAILED"
        println(e.toString());
        println(e.getMessage());
        throw e
    }
}
