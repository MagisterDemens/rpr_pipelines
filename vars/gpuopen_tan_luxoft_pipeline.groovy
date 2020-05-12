import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

def executeTests(String osName, String asicName, Map options) {
    
}

def executeBuildWindows(Map options) {

    receiveFiles("gpuopen/OpenCL-Headers/*", './thirdparty/OpenCL-Headers')
    receiveFiles("gpuopen/portaudio/*", './thirdparty/portaudio')
    receiveFiles("gpuopen/fftw-3.3.5-dll64/*", './tan/tanlibrary/src/fftw-3.3.5-dll64')

    bat """
        mkdir thirdparty\\Qt\\Qt5.9.9\\5.9.9\\msvc2017_64
        xcopy C:\\Qt\\Qt5.9.9\\5.9.9\\msvc2017_64 thirdparty\\Qt\\Qt5.9.9\\5.9.9\\msvc2017_64 /s/y/i
    """

    options.buildConfiguration.each() { win_build_conf ->
        options.ipp.each() { win_cur_ipp ->
            options.winTool.each() { win_tool ->
                options.winVisualStudioVersion.each() { vs_ver ->
                    options.winRTQ.each() { win_cur_rtq ->
    
                        win_build_conf = win_build_conf.capitalize()

                        println "Current build configuration: ${win_build_conf}."
                        println "Current ipp: ${win_cur_ipp}."
                        println "Current tool: ${win_tool}."
                        println "Current VS version: ${vs_ver}."
                        println "Current rtq: ${win_cur_rtq}."

                        win_build_name = "${win_build_conf}_vs${vs_ver}_${win_tool}_ipp-${win_cur_ipp}_rtq-${win_cur_rtq}"

                        if (win_cur_ipp == "on"){
                            println "add ipp flag"
                        } else {
                            println "nothing to do"
                        }

                        if (win_cur_rtq == "on"){
                            win_cur_rtq = 1
                        } else {
                            win_cur_rtq = 0
                        }

                        switch(vs_ver) {
                            case '2015':
                                options.visualStudio = "Visual Studio 14 2015"
                                options.msBuildPath = "C:\\Program Files (x86)\\MSBuild\\14.0\\Bin\\MSBuild.exe"
                                break;
                            case '2017':
                                options.visualStudio = "Visual Studio 15 2017"
                                options.msBuildPath = "C:\\Program Files (x86)\\Microsoft Visual Studio\\2017\\Community\\MSBuild\\15.0\\Bin\\MSBuild.exe"
                                break;
                            case '2019':
                                options.visualStudio = "Visual Studio 16 2019"
                                options.msBuildPath = "C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\Community\\MSBuild\\Current\\Bin\\MSBuild.exe"
                        }

                        dir('tan\\build\\cmake') {

                            if(fileExists("vs${vs_ver}")){
                                bat """
                                    rd /s /q "vs${vs_ver}"
                                    mkdir "vs${vs_ver}"
                                """
                            }
                            
                            options.win_openCL_dir = "..\\..\\..\\..\\thirdparty\\OpenCL-Headers"
                            options.win_portaudio_dir = "..\\..\\..\\..\\..\\thirdparty\\portaudio"

                            try {
                                dir ("vs${vs_ver}") {
                                    bat """
                                        SET CMAKE_PREFIX_PATH=..\\..\\..\\..\\thirdparty\\Qt\\Qt5.9.9\\5.9.9\\msvc2017_64\\lib\\cmake\\Qt5Widgets
                                        cmake .. -G "${options.visualStudio}" -A x64 -DCMAKE_BUILD_TYPE=${win_build_conf} -DOpenCL_INCLUDE_DIR="${options.win_openCL_dir}" -DPortAudio_DIR="${options.win_portaudio_dir}" -DDEFINE_AMD_OPENCL_EXTENSION=1 -DRTQ_ENABLED=${win_cur_rtq} >> ..\\..\\..\\..\\${STAGE_NAME}.${win_build_name}.log 2>&1
                                    """
                                }
          
                                if (win_tool == "msbuild") {
                                    dir ("vs${vs_ver}") {
                                        bat """
                                            set msbuild="${options.msBuildPath}"
                                            %msbuild% TAN.sln /target:build /maxcpucount /property:Configuration=${win_build_conf};Platform=x64 >> ..\\..\\..\\..\\${STAGE_NAME}.${win_build_name}.log 2>&1
                                        """
                                    }
                                } else if (win_tool == "cmake") {
                                    bat """
                                        cmake --build vs${vs_ver} --config ${win_build_conf} >> ..\\..\\..\\${STAGE_NAME}.${win_build_name}.log 2>&1
                                    """
                                }
                                
                                bat """
                                    mkdir bin
                                    copy vs${vs_ver}\\cmake-TAN-bin\\${win_build_conf}\\TrueAudioNext.dll bin
                                    copy vs${vs_ver}\\cmake-TrueAudioVR-bin\\${win_build_conf}\\TrueAudioVR.dll bin
                                    copy vs${vs_ver}\\cmake-GPUUtilities-bin\\${win_build_conf}\\GPUUtilities.dll bin
                                    copy ..\\..\\..\\thirdparty\\Qt\\Qt5.9.9\\5.9.9\\msvc2017_64\\bin\\Qt5Core*.dll bin
                                    copy ..\\..\\..\\thirdparty\\Qt\\Qt5.9.9\\5.9.9\\msvc2017_64\\bin\\Qt5Widgets*.dll bin
                                    copy ..\\..\\..\\thirdparty\\Qt\\Qt5.9.9\\5.9.9\\msvc2017_64\\bin\\Qt5Gui*.dll bin
                                    copy ..\\..\\..\\thirdparty\\portaudio\\build\\msvc\\x64\\Debug\\portaudio_x64.dll bin
                                    copy vs${vs_ver}\\cmake-TALibDopplerTest-bin\\${win_build_conf}\\TALibDopplerTest.exe bin
                                    copy vs${vs_ver}\\cmake-TALibTestConvolution-bin\\${win_build_conf}\\TALibTestConvolution.exe bin
                                    copy vs${vs_ver}\\cmake-RoomAcousticQT-bin\\${win_build_conf}\\RoomAcousticsQT.exe bin
                                """
                                zip archive: true, dir: 'bin', glob: '', zipFile: "Windows_${win_build_name}.zip"

                            } catch (FlowInterruptedException error) {
                                println "[INFO] Job was aborted during build stage"
                                throw error
                            } catch (e) {
                                println(e.toString());
                                println(e.getMessage());
                                currentBuild.result = "FAILED"
                                println "[ERROR] Failed to build TAN on Windows"
                            }
                        }
                    }
                }
            }
        }
    }
}

