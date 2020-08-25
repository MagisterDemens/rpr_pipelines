def executeTests(String osName, String asicName, Map options)
{
    // TODO: implement tests stage
}

def executeBuildWindows(Map options)
{
    dir('U\\integration')
    {
        bat """
            Build.bat ${options.targets.join(' ')} ${options.version} ${options.renderType} ${options.engineConfiguration} ${options.testsVariants.join(' ')} ${options.testsName.join(' ')} ${options.visualStudioVersion} >> ..\\${STAGE_NAME}.log 2>&1
        """
    }
}

def executeBuild(String osName, Map options)
{
    try {        
        dir('U')
        {
            checkOutBranchOrScm(options['projectBranch'], 'git@github.com:amfdev/UnrealEngine_dev.git')
        }
        
        outputEnvironmentInfo(osName)

        switch(osName)
        {
        case 'Windows': 
            executeBuildWindows(options); 
            break;
        case 'OSX':
            echo "[WARNING] OSX is not supported"
            break;
        default: 
            echo "[WARNING] ${osName} is not supported"
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
         String version = '',
         String renderType = '',
         String engineConfiguration = '',
         String testsVariants = '',
         String testsName = '',
         String visualStudioVersion = '',
         Boolean enableNotifications = false) {
    try
    {
        String PRJ_NAME="UE"
        String PRJ_ROOT="gpuopen"

        targets = targets.split(',')
        testsVariants = testsVariants.split(',')
        testsName = testsName.split(',')

        multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy,
                               [projectBranch:projectBranch,
                                targets:targets,
                                version:version,
                                renderType:renderType,
                                engineConfiguration:engineConfiguration,
                                testsVariants:testsVariants,
                                testsName:testsName,
                                visualStudioVersion:visualStudioVersion,
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
