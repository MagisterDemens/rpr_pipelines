def executeGenTestRefCommand(String osName, Map options)
{
    executeTestCommand(osName, options)

    dir('scripts')
    {
        switch(osName)
        {
            case 'Windows':
                bat """
                make_rpr_baseline.bat
                """
                break;
            case 'OSX':
                sh """
                ./make_rpr_baseline.sh
                """
                break;
            default:
                sh """
                ./make_rpr_baseline.sh
                """
        }
    }
}

def executeTestCommand(String osName, Map options)
{
    switch(osName)
    {
    case 'Windows':
        dir('scripts')
        {
            bat """
            render_rpr.bat ${options.testsPackage} \"${options.tests}\">> ../${STAGE_NAME}.log  2>&1
            """
        }
        break;
    case 'OSX':
        sh """
        echo 'sample image' > ./OutputImages/sample_image.txt
        """
        break;
    default:
        sh """
        echo 'sample image' > ./OutputImages/sample_image.txt
        """
    }
}

def installPlugins(String osName, Map options)
{
    switch(osName)
    {
    case 'Windows':
        // remove installed plugin
        try
        {
            powershell"""
            \$uninstall = Get-WmiObject -Class Win32_Product -Filter "Name = 'Radeon ProRender for Autodesk Maya®'"
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
            receiveFiles("/bin_storage/RadeonProRenderMaya_2.8.44.msi", "/mnt/c/TestResources/")

            bat """
            msiexec /i "C:\\TestResources\\RadeonProRenderMaya_2.8.44.msi" /quiet /qn PIDKEY=${env.RPR_PLUGIN_KEY} /L+ie ../../${options.stageName}.install.log /norestart
            """
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
        echo "pass"
        break;
    default:
        echo "pass"
    }
}

def executeTests(String osName, String asicName, Map options)
{
    try {
        checkoutGit(options['testsBranch'], 'git@github.com:luxteam/jobs_test_rs2rpr.git')
        dir('jobs/Scripts')
        {
            if(fileExists("convertRS2RPR.py")){
                bat "del convertRS2RPR.py"
            }
            unstash "convertionScript"
        }

        // update assets
        if(isUnix())
        {
            sh """
            ${CIS_TOOLS}/receiveFilesSync.sh /${options.PRJ_PATH}/RedshiftAssets/ ${CIS_TOOLS}/../TestResources/RedshiftAssets
            """
        }
        else
        {
            bat """
            %CIS_TOOLS%\\receiveFilesSync.bat /${options.PRJ_PATH}/RedshiftAssets/ /mnt/c/TestResources/RedshiftAssets
            """
        }

        String REF_PATH_PROFILE="${options.REF_PATH}/${asicName}-${osName}"
        String JOB_PATH_PROFILE="${options.JOB_PATH}/${asicName}-${osName}"

        String REF_PATH_PROFILE_OR="${options.REF_PATH}/Redshift-${osName}"
        String JOB_PATH_PROFILE_OR="${options.JOB_PATH}/Redshift-${osName}"

        outputEnvironmentInfo(osName)

        if(options['updateORRefs'])
        {
            dir('scripts')
            {
                bat """render_or.bat ${options.testsPackage} \"${options.tests}\">> ../${STAGE_NAME}.log  2>&1"""
                bat "make_original_baseline.bat"
            }
            sendFiles('./Work/Baseline/', REF_PATH_PROFILE_OR)
        }
        else if(options['updateRefs'])
        {
            installPlugins(osName, options)

            executeGenTestRefCommand(osName, options)
            sendFiles('./Work/Baseline/', REF_PATH_PROFILE)
        }
        else
        {
            installPlugins(osName, options)
            try
            {
                options.tests.split(" ").each() {
                    receiveFiles("${REF_PATH_PROFILE}/${it}", './Work/Baseline/')
                }
            } catch (e) {}
            try
            {
                options.tests.split(" ").each() {
                    receiveFiles("${REF_PATH_PROFILE_OR}/${it}", './Work/Baseline/')
                }
            } catch (e) {}
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

            def sessionReport = readJSON file: 'Results/rs2rpr/session_report.json'
            if (sessionReport.summary.total == 0) {
                options.failureMessage = "Noone test was finished for: ${asicName}-${osName}"
                currentBuild.result = "FAILED"
            }
            /*sessionReport.results.each{ testName, testConfigs ->
                testConfigs.each{ key, value ->
                    if ( value.render_duration == 0)
                    {
                        error "Crashed tests detected"
                    }
                }
            }*/
        }
    }
}

def executeBuildWindows(Map options)
{

}

def executeBuildOSX(Map options)
{

}

def executeBuildLinux(Map options)
{

}

def executeBuild(String osName, Map options)
{
    try {
        // dir('RS2RPRConvertTool')
        // {
        //     checkoutGit(options['projectBranch'], 'https://github.com/luxteam/RS2RPRConvertTool.git')
        //     stash includes: "convertRS2RPR.mel", name: "convertionScript"
        // }

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

        //stash includes: 'Bin/**/*', name: "app${osName}"
    }
    catch (e) {
        currentBuild.result = "FAILED"
        throw e
    }
    finally {
        archiveArtifacts "*.log"
    }
}

def executePreBuild(Map options)
{
    //properties([])

    dir('RS2RPRConvertTool')
    {
        checkOutBranchOrScm(options['projectBranch'], 'git@github.com:luxteam/RS2RPRConvertTool.git')

        stash includes: "convertRS2RPR.py", name: "convertionScript"

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

    }


    def tests = []
    if(options.testsPackage != "none")
    {
        dir('jobs_test_rs2rpr')
        {
            checkOutBranchOrScm(options['testsBranch'], 'https://github.com/luxteam/jobs_test_rs2rpr.git')
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

def executeDeploy(Map options, List platformList, List testResultList)
{
    try {
        if(options['executeTests'] && testResultList)
        {
            checkoutGit(options['testsBranch'], 'git@github.com:luxteam/jobs_test_rs2rpr.git')

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

            String branchName = env.BRANCH_NAME ?: options.projectBranch

            try {
                withEnv(["JOB_STARTED_TIME=${options.JOB_STARTED_TIME}"])
                {
                    dir("jobs_launcher") {
                        bat """
                        build_reports.bat ..\\summaryTestResults RS2RPR ${options.commitSHA} ${branchName} \"${escapeCharsByUnicode(options.commitMessage)}\"
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
                         reportFiles: 'summary_report.html',
                         reportName: 'Test Report',
                         reportTitles: 'Summary Report'])
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
         String platforms = 'Windows:NVIDIA_GF1080TI',
         Boolean updateORRefs = false,
         Boolean updateRefs = false,
         Boolean enableNotifications = true,
         String testsPackage = "",
         String tests = "") {
    try
    {
        String PRJ_NAME="RS2RPRConvertTool-Maya"
        String PRJ_ROOT="rpr-tools"

        multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy,
                               [projectBranch:projectBranch,
                                testsBranch:testsBranch,
                                updateORRefs:updateORRefs,
                                updateRefs:updateRefs,
                                enableNotifications:enableNotifications,
                                executeTests:true,
                                PRJ_NAME:PRJ_NAME,
                                PRJ_ROOT:PRJ_ROOT,
                                testsPackage:testsPackage,
                                tests:tests,
                                reportName:'Test_20Report',
                                TEST_TIMEOUT:120])
    }
    catch(e) {
        currentBuild.result = "FAILED"
        println(e.toString());
        println(e.getMessage());
        throw e
    }
}
