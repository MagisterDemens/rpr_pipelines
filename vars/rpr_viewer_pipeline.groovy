import UniverseClient
import groovy.transform.Field

@Field UniverseClient universeClient = new UniverseClient(this, "https://umsapi.cis.luxoft.com", env, "https://imgs.cis.luxoft.com", "AMD%20Radeon™%20ProRender%20Viewer")
import groovy.json.JsonOutput;

def getViewerTool(String osName, Map options)
{
    switch(osName)
    {
        case 'Windows':

            if (!fileExists("${CIS_TOOLS}\\..\\PluginsBinaries\\${options.pluginWinSha}.zip")) {

                clearBinariesWin()

                println "[INFO] The plugin does not exist in the storage. Unstashing and copying..."
                unstash "appWindows"
                
                bat """
                    IF NOT EXIST "${CIS_TOOLS}\\..\\PluginsBinaries" mkdir "${CIS_TOOLS}\\..\\PluginsBinaries"
                    copy RprViewer_Windows.zip "${CIS_TOOLS}\\..\\PluginsBinaries\\${options.pluginWinSha}.zip"
                """

            } else {
                println "[INFO] The plugin ${options.pluginWinSha}.zip exists in the storage."
                bat """
                    copy "${CIS_TOOLS}\\..\\PluginsBinaries\\${options.pluginWinSha}.zip" RprViewer_Windows.zip
                """
            }

            unzip zipFile: "RprViewer_Windows.zip", dir: "RprViewer", quiet: true

            break;

        case 'OSX':
            println "OSX isn't supported"
            break;

        default:
            
            if (!fileExists("${CIS_TOOLS}/../PluginsBinaries/${options.pluginUbuntuSha}.zip")) {

                clearBinariesUnix()

                println "[INFO] The plugin does not exist in the storage. Unstashing and copying..."
                unstash "appUbuntu18"
                
                sh """
                    mkdir -p "${CIS_TOOLS}/../PluginsBinaries"
                    cp RprViewer_Ubuntu18.zip "${CIS_TOOLS}/../PluginsBinaries/${options.pluginUbuntuSha}.zip"
                """ 

            } else {

                println "[INFO] The plugin ${options.pluginUbuntuSha}.zip exists in the storage."
                sh """
                    cp "${CIS_TOOLS}/../PluginsBinaries/${options.pluginUbuntuSha}.zip" RprViewer_Ubuntu18.zip
                """
            }

            unzip zipFile: "RprViewer_Ubuntu18.zip", dir: "RprViewer", quiet: true

            break;
    }
}