def executeBuildOSX(Map options) {

    receiveFiles("gpuopen/OpenCL-Headers/*", './thirdparty/OpenCL-Headers')
    receiveFiles("gpuopen/portaudio/*", './thirdparty/portaudio')
    receiveFiles("gpuopen/fftw-3.3.5/*", './tan/tanlibrary/src/fftw-3.3.5')

    options.buildConfiguration.each() { osx_build_conf ->
        options.ipp.each() { osx_cur_ipp ->
            options.osxTool.each() { osx_tool ->

                osx_build_conf = osx_build_conf.capitalize()

                println "Current build configuration: ${osx_build_conf}."
                println "Current ipp: ${osx_cur_ipp}."
                println "Current tool: ${osx_tool}."

                osx_build_name = "${osx_build_conf}_${osx_tool}_ipp-${osx_cur_ipp}"

                if (osx_cur_ipp == "ipp"){
                    println "add ipp flag"
                } else {
                    println "add fftw"
                }

                dir('tan\\build\\cmake') {
                    sh """
                        rm -rf ./macos
                        mkdir macos
                    """
                    dir("macos") {
                        try {
                            options.osx_cmake = "/usr/local/Cellar/qt/5.13.1"
                            options.osx_opencl_headers = "../../../../thirdparty/OpenCL-Headers"
                            options.osx_portaudio = "../../../../../thirdparty/portaudio"

                            if (osx_tool == "cmake") {
                                sh """
                                    cmake .. -DCMAKE_BUILD_TYPE=${osx_build_conf} -DCMAKE_PREFIX_PATH="${options.osx_cmake}" -DOpenCL_INCLUDE_DIR="${options.osx_opencl_headers}" -DPortAudio_DIR="${options.osx_portaudio}" -DDEFINE_AMD_OPENCL_EXTENSION=1 >> ../../../../${STAGE_NAME}.${osx_build_name}.log 2>&1
                                """
                            } else if (osx_tool == "xcode") {
                                sh """
                                    cmake -G "Xcode" .. -DCMAKE_PREFIX_PATH="${options.osx_cmake}" -DOpenCL_INCLUDE_DIR="${options.osx_opencl_headers}" -DPortAudio_DIR="${options.osx_portaudio}" -DDEFINE_AMD_OPENCL_EXTENSION=1  >> ../../../../${STAGE_NAME}.${osx_build_name}.log 2>&1
                                """
                            }
                            
                            sh """
                                make VERBOSE=1 >> ../../../../${STAGE_NAME}.${osx_build_name}.log 2>&1
                            """

                            sh """
                                mkdir bin
                                cp cmake-TAN-bin/libTrueAudioNext.dylib bin
                                cp cmake-TrueAudioVR-bin/libTrueAudioVR.dylib bin
                                cp cmake-GPUUtilities-bin/libGPUUtilities.dylib bin
                                cp cmake-TALibDopplerTest-bin/TALibDopplerTest bin
                                cp cmake-TALibTestConvolution-bin/TALibTestConvolution bin
                                cp cmake-RoomAcousticQT-bin/RoomAcousticsQT bin
                            """
                            zip archive: true, dir: 'bin', glob: '', zipFile: "OSX_${osx_build_name}.zip"

                        } catch (FlowInterruptedException error) {
                            println "[INFO] Job was aborted during build stage"
                            throw error
                        } catch (e) {
                            println(e.toString());
                            println(e.getMessage());
                            currentBuild.result = "FAILED"
                            println "[ERROR] Failed to build TAN on OSX"
                        } 
                    }
                }
            }
        }
    }
}

