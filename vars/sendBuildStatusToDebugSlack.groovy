def call(Map options) {
    String BRANCH_NAME = env.BRANCH_NAME ?: options.projectBranch

    if (currentBuild.result == "FAILURE") {

        String debagSlackMessage = """[{
          "title": "${env.JOB_NAME} [${env.BUILD_NUMBER}]",
          "title_link": "${env.BUILD_URL}",
          "color": "#fc0356",
          "pretext": "${currentBuild.result}",
          "text": "Failed in:  ${options.FAILED_STAGES.join("\n")}"
          }]
        """;
        // TODO: foreach
        /*
        "fields": [ { "title": '', value: '', short: "false"}, ... ]
         */

        try {
            if (BRANCH_NAME == "master" || env.CHANGE_BRANCH || env.JOB_NAME.contains("Weekly")) {
                slackSend(attachments: debagSlackMessage, channel: 'cis_failed_master', baseUrl: env.debagUrl, tokenCredentialId: 'debug-channel-master')
            } else {
                slackSend (attachments: debagSlackMessage, channel: env.debagChannel, baseUrl: env.debagUrl, tokenCredentialId: 'debug-channel')
            }
        } catch (e) {
            println("Error during slack notification to debug channel")
            println(e.toString())
        }
    }
}