def executeGenTestRefCommand(String osName, Map options)
{
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

def executeTestCommand(String osName, String asicName, Map options)
{
    def test_timeout
    if (options.testsPackage.endsWith('.json')) 
    {
        test_timeout = options.timeouts["${options.testsPackage}"]
    } 
    else
    {
        test_timeout = options.timeouts["${options.tests}"]
    }

    println "Set timeout to ${test_timeout}"

    timeout(time: test_timeout, unit: 'MINUTES') { 

        build_id = "none"
        job_id = "none"
        if (options.sendToUMS && universeClient.build != null){
            build_id = universeClient.build["id"]
            job_id = universeClient.build["job_id"]
        }
        withCredentials([usernamePassword(credentialsId: 'image_service', usernameVariable: 'IS_USER', passwordVariable: 'IS_PASSWORD'),
            usernamePassword(credentialsId: 'universeMonitoringSystem', usernameVariable: 'UMS_USER', passwordVariable: 'UMS_PASSWORD')])
        {
            withEnv(["UMS_USE=${options.sendToUMS}", "UMS_BUILD_ID=${build_id}", "UMS_JOB_ID=${job_id}",
                "UMS_URL=${universeClient.url}", "UMS_ENV_LABEL=${osName}-${asicName}", "IS_URL=${universeClient.is_url}",
                "UMS_LOGIN=${UMS_USER}", "UMS_PASSWORD=${UMS_PASSWORD}", "IS_LOGIN=${IS_USER}", "IS_PASSWORD=${IS_PASSWORD}"])
            {
                switch(osName)
                {
                case 'Windows':
                    dir('scripts')
                    {
                        bat """
                        run.bat ${options.testsPackage} \"${options.tests}\" >> ../${options.stageName}.log  2>&1
                        """
                    }
                    break;

                case 'OSX':
                    echo "OSX is not supported"
                    break;

                default:
                    dir('scripts')
                    {
                        withEnv(["LD_LIBRARY_PATH=../RprViewer/engines/hybrid:\$LD_LIBRARY_PATH"]) {
                            sh """
                            chmod +x ../RprViewer/RadeonProViewer
                            chmod +x run.sh
                            ./run.sh ${options.testsPackage} \"${options.tests}\" >> ../${options.stageName}.log  2>&1
                            """
                        }
                    }
                }
            }
        }
    }
}

def executeTests(String osName, String asicName, Map options)
{
    if (options.sendToUMS){
        universeClient.stage("Tests-${osName}-${asicName}", "begin")
    }
    // used for mark stash results or not. It needed for not stashing failed tasks which will be retried.
    Boolean stashResults = true

    try {

        timeout(time: "10", unit: 'MINUTES') {
            try {
                cleanWS(osName)
                checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_rprviewer.git')
                getViewerTool(osName, options)
            } catch(e) {
                println("[ERROR] Failed to prepare test group on ${env.NODE_NAME}")
                println(e.toString())
                throw e
            }
        }

        downloadAssets("${options.PRJ_ROOT}/${options.PRJ_NAME}/Assets/", 'RprViewer')

        String REF_PATH_PROFILE="${options.REF_PATH}/${asicName}-${osName}-NewStructure"

        options.REF_PATH_PROFILE = REF_PATH_PROFILE

        outputEnvironmentInfo(osName)

        if(options['updateRefs']) {
            executeTestCommand(osName, asicName, options)
            executeGenTestRefCommand(osName, options)
            sendFiles('./Work/Baseline/', REF_PATH_PROFILE)
        } else {
            try {
                String middle_dir = isUnix() ? "${CIS_TOOLS}/../TestResources/rpr_viewer_autotests_baselines" : "/mnt/c/TestResources/rpr_viewer_autotests_baselines"
                println "[INFO] Downloading reference images for ${options.tests}"
                options.tests.split(" ").each() {
                    receiveFiles("${REF_PATH_PROFILE}/${it}", middle_dir)
                }
            } catch (e) {
                println("[WARNING] Problem when copying baselines. " + e.getMessage())
            }
            executeTestCommand(osName, asicName, options)
        }
    } catch (e) {
        if (options.currentTry < options.nodeReallocateTries) {
            stashResults = false
        } 
        println(e.toString())
        println(e.getMessage())
        options.failureMessage = "Failed during testing: ${asicName}-${osName}"
        options.failureError = e.getMessage()
        throw e
    }
    finally {
        archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
        if (stashResults) {
            dir('Work')
            {
                if (fileExists("Results/RprViewer/session_report.json")) {

                    def sessionReport = null
                    sessionReport = readJSON file: 'Results/RprViewer/session_report.json'

                    // if none launched tests - mark build failed
                    if (sessionReport.summary.total == 0)
                    {
                        options.failureMessage = "Noone test was finished for: ${asicName}-${osName}"
                        currentBuild.result = "FAILED"
                    }

                    if (options.sendToUMS)
                    {
                        universeClient.stage("Tests-${osName}-${asicName}", "end")
                    }

                    echo "Stashing test results to : ${options.testResultsName}"
                    stash includes: '**/*', name: "${options.testResultsName}", allowEmpty: true

                    // reallocate node if there are still attempts
                    if (sessionReport.summary.total == sessionReport.summary.error + sessionReport.summary.skipped) {
                        if (sessionReport.summary.total != sessionReport.summary.skipped){
                            collectCrashInfo(osName, options)
                            if (osName == "Ubuntu18"){
                                sh """
                                    echo "Restarting Unix Machine...."
                                    hostname
                                    (sleep 3; sudo shutdown -r now) &
                                """
                                sleep(60)
                            }
                            if (options.currentTry < options.nodeReallocateTries) {
                                throw new Exception("All tests crashed")
                            }
                        }
                    }
                }
            }
        }
    }
}

def executeBuildWindows(Map options)
{
    bat"""
        cmake . -B build -G "Visual Studio 15 2017" -A x64 >> ${STAGE_NAME}.log 2>&1
        cmake --build build --target RadeonProViewer --config Release >> ${STAGE_NAME}.log 2>&1

        mkdir ${options.DEPLOY_FOLDER}
        xcopy config.json ${options.DEPLOY_FOLDER}
        xcopy README.md ${options.DEPLOY_FOLDER}
        xcopy UIConfig.json ${options.DEPLOY_FOLDER}
        xcopy sky.hdr ${options.DEPLOY_FOLDER}
        xcopy build\\Viewer\\Release\\RadeonProViewer.exe ${options.DEPLOY_FOLDER}\\RadeonProViewer.exe*

        xcopy shaders ${options.DEPLOY_FOLDER}\\shaders /y/i/s

        mkdir ${options.DEPLOY_FOLDER}\\rml\\lib
        xcopy rml\\lib\\RadeonML-DirectML.dll ${options.DEPLOY_FOLDER}\\rml\\lib\\RadeonML-DirectML.dll*
        xcopy rif\\models ${options.DEPLOY_FOLDER}\\rif\\models /s/i/y
        xcopy rif\\lib ${options.DEPLOY_FOLDER}\\rif\\lib /s/i/y
        del /q ${options.DEPLOY_FOLDER}\\rif\\lib\\*.lib
    """

    //temp fix
    bat"""
        xcopy build\\viewer\\engines ${options.DEPLOY_FOLDER}\\engines /s/i/y
    """

    def controlFiles = ['config.json', 'UIConfig.json', 'sky.hdr', 'RadeonProViewer.exe', 'rml/lib/RadeonML-DirectML.dll']
        controlFiles.each() {
        if (!fileExists("${options.DEPLOY_FOLDER}/${it}")) {
            error "Not found ${it}"
        }
    }

    zip archive: true, dir: "${options.DEPLOY_FOLDER}", glob: '', zipFile: "RprViewer_Windows.zip"
    stash includes: "RprViewer_Windows.zip", name: "appWindows"
    options.pluginWinSha = sha1 "RprViewer_Windows.zip"

}


def executeBuildLinux(Map options)
{
    sh """
        mkdir build
        cd build
        cmake .. >> ../${STAGE_NAME}.log 2>&1
        make >> ../${STAGE_NAME}.log 2>&1
    """

    sh """
        mkdir ${options.DEPLOY_FOLDER}
        cp config.json ${options.DEPLOY_FOLDER}
        cp README.md ${options.DEPLOY_FOLDER}
        cp UIConfig.json ${options.DEPLOY_FOLDER}
        cp sky.hdr ${options.DEPLOY_FOLDER}
        cp build/viewer/RadeonProViewer ${options.DEPLOY_FOLDER}/RadeonProViewer

        cp -rf shaders ${options.DEPLOY_FOLDER}/shaders

        mkdir ${options.DEPLOY_FOLDER}/rif
        cp -rf rif/models ${options.DEPLOY_FOLDER}/rif/models
        cp -rf rif/lib ${options.DEPLOY_FOLDER}/rif/lib

        cp -rf build/viewer/engines ${options.DEPLOY_FOLDER}/engines
    """

    zip archive: true, dir: "${options.DEPLOY_FOLDER}", glob: '', zipFile: "RprViewer_Ubuntu18.zip"
    stash includes: "RprViewer_Ubuntu18.zip", name: "appUbuntu18"
    options.pluginUbuntuSha = sha1 "RprViewer_Ubuntu18.zip"
}

def executeBuild(String osName, Map options)
{
    if (options.sendToUMS){
        universeClient.stage("Build-" + osName , "begin")
    }

    try {
        checkOutBranchOrScm(options['projectBranch'], options['projectRepo'])
        outputEnvironmentInfo(osName)

        switch(osName)
        {
        case 'Windows':
            executeBuildWindows(options);
            break;
        case 'OSX':
            println "OSX isn't supported."
            break;
        default:
            executeBuildLinux(options);
        }
    }
    catch (e) {
        currentBuild.result = "FAILED"
        throw e
    }
    finally {
        archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
    }
    if (options.sendToUMS){
        universeClient.stage("Build-" + osName, "end")
    }
}

def executePreBuild(Map options)
{
    checkOutBranchOrScm(options['projectBranch'], options['projectRepo'], true)

    options.commitAuthor = bat (script: "git show -s --format=%%an HEAD ",returnStdout: true).split('\r\n')[2].trim()
    options.commitMessage = bat (script: "git log --format=%%s -n 1", returnStdout: true).split('\r\n')[2].trim().replace('\n', '')
    options.commitSHA = bat (script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()

    println "The last commit was written by ${options.commitAuthor}."
    println "Commit message: ${options.commitMessage}"
    println "Commit SHA: ${options.commitSHA}"

    if (options.projectBranch){
        currentBuild.description = "<b>Project branch:</b> ${options.projectBranch}<br/>"
    } else {
        currentBuild.description = "<b>Project branch:</b> ${env.BRANCH_NAME}<br/>"
    }

    currentBuild.description += "<b>Commit author:</b> ${options.commitAuthor}<br/>"
    currentBuild.description += "<b>Commit message:</b> ${options.commitMessage}<br/>"
    currentBuild.description += "<b>Commit SHA:</b> ${options.commitSHA}<br/>"

    if (env.CHANGE_URL) {
        echo "branch was detected as Pull Request"
        options.testsPackage = "PR"
    } else if(env.BRANCH_NAME && env.BRANCH_NAME == "master") {
        options.testsPackage = "master"
    } else if(env.BRANCH_NAME) {
        options.testsPackage = "smoke"
    }

    if (env.BRANCH_NAME && (env.BRANCH_NAME == "master" || env.BRANCH_NAME == "develop")) {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '20']]]);
    } else if (env.BRANCH_NAME && env.BRANCH_NAME != "master" && env.BRANCH_NAME != "develop") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '3']]]);
    } else if (env.JOB_NAME == "RadeonProViewer-WeeklyFull") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '20']]]);
    } else {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '20']]]);
    }

    def tests = []
    options.timeouts = [:]
    options.groupsUMS = []

    dir('jobs_test_rprviewer')
    {
        checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_rprviewer.git')

        if(options.testsPackage != "none")
        {
            if(options.testsPackage.endsWith('.json'))
            {
                def testsByJson = readJSON file: "jobs/${options.testsPackage}"
                testsByJson.each() {
                    options.groupsUMS << "${it.key}"
                }
                options.splitTestsExecution = false
                options.timeouts = ["regression.json": options.REGRESSION_TIMEOUT + options.ADDITIONAL_XML_TIMEOUT]
            }
            else
            {
                String tempTests = readFile("jobs/${options.testsPackage}")
                tempTests.split("\n").each {
                    // TODO: fix: duck tape - error with line ending
                    def test_group = "${it.replaceAll("[^a-zA-Z0-9_]+","")}"
                    tests << test_group
                    def xml_timeout = utils.getTimeoutFromXML(this, "${test_group}", "simpleRender.py", options.ADDITIONAL_XML_TIMEOUT)
                    options.timeouts["${test_group}"] = (xml_timeout > 0) ? xml_timeout : options.TEST_TIMEOUT
                }
                options.tests = tests
                options.testsPackage = "none"
                options.groupsUMS = tests
            }
        }
        else
        {
            options.tests.split(" ").each()
            {
                tests << "${it}"
                def xml_timeout = utils.getTimeoutFromXML(this, "${it}", "simpleRender.py", options.ADDITIONAL_XML_TIMEOUT)
                options.timeouts["${it}"] = (xml_timeout > 0) ? xml_timeout : options.TEST_TIMEOUT
            }
            options.tests = tests
            options.groupsUMS = tests
        }
    }

    if(options.splitTestsExecution)
    {
        options.testsList = options.tests
    }
    else
    {
        options.testsList = ['']
        options.tests = tests.join(" ")
    }

    println "timeouts: ${options.timeouts}"

    if (options.sendToUMS)
    {
        try
        {
            // Universe : auth because now we in node
            // If use httpRequest in master slave will catch 408 error
            universeClient.tokenSetup()

            // create build ([OS-1:GPU-1, ... OS-N:GPU-N], ['Suite1', 'Suite2', ..., 'SuiteN'])
            universeClient.createBuild(options.universePlatforms, options.groupsUMS)
        }
        catch (e)
        {
            println(e.toString())
        }
    }
}