def executeBuildLinux(String osName, Map options) {

    receiveFiles("gpuopen/OpenCL-Headers/*", './thirdparty/OpenCL-Headers')
    receiveFiles("gpuopen/portaudio/*", './thirdparty/portaudio')
    receiveFiles("gpuopen/fftw-3.3.5/*", './tan/tanlibrary/src/fftw-3.3.5')

    options.buildConfiguration.each() { ub18_build_conf ->
        options.ipp.each() { ub18_cur_ipp ->

            ub18_build_conf = ub18_build_conf.capitalize()

            println "Current build configuration: ${ub18_build_conf}."
            println "Current ipp: ${ub18_cur_ipp}."

            ub18_build_name = "${ub18_build_conf}_ipp-${ub18_cur_ipp}"

            if (ub18_cur_ipp == "ipp"){
                println "add ipp flag"
            } else {
                println "add fftw"
            }

            dir('tan\\build\\cmake') {
                sh """
                    rm -rf ./linux
                    mkdir linux
                """
                dir ("linux") {
                    try {
                        options.ub18_cmake = "/usr/bin/gcc"
                        options.ub18_opencl_headers = "../../../../thirdparty/OpenCL-Headers"
                        options.ub18_opencl_lib = "/usr/lib/x86_64-linux-gnu/libOpenCL.so"
                        options.ub18_portaudio = "../../../../../thirdparty/portaudio"

                        sh """
                            cmake .. -DCMAKE_BUILD_TYPE=${ub18_build_conf} -DCMAKE_PREFIX_PATH="${options.ub18_cmake}" -DOpenCL_INCLUDE_DIR="${options.ub18_opencl_headers}" -DOpenCL_LIBRARY="${options.ub18_opencl_lib}" -DPortAudio_DIR="${options.ub18_portaudio}" -DDEFINE_AMD_OPENCL_EXTENSION=1 >> ../../../../${STAGE_NAME}.${ub18_build_name}.log 2>&1
                        """

                        sh """
                            make VERBOSE=1 >> ../../../../${STAGE_NAME}.${ub18_build_name}.log 2>&1
                        """

                        sh """
                            mkdir bin
                            cp cmake-TAN-bin/libTrueAudioNext.so bin
                            cp cmake-TrueAudioVR-bin/libTrueAudioVR.so bin
                            cp cmake-GPUUtilities-bin/libGPUUtilities.so bin
                            cp cmake-TALibDopplerTest-bin/TALibDopplerTest bin
                            cp cmake-TALibTestConvolution-bin/TALibTestConvolution bin
                            cp cmake-RoomAcousticQT-bin/RoomAcousticsQT bin
                        """
                        zip archive: true, dir: 'bin', glob: '', zipFile: "Ubuntu18_${ub18_build_name}.zip"

                    } catch (FlowInterruptedException error) {
                        println "[INFO] Job was aborted during build stage"
                        throw error
                    } catch (e) {
                        println(e.getMessage())
                        currentBuild.result = "FAILED"
                        println "[ERROR] Failed to build TAN on Ubuntu18"
                    } 
                }
            }
        }
    }
}

