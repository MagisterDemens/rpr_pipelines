import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import java.text.SimpleDateFormat;

def executeTestsNode(String osName, String gpuNames, def executeTests, Map options)
{
    if(gpuNames && options['executeTests'])
    {
        def testTasks = [:]
        gpuNames.split(',').each()
        {
            String asicName = it
            testTasks["Test-${it}-${osName}"] = {
                stage("Test-${asicName}-${osName}") {
                    // if not split - testsList doesn't exists
                    // TODO: replace testsList check to splitExecution var
                    options.testsList = options.testsList ?: ['']

                    options.testsList.each() { testName ->
                        println("Scheduling ${osName}:${asicName} ${testName}")

                        String testerTag = options.TESTER_TAG ? "${options.TESTER_TAG} && Tester" : "Tester"
                        // reallocate node for each test
                        node("${osName} && ${testerTag} && OpenCL && gpu${asicName}")
                        {
                            println("Launched at: ${NODE_NAME}")
                            timeout(time: "${options.TEST_TIMEOUT}", unit: 'MINUTES')
                            {
                                ws("WS/${options.PRJ_NAME}_Test")
                                {
                                    println('Start!')
                                    Map newOptions = options.clone()
                                    newOptions['testResultsName'] = testName ? "testResult-${asicName}-${osName}-${testName}" : "testResult-${asicName}-${osName}"
                                    newOptions['stageName'] = testName ? "${asicName}-${osName}-${testName}" : "${asicName}-${osName}"
                                    newOptions['tests'] = testName ? testName : options.tests
                                    executeTests(osName, asicName, newOptions)
                                }
                            }
                        }
                    }

                }
            }
        }
        parallel testTasks
    }
    else
    {
        echo "No tests found for ${osName}"
    }
}

def executePlatform(String osName, String gpuNames, def executeBuild, def executeTests, Map options)
{
    def retNode =
    {
        try
        {
            if(!options['skipBuild'] && options['executeBuild'])
            {
                node("${osName} && ${options.BUILDER_TAG}")
                {
                    println("Started build at ${NODE_NAME}")
                    stage("Build-${osName}")
                    {
                        timeout(time: "${options.BUILD_TIMEOUT}", unit: 'MINUTES')
                        {
                            ws("WS/${options.PRJ_NAME}_Build")
                            {
                                executeBuild(osName, options)
                            }
                        }
                    }
                }
            }
            executeTestsNode(osName, gpuNames, executeTests, options)
        }
        catch (e)
        {
            println(e.toString());
            println(e.getMessage());
            currentBuild.result = "FAILED"
            options.FAILED_STAGES.add("e.toString()")
            throw e
        }
    }
    return retNode
}

def call(String platforms, def executePreBuild, def executeBuild, def executeTests, def executeDeploy, Map options) {
    try {

        // if it's PR - supersede all previously launched executions
        if(env.CHANGE_ID) {
            //set logRotation for PRs
            properties([[$class: 'BuildDiscarderProperty', strategy:
                [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '5']]]);

            def buildNumber = env.BUILD_NUMBER as int
            if (buildNumber > 1) milestone(buildNumber - 1)
            milestone(buildNumber)

        }

        def date = new Date()
        dateFormatter = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss")
        options.JOB_STARTED_TIME = dateFormatter.format(date)

        /*properties([[$class: 'BuildDiscarderProperty', strategy:
                     [$class: 'LogRotator', artifactDaysToKeepStr: '',
                      artifactNumToKeepStr: '10', daysToKeepStr: '', numToKeepStr: '']]]);*/
        timestamps
        {
            String PRJ_PATH="${options.PRJ_ROOT}/${options.PRJ_NAME}"
            String REF_PATH="${PRJ_PATH}/ReferenceImages"
            String JOB_PATH="${PRJ_PATH}/${JOB_NAME}/Build-${BUILD_ID}".replace('%2F', '_')
            options['PRJ_PATH']="${PRJ_PATH}"
            options['REF_PATH']="${REF_PATH}"
            options['JOB_PATH']="${JOB_PATH}"

            if(options.get('BUILDER_TAG', '') == '')
                options['BUILDER_TAG'] = 'Builder'

            // if timeout doesn't set - use default
            // value in minutes
            options['PREBUILD_TIMEOUT'] = options['PREBUILD_TIMEOUT'] ?: 15
            options['BUILD_TIMEOUT'] = options['BUILD_TIMEOUT'] ?: 60
            options['TEST_TIMEOUT'] = options['TEST_TIMEOUT'] ?: 60
            options['DEPLOY_TIMEOUT'] = options['DEPLOY_TIMEOUT'] ?: 60

            options['FAILED_STAGES'] = []

            def platformList = [];
            def testResultList = [];

            try
            {
                if(executePreBuild)
                {
                    node("Windows && PreBuild")
                    {
                        ws("WS/${options.PRJ_NAME}_PreBuild")
                        {
                            stage("PreBuild")
                            {
                                timeout(time: "${options.PREBUILD_TIMEOUT}", unit: 'MINUTES')
                                {
                                    executePreBuild(options)
                                    if(!options['executeBuild'])
                                    {
                                        options.CBR = 'SKIPPED'
                                        echo "Build SKIPPED"
                                    }
                                }
                            }
                        }
                    }
                }

                def tasks = [:]

                platforms.split(';').each()
                {
                    List tokens = it.tokenize(':')
                    String osName = tokens.get(0)
                    String gpuNames = ""
                    if (tokens.size() > 1)
                    {
                        gpuNames = tokens.get(1)
                    }

                    platformList << osName
                    if(gpuNames)
                    {
                        gpuNames.split(',').each()
                        {
                            // if not split - testsList doesn't exists
                            options.testsList = options.testsList ?: ['']
                            options['testsList'].each() { testName ->
                                String asicName = it
                                String testResultItem = testName ? "testResult-${asicName}-${osName}-${testName}" : "testResult-${asicName}-${osName}"
                                testResultList << testResultItem
                            }
                        }
                    }

                    tasks[osName]=executePlatform(osName, gpuNames, executeBuild, executeTests, options)
                }
                parallel tasks
            }
            finally
            {
                node("Windows && ReportBuilder")
                {
                    stage("Deploy")
                    {
                        timeout(time: "${options.DEPLOY_TIMEOUT}", unit: 'MINUTES')
                        {
                            ws("WS/${options.PRJ_NAME}_Deploy") {

                                try
                                {
                                    if(executeDeploy && options['executeTests'])
                                    {
                                        executeDeploy(options, platformList, testResultList)
                                    }
                                }
                                catch (e) {
                                    println(e.toString());
                                    println(e.getMessage());
                                    currentBuild.result = "FAILED"
                                    throw e
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    catch (FlowInterruptedException e)
    {
        println(e.toString());
        println(e.getMessage());
        currentBuild.result = "ABORTED"
        echo "Job was ABORTED by user. Job status: ${currentBuild.result}"
    }
    catch (e)
    {
        println(e.toString());
        println(e.getMessage());
        currentBuild.result = "FAILED"
        throw e
    }
    finally
    {
        echo "enableNotifications = ${options.enableNotifications}"
        if("${options.enableNotifications}" == "true")
        {
            sendBuildStatusNotification(currentBuild.result,
                                        options.get('slackChannel', ''),
                                        options.get('slackBaseUrl', ''),
                                        options.get('slackTocken', ''),
                                        options)
        }

        echo "Send Slack message to debug channels"
        sendBuildStatusToDebugSlack(options)
    }
}
