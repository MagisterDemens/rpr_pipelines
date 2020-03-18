def executeGenTestRefCommand(String osName)
{
    switch(osName)
    {
    case 'Windows':
        bat """
        ..\\Bin\\Release\\x64\\BaikalTest64.exe -genref 1 --gtest_output=xml:../${STAGE_NAME}.gtest.xml >> ..\\${STAGE_NAME}.log 2>&1
        """
        break;
    case 'OSX':
        sh """
            ../Bin/Release/x64/BaikalTest64 -genref 1 --gtest_output=xml:../${STAGE_NAME}.gtest.xml >> ../${STAGE_NAME}.log 2>&1
        """
        break;
    default:
        sh """
            export LD_LIBRARY_PATH=`pwd`/../Bin/Release/x64/:\${LD_LIBRARY_PATH}
            ../Bin/Release/x64/BaikalTest64 -genref 1 --gtest_output=xml:../${STAGE_NAME}.gtest.xml >> ../${STAGE_NAME}.log 2>&1
        """
    }
}

def executeTestCommand(String osName)
{
    switch(osName)
    {
    case 'Windows':
        bat """
            ..\\Bin\\Release\\x64\\BaikalTest64.exe --gtest_output=xml:../${STAGE_NAME}.gtest.xml >> ..\\${STAGE_NAME}.log 2>&1
        """
        break;
    case 'OSX':
        sh """
            ../Bin/Release/x64/BaikalTest64 --gtest_output=xml:../${STAGE_NAME}.gtest.xml >> ../${STAGE_NAME}.log 2>&1
        """
        break;
    default:
        sh """
            export LD_LIBRARY_PATH=`pwd`/../Bin/Release/x64/:\${LD_LIBRARY_PATH}
            ../Bin/Release/x64/BaikalTest64 --gtest_output=xml:../${STAGE_NAME}.gtest.xml >> ../${STAGE_NAME}.log 2>&1
        """
    }
}

def executeTests(String osName, String asicName, Map options)
{
    String REF_PATH_PROFILE="${options.REF_PATH}/${asicName}-${osName}"
    String JOB_PATH_PROFILE="${options.JOB_PATH}/${asicName}-${osName}"

    try {
        checkOutBranchOrScm(options['projectBranch'], options['projectRepo'])

        outputEnvironmentInfo(osName)
        unstash "app${osName}"
        
        dir('BaikalTest')
        {
            if(options['updateRefs'])
            {
                executeGenTestRefCommand(osName)
                sendFiles('./ReferenceImages/*.*', "${REF_PATH_PROFILE}")
            }
            else
            {
                receiveFiles("${REF_PATH_PROFILE}/*", './ReferenceImages/')
                executeTestCommand(osName)
            }
        }                    
    }
    catch (e) {
        println(e.toString());
        println(e.getMessage());
        //println(e.getStackTrace());    
        
        dir('BaikalTest')
        {
            sendFiles('./ReferenceImages/*.*', "${JOB_PATH_PROFILE}/ReferenceImages")
            sendFiles('./OutputImages/*.*', "${JOB_PATH_PROFILE}/OutputImages")
        }
        currentBuild.result = "FAILED"
        throw e
    }
    finally {
        archiveArtifacts "*.log"
        junit "*.gtest.xml"
    }
}

def executeBuildWindows()
{
    bat """
    HOSTNAME > ${STAGE_NAME}.log
    set msbuild="C:\\Program Files (x86)\\MSBuild\\14.0\\Bin\\MSBuild.exe"
    if not exist %msbuild% (
        set msbuild="C:\\Program Files (x86)\\Microsoft Visual Studio\\2017\\Community\\MSBuild\\15.0\\Bin\\MSBuild.exe"
    )
    set target=build
    set maxcpucount=/maxcpucount 
    set PATH=C:\\Python27\\;%PATH%
    .\\Tools\\premake\\win\\premake5 vs2015 >> ${STAGE_NAME}.log 2>&1
    set solution=.\\Baikal.sln
    %msbuild% /target:%target% %maxcpucount% /property:Configuration=Release;Platform=x64 %parameters% %solution% >> ${STAGE_NAME}.log 2>&1
    """
}

def executeBuildOSX()
{
    sh """
    uname -a > ${STAGE_NAME}.log
    Tools/premake/osx/premake5 gmake >> ${STAGE_NAME}.log 2>&1
    make config=release_x64          >> ${STAGE_NAME}.log 2>&1
    """
}

def executeBuildLinux()
{
    sh """
    uname -a > ${STAGE_NAME}.log
    chmod +x Tools/premake/linux64/premake5
    Tools/premake/linux64/premake5 gmake    >> ${STAGE_NAME}.log 2>&1
    make config=release_x64                 >> ${STAGE_NAME}.log 2>&1
    """
}
def executeBuild(String osName, Map options)
{
    try {
        checkOutBranchOrScm(options['projectBranch'], options['projectRepo'])
        outputEnvironmentInfo(osName)

        switch(osName)
        {
        case 'Windows': 
            executeBuildWindows(); 
            break;
        case 'OSX':
            executeBuildOSX();
            break;
        default: 
            executeBuildLinux();
        }
        
        stash includes: 'Bin/**/*', name: "app${osName}"
    }
    catch (e) {
        currentBuild.result = "FAILED"
        throw e
    }
    finally {
        archiveArtifacts "${STAGE_NAME}.log"
    }                        

}

def executeDeploy(Map options, List platformList, List testResultList)
{
}

def call(String projectBranch = "", 
         String platforms = 'Windows:AMD_RXVEGA,AMD_WX9100,AMD_WX7100,NVIDIA_GF1080TI;OSX:Intel_Iris;Ubuntu:AMD_WX7100;CentOS7', 
         String PRJ_ROOT='rpr-core',
         String PRJ_NAME='RadeonProRender-Baikal',
         String projectRepo='https://github.com/GPUOpen-LibrariesAndSDKs/RadeonProRender-Baikal.git',
         Boolean updateRefs = false, 
         Boolean enableNotifications = true) {
    
    multiplatform_pipeline(platforms, null, this.&executeBuild, this.&executeTests, null,
                           [projectBranch:projectBranch,
                            updateRefs:updateRefs, 
                            enableNotifications:enableNotifications,
                            PRJ_NAME:PRJ_NAME,
                            PRJ_ROOT:PRJ_ROOT,
                            projectRepo:projectRepo,
                            BUILDER_TAG:'BuilderS',
                            executeBuild:true,
                            executeTests:true,
                            slackChannel:"${SLACK_BAIKAL_CHANNEL}",
                            slackBaseUrl:"${SLACK_BAIKAL_BASE_URL}",
                            slackTocken:"${SLACK_BAIKAL_TOCKEN}"])
}