def executeBuild(String osName, Map options) {
    try {

        cleanWS(osName)
        checkOutBranchOrScm(options['projectBranch'], 'git@github.com:imatyushin/TAN.git', true)
        
        switch(osName)
        {
            case 'Windows':
                executeBuildWindows(options);
                break;
            case 'OSX':
                withEnv(["PATH=$WORKSPACE:$PATH"]) {
                    executeBuildOSX(options);
                }
                break;
            default:
                withEnv(["PATH=$PWD:$PATH"]) {
                    executeBuildLinux(osName, options);
                }
        }
    } catch (e) {
        options.failureMessage = "[ERROR] Failed to build plugin on ${osName}"
        options.failureError = e.getMessage()
        currentBuild.result = "FAILED"
        throw e
    } finally {
        archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
    }
}

def executePreBuild(Map options) {

    // manual job
    if (options.forceBuild) {
        options.executeBuild = true
        options.executeTests = true
    // auto job
    } else {
        options.executeBuild = true
        options.executeTests = true
        if (env.CHANGE_URL)
        {
            println "[INFO] Branch was detected as Pull Request"
            options.isPR = true
            options.testsPackage = "PR"
        }
        else if("${env.BRANCH_NAME}" == "master")
        {
           println "[INFO] master branch was detected"
           options.testsPackage = "master"
        } else {
            println "[INFO] ${env.BRANCH_NAME} branch was detected"
            options.testsPackage = "smoke"
        }
    }

    if(!env.CHANGE_URL){

        checkOutBranchOrScm(env.BRANCH_NAME, 'git@github.com:imatyushin/TAN.git', true)

        options.commitAuthor = bat (script: "git show -s --format=%%an HEAD ",returnStdout: true).split('\r\n')[2].trim()
        options.commitMessage = bat (script: "git log --format=%%B -n 1", returnStdout: true).split('\r\n')[2].trim()
        options.commitSHA = bat(script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()
        options.commitShortSHA = options.commitSHA[0..6]

        println "The last commit was written by ${options.commitAuthor}."
        println "Commit message: ${options.commitMessage}"
        println "Commit SHA: ${options.commitSHA}"
        println "Commit shortSHA: ${options.commitShortSHA}"

        try {
            def major = version_read("${env.WORKSPACE}\\tan\\tanlibrary\\include\\TrueAudioNext.h", '#define TAN_VERSION_MAJOR')
            def minor = version_read("${env.WORKSPACE}\\tan\\tanlibrary\\include\\TrueAudioNext.h", '#define TAN_VERSION_MINOR')
            def release = version_read("${env.WORKSPACE}\\tan\\tanlibrary\\include\\TrueAudioNext.h", '#define TAN_VERSION_RELEASE')
            def build = version_read("${env.WORKSPACE}\\tan\\tanlibrary\\include\\TrueAudioNext.h", '#define TAN_VERSION_BUILD')
            options.tanVersion = "${major}.${minor}.${release}.${build}"
        } catch (e) {
            println "[WARNING] Can't detect TAN version"
            options.tanVersion = "0"
        }

        currentBuild.description += "<b>Project branch:</b> ${options.projectBranch}<br/>"
        currentBuild.description += "<b>Version:</b> ${options.tanVersion}<br/>"
        currentBuild.description += "<b>Commit author:</b> ${options.commitAuthor}<br/>"
        currentBuild.description += "<b>Commit message:</b> ${options.commitMessage}<br/>"
        currentBuild.description += "<b>Commit SHA:</b> ${options.commitShortSHA}<br/>"
        
        if (options.incrementVersion) {
            if ("${options.commitAuthor}" != "radeonprorender") {
                
                println "[INFO] Incrementing version of change made by ${options.commitAuthor}."
                
                def current_version=version_read("${env.WORKSPACE}\\tan\\tanlibrary\\include\\TrueAudioNext.h", '#define TAN_VERSION_BUILD')
                println "[INFO] Current build version: ${current_version}"
                
                def new_version = version_inc(current_version, 1)
                println "[INFO] New build version: ${new_version}"

                version_write("${env.WORKSPACE}\\tan\\tanlibrary\\include\\TrueAudioNext.h", '#define TAN_VERSION_BUILD', new_version)
                def updated_version = version_read("${env.WORKSPACE}\\tan\\tanlibrary\\include\\TrueAudioNext.h", '#define TAN_VERSION_BUILD')
                println "[INFO] Updated build version: ${updated_version}"

                bat """
                    git add tan\\tanlibrary\\include\\TrueAudioNext.h
                    git commit -m "buildmaster: automatic build version update to ${updated_version}"
                    git push origin HEAD:${env.BRANCH_NAME}
                """

                //get commit's sha which have to be build
                options.projectBranch = bat (script: "git log --format=%%H -1", returnStdout: true).split('\r\n')[2].trim()
            } 
        }
    }

    if (env.BRANCH_NAME && env.BRANCH_NAME == "master") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '25']]]);
    } else if (env.BRANCH_NAME && BRANCH_NAME != "master") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '3']]]);
    } else {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '20']]]);
    }
}

