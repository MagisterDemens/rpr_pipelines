import groovy.json.JsonSlurper


def getBlenderAddonInstaller(String osName, Map options) {

    switch(osName) {
        case 'Windows':

            if (options['isPreBuilt']) {
                if (options.pluginWinSha) {
                    addon_name = options.pluginWinSha
                } else {
                    addon_name = "unknown"
                }
            } else {
                addon_name = "${options.commitSHA}_Windows"
            }

            if (!fileExists("${CIS_TOOLS}/../PluginsBinaries/${addon_name}.zip")) {

                clearBinariesWin()

                if (options['isPreBuilt']) {
                    println "[INFO] The plugin does not exist in the storage. Downloading and copying..."
                    downloadPlugin(osName, "Blender", options)
                    addon_name = options.pluginWinSha
                } else {
                    println "[INFO] The plugin does not exist in the storage. Copying artifact..."
                    copyArtifacts(filter: "${options.WindowsPluginName}.zip", fingerprintArtifacts: false, projectName: "${options.masterJobName}", selector: specific("${options.masterBuildNumber}"))
                }

                bat """
                    IF NOT EXIST "${CIS_TOOLS}\\..\\PluginsBinaries" mkdir "${CIS_TOOLS}\\..\\PluginsBinaries"
                    move RadeonProRender*.zip "${CIS_TOOLS}\\..\\PluginsBinaries\\${addon_name}.zip"
                """

            } else {
                println "[INFO] The plugin ${addon_name}.zip exists in the storage."
            }

            break;

        case "OSX":

            if (options['isPreBuilt']) {
                if (options.pluginOSXSha) {
                    addon_name = options.pluginOSXSha
                } else {
                    addon_name = "unknown"
                }
            } else {
                addon_name = "${options.commitSHA}_OSX"
            }

            if(!fileExists("${CIS_TOOLS}/../PluginsBinaries/${addon_name}.zip")) {
                clearBinariesUnix()

                if (options['isPreBuilt']) {
                    println "[INFO] The plugin does not exist in the storage. Downloading and copying..."
                    downloadPlugin(osName, "Blender", options)
                    addon_name = options.pluginOSXSha
                } else {
                    println "[INFO] The plugin does not exist in the storage. Copying artifact..."
                    copyArtifacts(filter: "${options.OSXPluginName}.zip", fingerprintArtifacts: true, projectName: "${options.masterJobName}", selector: specific("${options.masterBuildNumber}"))
                }

                sh """
                    mkdir -p "${CIS_TOOLS}/../PluginsBinaries"
                    mv RadeonProRenderBlender*.zip "${CIS_TOOLS}/../PluginsBinaries/${addon_name}.zip"
                """

            } else {
                println "[INFO] The plugin ${addon_name}.zip exists in the storage."
            }

            break;

        default:

            if (options['isPreBuilt']) {
                if (options.pluginUbuntuSha) {
                    addon_name = options.pluginUbuntuSha
                } else {
                    addon_name = "unknown"
                }
            } else {
                addon_name = "${options.commitSHA}_${osName}"
            }

            if(!fileExists("${CIS_TOOLS}/../PluginsBinaries/${addon_name}.zip"))
            {
                clearBinariesUnix()

                if (options['isPreBuilt']) {
                    println "[INFO] The prebuilt plugin does not exist in the storage. Downloading and copying..."
                    downloadPlugin(osName, "Blender", options)
                    addon_name = options.pluginUbuntuSha
                } else {
                    println "[INFO] The plugin does not exist in the storage. Copying artifact..."
                    copyArtifacts(filter: "${options.LinuxPluginName}.zip", fingerprintArtifacts: true, projectName: "${options.masterJobName}", selector: specific("${options.masterBuildNumber}"))
                }

                sh """
                    mkdir -p "${CIS_TOOLS}/../PluginsBinaries"
                     mv RadeonProRenderBlender*.zip "${CIS_TOOLS}/../PluginsBinaries/${addon_name}.zip"
                """

            } else {
                println "[INFO] The plugin ${addon_name}.zip exists in the storage."
            }
    }

}


def executeGenTestRefCommand(String osName, Map options) {
    try {
        //for update existing manifest file
        receiveFiles("${options.REF_PATH_PROFILE}/baseline_manifest.json", './Work/Baseline/')
    }
    catch(e) {
        println("baseline_manifest.json not found")
    }

    dir('scripts') {
        switch(osName) {
        case 'Windows':
            bat """
            make_results_baseline.bat
            """
            break;
        // OSX & Ubuntu18
        default:
            sh """
            ./make_results_baseline.sh
            """
        }
    }
}

def buildRenderCache(String osName, String toolVersion, String log_name) {
    dir("scripts") {
        switch(osName) {
            case 'Windows':
                bat "build_rpr_cache.bat ${toolVersion} >> ..\\${log_name}.cb.log  2>&1"
                break;
            default:
                sh "./build_rpr_cache.sh ${toolVersion} >> ../${log_name}.cb.log 2>&1"        
        }
    }
}

