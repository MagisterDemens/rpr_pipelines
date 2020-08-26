def executeTests(String osName, String asicName, Map options)
{
    // TODO: implement tests stage
}

def getPreparedUE(String version, String pluginType, Boolean forceDownloadUE) {
    String targetFolderPath = "${CIS_TOOLS}\\..\\PreparedUE\\UE-${version}"
    String folderName = pluginType == "Standard" ? "UE-${version}" : "UE-${version}-${pluginType}"
    if (!fileExists(targetFolderPath) || forceDownloadUE) {
        println("[INFO] UnrealEngine will be downloaded and configured")
        bat """
            Build.bat Engine ${version} PrepareUE Development >> ..\\..\\PrepareUE.${STAGE_NAME}.log 2>&1
        """

        println("[INFO] Prepared UE is ready. Saving it for use in future builds...")
        bat """
            xcopy /s/y/i UE-${version} ${targetFolderPath} >> nul
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

def executeBuildWindows(Map options)
{
    dir('U\\integration')
    {
        getPreparedUE(options['version'], options['pluginType'], options['forceDownloadUE'])
        bat """
            Build.bat ${options.targets.join(' ')} ${options.version} ${options.pluginType} ${options.engineConfiguration} ${options.testsVariants.join(' ')} ${options.testsName.join(' ')} ${options.visualStudioVersion} ${options.source} >> ..\\..\\${STAGE_NAME}.log 2>&1
        """
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
        archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
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
         String pluginType = '',
         String engineConfiguration = '',
         String testsVariants = '',
         String testsName = '',
         String visualStudioVersion = '',
         String graphicsAPI = '',
         String pluginRepository = '',
         Boolean forceDownloadUE = false,
         Boolean enableNotifications = false) {
    try
    {
        String PRJ_NAME="UE"
        String PRJ_ROOT="gpuopen"

        targets = targets.split(',')
        testsVariants = testsVariants.split(',')
        testsName = testsName.split(',')
        graphicsAPI = graphicsAPI.split(',')
        for (int i = 0; i < graphicsAPI.length; i++) {
            // DX11 is selected if 'Vulkan' value isn't specified. There isn't special key for DX11
            if (graphicsAPI[i].contains("DX11")) {
                graphicsAPI[i] = " "
                break
            }
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
                                engineConfiguration:engineConfiguration,
                                testsVariants:testsVariants,
                                testsName:testsName,
                                visualStudioVersion:visualStudioVersion,
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