def executeDeploy(Map options, List platformList, List testResultList) {
    //TODO add deploy logic
}


def call(String projectBranch = "",
    String testsBranch = "master",
    String platforms = 'Windows;OSX;Ubuntu18',
    String buildConfiguration = "release",
    String ipp = "off",
    String winTool = "msbuild",
    String winVisualStudioVersion = "2017,2019",
    String winRTQ = "on,off",
    String osxTool = "cmake",
    Boolean enableNotifications = true,
    Boolean incrementVersion = true,
    Boolean forceBuild = false,
    Boolean skipBuild = false,
    String testsPackage = "",
    String tests = "") {
    try {
        String PRJ_NAME="TAN_Dev"
        String PRJ_ROOT="GPUOpen"

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

        buildConfiguration = buildConfiguration.split(',')
        ipp = ipp.split(',')
        winTool = winTool.split(',')
        winVisualStudioVersion = winVisualStudioVersion.split(',')
        winRTQ = winRTQ.split(',')
        osxTool = osxTool.split(',')

        println "Build configuration: ${buildConfiguration}"
        println "IPP: ${ipp}"
        println "Win visual studio version: ${winVisualStudioVersion}"
        println "Win tool: ${winTool}"
        println "Win RQT: ${winRTQ}"
        println "OSX tool: ${osxTool}"

        multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy,
                               [projectBranch:projectBranch,
                                testsBranch:testsBranch,
                                enableNotifications:enableNotifications,
                                incrementVersion:incrementVersion,
                                forceBuild:forceBuild,
                                skipBuild:skipBuild,
                                PRJ_NAME:PRJ_NAME,
                                PRJ_ROOT:PRJ_ROOT,
                                buildConfiguration:buildConfiguration,
                                ipp:ipp,
                                winTool:winTool,
                                winVisualStudioVersion:winVisualStudioVersion,
                                winRTQ:winRTQ,
                                osxTool:osxTool,
                                testsPackage:testsPackage,
                                tests:tests,
                                gpusCount:gpusCount,
                                TEST_TIMEOUT:90,
                                DEPLOY_TIMEOUT:150,
                                BUILDER_TAG:"BuilderT",
                                ])
    } catch(e) {
        currentBuild.result = "FAILED"
        failureMessage = "INIT FAILED"
        failureError = e.getMessage()
        println(e.toString());
        println(e.getMessage());

        throw e
    }
}