def executeTestCommand(String osName, Map options) {
    switch(osName) {
    case 'Windows':
        dir('scripts') {
            bat """
            run.bat ${options.renderDevice} ${options.testsPackage} \"${options.tests}\" ${options.resX} ${options.resY} ${options.SPU} ${options.iter} ${options.theshold} >> ..\\${options.stageName}.log  2>&1
            """
        }
        break;
    // OSX & Ubuntu18
    default:
        dir("scripts") {
            sh """
            ./run.sh ${options.renderDevice} ${options.testsPackage} \"${options.tests}\" ${options.resX} ${options.resY} ${options.SPU} ${options.iter} ${options.theshold} >> ../${options.stageName}.log 2>&1
            """
        }
    }
}


def executeTests(String osName, String asicName, Map options) {
    // used for mark archive results or not. It needed for not archiving failed tasks which will be retried.
    Boolean archiveResults = true

    try {

        timeout(time: "5", unit: 'MINUTES') {
            try {
                cleanWS(osName)
                checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_blender.git')
                println "[INFO] Preparing on ${env.NODE_NAME} successfully finished."

            } catch(e) {
                println("[ERROR] Failed to prepare test group on ${env.NODE_NAME}")
                println(e.toString())
                throw e
            }
        }

        downloadAssets("${options.PRJ_ROOT}/${options.ASSETS_NAME}/Blender2.8Assets/", 'Blender2.8Assets')

        if (!options['skipBuild']) {

            try {

                Boolean newPluginInstalled = false
                timeout(time: "12", unit: 'MINUTES') {
                    getBlenderAddonInstaller(osName, options)
                    newPluginInstalled = installBlenderAddon(osName, "2.82", options)
                    println "[INFO] Install function on ${env.NODE_NAME} return ${newPluginInstalled}"
                }

                if (newPluginInstalled) {
                    timeout(time: "3", unit: 'MINUTES') {
                        buildRenderCache(osName, "2.82", options.stageName)
                        if(!fileExists("./Work/Results/Blender28/cache_building.jpg")){
                            println "[ERROR] Failed to build cache on ${env.NODE_NAME}. No output image found."
                            throw new Exception("No output image")
                        }
                    }
                }
                
            } catch(e) {
                println("[ERROR] Failed to install plugin on ${env.NODE_NAME}")
                println(e.toString())
                // deinstalling broken addon
                installBlenderAddon(osName, "2.82", options, false, true)
                throw e
            }
        }

        String REF_PATH_PROFILE="${options.REF_PATH}/${asicName}-${osName}"
        String JOB_PATH_PROFILE="${options.JOB_PATH}/${asicName}-${osName}"

        options.REF_PATH_PROFILE = REF_PATH_PROFILE

        outputEnvironmentInfo(osName, options.stageName)

        if (options['updateRefs']) {
            executeTestCommand(osName, options)
            executeGenTestRefCommand(osName, options)
            sendFiles('./Work/Baseline/', REF_PATH_PROFILE)
        } else {
            // TODO: receivebaseline for json suite
            try {
                println "[INFO] Downloading reference images for ${options.tests}"
                receiveFiles("${REF_PATH_PROFILE}/baseline_manifest.json", './Work/Baseline/')
                options.tests.split(" ").each() {
                    receiveFiles("${REF_PATH_PROFILE}/${it}", './Work/Baseline/')
                }
            } catch (e) {
                println("[WARNING] Baseline doesn't exist.")
            }
            executeTestCommand(osName, options)
        }

    } catch (e) {
        if (options.currentTry < options.nodeReallocateTries) {
            archiveResults = false
        } 
        println(e.toString())
        println(e.getMessage())
        options.failureMessage = "Failed during testing: ${asicName}-${osName}"
        options.failureError = e.getMessage()
        throw e
    } finally {
        archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
        if (archiveResults) {
            dir('Work') {
                if (fileExists("Results/Blender28/session_report.json")) {
                    
                    def sessionReport = null
                    sessionReport = readJSON file: 'Results/Blender28/session_report.json'

                    // if none launched tests - mark build failed
                    if (sessionReport.summary.total == 0) {
                        options.failureMessage = "Noone test was finished for: ${asicName}-${osName}"
                        currentBuild.result = "FAILED"
                    }

                    // deinstalling broken addon
                    if (sessionReport.summary.total == sessionReport.summary.error) {
                        installBlenderAddon(osName, "2.82", options, false, true)
                    }               
                }
            }
            echo "Archive test results to: ${options.testResultsName}"
            zip(archive: true, dir: 'Work', zipFile: "${options.testResultsName}.zip")
        } else {
            println "[INFO] Task ${options.tests} on ${options.nodeLabels} labels will be retried."
        }
    }
}


def call(String testsBranch = "master",
    String asicName,
    String osName,
    String testName,
    String jsonOptions) {

    try {
        // parse converted options
        Map options = [:] << new JsonSlurper().parseText(jsonOptions)
        options.masterEnv = [:] << options.masterEnv

        tests_launch_pipeline(this.&executeTests, asicName, osName, testName, options)
    } catch(e) {
        currentBuild.result = "FAILED"
        failureMessage = "INIT FAILED"
        failureError = e.getMessage()
        println(e.toString());
        println(e.getMessage());

        throw e
    }
}