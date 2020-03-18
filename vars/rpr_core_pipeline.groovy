import RBSDevelopment
import RBSProduction


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

def executeTestCommand(String osName, Map options)
{
    if (!options.skipBuild) {
        dir('rprSdk') {
                unstash "${osName}SDK"
            }
    }

    switch(osName) {
        case 'Windows':
            dir('scripts')
            {
                bat """
                run.bat ${options.testsPackage} \"${options.tests}\" ${options.width} ${options.height} ${options.iterations} \"${options.engine}\" >> ../${STAGE_NAME}.log 2>&1
                """
            }
            break;
        case 'OSX':
            dir('scripts')
            {
                withEnv(["LD_LIBRARY_PATH=../rprSdk:\$LD_LIBRARY_PATH"]) {
                    sh """
                    ./run.sh ${options.testsPackage} \"${options.tests}\" ${options.width} ${options.height} ${options.iterations} \"${options.engine}\" >> ../${STAGE_NAME}.log 2>&1
                    """
                }
            }
            break;
        default:
            dir('scripts')
            {
                withEnv(["LD_LIBRARY_PATH=../rprSdk:\$LD_LIBRARY_PATH"]) {
                    sh """
                    ./run.sh ${options.testsPackage} \"${options.tests}\" ${options.width} ${options.height} ${options.iterations} \"${options.engine}\" >> ../${STAGE_NAME}.log 2>&1
                    """
                }
            }
    }
}

def executeTests(String osName, String asicName, Map options)
{
    cleanWs(deleteDirs: true, disableDeferredWipeout: true)
    try {

        checkoutGit(options['testsBranch'], 'git@github.com:luxteam/jobs_test_core.git')

//        Enable for testing Core Split
//        if (options.sendToRBS) {
//            options.rbs_prod.setTester(options)
//            options.rbs_dev.setTester(options)
//        }


        downloadAssets("${options.PRJ_ROOT}/${options.PRJ_NAME}/CoreAssets/", 'CoreAssets')

        String REF_PATH_PROFILE="${options.REF_PATH}/${asicName}-${osName}"
        String JOB_PATH_PROFILE="${options.JOB_PATH}/${asicName}-${osName}"

        options.REF_PATH_PROFILE = REF_PATH_PROFILE

        outputEnvironmentInfo(osName)

        if(options['updateRefs'])
        {
            executeGenTestRefCommand(osName, options)
            sendFiles('./Work/Baseline/', REF_PATH_PROFILE)
        }
        else if(options.updateRefsByOne)
        {
            executeGenTestRefCommand(osName, options)
            ['AMD_RXVEGA', 'AMD_WX9100', 'AMD_WX7100'].each
            {
                sendFiles('./Work/Baseline/', "${options.REF_PATH}/${it}-Windows")
            }
        }
        else
        {
            try {
                receiveFiles("${REF_PATH_PROFILE}/*", './Work/Baseline/')
            } catch(e) {
                println("No baseline")
            }
            executeTestCommand(osName, options)
        }
    }
    catch (e) {
        println(e.toString());
        println(e.getMessage());
        currentBuild.result = "FAILED"
        throw e
    }
    finally {
        archiveArtifacts "*.log"
        echo "Stashing test results to : ${options.testResultsName}"
        dir('Work')
        {
            stash includes: '**/*', name: "${options.testResultsName}", allowEmpty: true

            try
            {
                def sessionReport = readJSON file: 'Results/Core/session_report.json'
                // if none launched tests - mark build failed
                if (sessionReport.summary.total == 0)
                {
                    options.failureMessage = "Noone test was finished for: ${asicName}-${osName}"
                    currentBuild.result = "FAILED"
                }

                if (options.sendToRBS)
                {
                    options.rbs_prod.sendSuiteResult(sessionReport, options)
                    options.rbs_dev.sendSuiteResult(sessionReport, options)
                }
            }
            catch (e)
            {
                println(e.toString())
                println(e.getMessage())
            }
        }
    }
}

def executeBuildWindows(Map options)
{
    dir('RadeonProRenderSDK/RadeonProRender/binWin64')
    {
        stash includes: '**', name: 'WindowsSDK'
        zip archive: true, dir: '.', glob: '', zipFile: 'binWin64.zip'
    }
}

def executeBuildOSX(Map options)
{
    dir('RadeonProRenderSDK/RadeonProRender/binMacOS')
    {
        stash includes: '**', name: 'OSXSDK'
        zip archive: true, dir: '.', glob: '', zipFile: 'binMacOS.zip'
    }

}

def executeBuildLinux(Map options)
{
    dir('RadeonProRenderSDK/RadeonProRender/binUbuntu18')
    {
        stash includes: '**', name: 'Ubuntu18SDK'
        zip archive: true, dir: '.', glob: '', zipFile: 'binUbuntu18.zip'

        // stash includes: 'binCentOS7/*', name: 'CentOSSDK'
        // zip archive: true, dir: 'binCentOS7', glob: '', zipFile: 'binCentOS7.zip'
    }

}