def executeDeploy(Map options, List platformList, List testResultList)
{
    try
    {
        if(options['executeTests'] && testResultList)
        {
            checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_rprviewer.git')

            List lostStashes = []

            dir("summaryTestResults")
            {
                unstashCrashInfo(options['nodeRetry'])
                testResultList.each()
                {
                    dir("$it".replace("testResult-", ""))
                    {
                        try
                        {
                            unstash "$it"
                        }catch(e)
                        {
                            echo "[ERROR] Failed to unstash ${it}"
                            lostStashes.add("'$it'".replace("testResult-", ""))
                            println(e.toString());
                            println(e.getMessage());
                        }

                    }
                }
            }

            try {
                String executionType
                if (options.testsPackage.endsWith('.json')) {
                    executionType = 'regression'
                } else if (options.splitTestsExecution) {
                    executionType = 'split_execution'
                } else {
                    executionType = 'default'
                }

                dir("jobs_launcher") {
                    bat """
                    count_lost_tests.bat \"${lostStashes}\" .. ..\\summaryTestResults ${executionType} \"${options.tests}\"
                    """
                }
            } catch (e) {
                println("[ERROR] Can't generate number of lost tests")
            }
            
            String branchName = env.BRANCH_NAME ?: options.projectBranch

            try {
                withEnv(["JOB_STARTED_TIME=${options.JOB_STARTED_TIME}"])
                {
                    dir("jobs_launcher") {
                        def retryInfo = JsonOutput.toJson(options.nodeRetry)
                        bat """
                        build_reports.bat ..\\summaryTestResults "RprViewer" ${options.commitSHA} ${branchName} \"${escapeCharsByUnicode(options.commitMessage)}\" \"${escapeCharsByUnicode(retryInfo.toString())}\"
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
                dir("jobs_launcher") {
                    archiveArtifacts "launcher.engine.log"
                }
            }
            catch(e)
            {
                println("ERROR during archiving launcher.engine.log")
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
                } else {
                    currentBuild.result="SUCCESS"
                }
            }
            catch(e)
            {
                println("CAN'T GET TESTS STATUS")
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

            utils.publishReport(this, "${BUILD_URL}", "summaryTestResults", "summary_report.html, performance_report.html, compare_report.html", \
                "Test Report", "Summary Report, Performance Report, Compare Report")

            if (options.sendToUMS) {
                try {
                    String status = currentBuild.result ?: 'SUCCESSFUL'
                    universeClient.changeStatus(status)
                }
                catch (e){
                    println(e.getMessage())
                }
            }
        }
    }
    catch(e)
    {
        println(e.toString())
    }
}

def call(String projectBranch = "",
         String testsBranch = "master",
         String platforms = 'Windows:AMD_RadeonVII;Ubuntu18:AMD_RadeonVII',
         Boolean updateRefs = false,
         Boolean enableNotifications = true,
         String testsPackage = "",
         String tests = "",
         Boolean splitTestsExecution = true,
         Boolean sendToUMS = true,
         String tester_tag = 'RprViewer') {

    def nodeRetry = []

    String PRJ_ROOT='rpr-core'
    String PRJ_NAME='RadeonProViewer'
    String projectRepo='git@github.com:Radeon-Pro/RadeonProViewer.git'

    def universePlatforms = convertPlatforms(platforms);

    println "Platforms: ${platforms}"
    println "Tests: ${tests}"
    println "Tests package: ${testsPackage}"
    println "UMS platforms: ${universePlatforms}"

    multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy,
                           [projectBranch:projectBranch,
                            testsBranch:testsBranch,
                            updateRefs:updateRefs,
                            enableNotifications:enableNotifications,
                            PRJ_NAME:PRJ_NAME,
                            PRJ_ROOT:PRJ_ROOT,
                            projectRepo:projectRepo,
                            BUILDER_TAG:'BuilderViewer',
                            TESTER_TAG:tester_tag,
                            executeBuild:true,
                            executeTests:true,
                            splitTestsExecution:splitTestsExecution,
                            DEPLOY_FOLDER:"RprViewer",
                            testsPackage:testsPackage,
                            TEST_TIMEOUT:45,
                            ADDITIONAL_XML_TIMEOUT:15,
                            REGRESSION_TIMEOUT:45,
                            DEPLOY_TIMEOUT:45,
                            tests:tests,
                            nodeRetry: nodeRetry,
                            sendToUMS:sendToUMS,
                            universePlatforms: universePlatforms])
}