def executeBuild(String osName, Map options)
{
    try {
        dir('RadeonProRenderSDK')
        {
            checkoutGit(options['projectBranch'], 'https://github.com/Radeon-Pro/RadeonProRenderSDK.git')
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
            executeBuildLinux(options);
        }
    }
    catch (e) {
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

    checkoutGit(options['projectBranch'], 'https://github.com/Radeon-Pro/RadeonProRenderSDK.git')

    AUTHOR_NAME = bat (
            script: "git show -s --format=%%an HEAD ",
            returnStdout: true
            ).split('\r\n')[2].trim()

    echo "The last commit was written by ${AUTHOR_NAME}."
    options.AUTHOR_NAME = AUTHOR_NAME

    commitMessage = bat ( script: "git log --format=%%B -n 1", returnStdout: true )
    echo "Commit message: ${commitMessage}"
    options.commitMessage = commitMessage.split('\r\n')[2].trim()

    options['commitSHA'] = bat(script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()

    if(!env.CHANGE_URL)
    {
        currentBuild.description += "<b>Commit author:</b> ${options.AUTHOR_NAME}<br/>"
        currentBuild.description += "<b>Commit message:</b> ${options.commitMessage}<br/>"
    }

    if (env.BRANCH_NAME && env.BRANCH_NAME == "master") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '30']]]);
    } else if (env.BRANCH_NAME && BRANCH_NAME != "master") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '5']]]);
    } else {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '30']]]);
    }


    if (options.sendToRBS)
    {
        try
        {
            def tests = []
            if(options.testsPackage != "none")
            {
                dir('jobs_test_core')
                {
                    checkOutBranchOrScm(options['testsBranch'], 'https://github.com/luxteam/jobs_test_core.git')
                    // options.splitTestsExecution = false
                    String tempTests = readFile("jobs/${options.testsPackage}")
                    tempTests.split("\n").each {
                        // TODO: fix: duck tape - error with line ending
                        tests << "${it.replaceAll("[^a-zA-Z0-9_]+","")}"
                    }

                    options.groupsRBS = tests
                }
            }
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
            checkoutGit(options['testsBranch'], 'https://github.com/luxteam/jobs_test_core.git')

            dir("summaryTestResults")
            {
                testResultList.each()
                {
                    dir("$it".replace("testResult-", ""))
                    {
                        try
                        {
                            unstash "$it"
                        }catch(e)
                        {
                            echo "Can't unstash ${it}"
                            println(e.toString());
                            println(e.getMessage());
                        }

                    }
                }
            }

            dir("jobs_launcher")
            {
                if(options.projectBranch != "") {
                    options.branchName = options.projectBranch
                } else {
                    options.branchName = env.BRANCH_NAME
                }
                if(options.incrementVersion) {
                    options.branchName = "master"
                }

                options.commitMessage = options.commitMessage.replace("'", "")
                options.commitMessage = options.commitMessage.replace('"', '')
                bat """
                build_reports.bat ..\\summaryTestResults Core ${options.commitSHA} ${options.branchName} \"${escapeCharsByUnicode(options.commitMessage)}\"
                """
                bat "get_status.bat ..\\summaryTestResults"
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

            publishHTML([allowMissing: false,
                         alwaysLinkToLastBuild: false,
                         keepAll: true,
                         reportDir: 'summaryTestResults',
                         reportFiles: 'summary_report.html, performance_report.html, compare_report.html',
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
    catch (e) {
        currentBuild.result = "FAILED"
        println(e.toString());
        println(e.getMessage());
        throw e
    }
    finally
    {}
}


def call(String projectBranch = "",
         String testsBranch = "master",
         String platforms = 'Windows:AMD_RXVEGA,AMD_WX9100,AMD_WX7100,AMD_RadeonVII,NVIDIA_GF1080TI,NVIDIA_RTX2080;OSX:AMD_RXVEGA;Ubuntu18:AMD_RadeonVII,NVIDIA_GTX980',
         Boolean updateRefs = false,
         Boolean updateRefsByOne = false,
         Boolean enableNotifications = true,
         Boolean skipBuild = false,
         String renderDevice = "gpu",
         String testsPackage = "Full",
         String tests = "",
         String width = "0",
         String height = "0",
         String iterations = "0",
         String engine = "",
         Boolean sendToRBS = true) {
    try
    {
        String PRJ_NAME="RadeonProRenderCore"
        String PRJ_ROOT="rpr-core"


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

        rbs_prod = new RBSProduction(this, "Core", env.JOB_NAME, env)
        rbs_dev = new RBSDevelopment(this, "Core", env.JOB_NAME, env)

        multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy,
                               [projectBranch:projectBranch,
                                testsBranch:testsBranch,
                                updateRefs:updateRefs,
                                updateRefsByOne:updateRefsByOne,
                                enableNotifications:enableNotifications,
                                PRJ_NAME:PRJ_NAME,
                                PRJ_ROOT:PRJ_ROOT,
                                BUILDER_TAG:'BuilderS',
                                slackChannel:"${SLACK_CORE_CHANNEL}",
                                skipBuild:skipBuild,
                                renderDevice:renderDevice,
                                testsPackage:testsPackage,
                                tests:tests.replace(',', ' '),
                                executeBuild:true,
                                executeTests:true,
                                reportName:'Test_20Report',
                                width:width,
                                gpusCount:gpusCount,
                                height:height,
                                iterations:iterations,
                                engine:engine.replace(',', ' '),
                                sendToRBS:sendToRBS,
                                rbs_prod: rbs_prod,
                                rbs_dev: rbs_dev
                                ])
    }
    catch(e) {
        currentBuild.result = "FAILED"
        println(e.toString());
        println(e.getMessage());
        throw e
    }
}
