

def executeTests(operations) {
	
	def zip_one_exists = fileExists '1.zip'
	if (!zip_one_exists) {
		try {
			echo "Downloading 1st zip test package"
			bat '''
				wget --no-check-certificate "https://render.cis.luxoft.com/media/1.zip"
			'''
		} catch (e) {
			println(e.toString());
			println(e.getMessage());
			println(e.getStackTrace());
			throw e	
		}
	}
	
	def zip_two_exists = fileExists '2.zip'
	if (!zip_two_exists) {
		try {
			echo "Downloading 2nd zip test package"
			bat '''
				wget --no-check-certificate "https://render.cis.luxoft.com/media/2.zip"
			'''
		} catch (e) {
			println(e.toString());
			println(e.getMessage());
			println(e.getStackTrace());
			throw e	
		}
	}
	
	if (operations.contains("stash")) {
		print("Stashing test")
		stash includes: "1.zip", name: "1.zip", allowEmpty: true
		stash includes: "2.zip", name: "2.zip", allowEmpty: true
	}

	if (operations.contains("unstash")) {
		print("Unstashing test")
		unstash "1.zip"
		unstash "2.zip"
	}
	
	if (operations.contains("archiveArtifacts")) {
		print("archiveArtifacts test")
		archiveArtifacts "1.zip"
		archiveArtifacts "2.zip"		
	}
}

def main(label, operations, type) {
	try {
		timestamps {
			
			def testers = [:]
			def label_ = label
			testers[label_] = {
				node(label_) {
					stage("Test"){
						timeout(time: 360, unit: 'MINUTES'){
							ws("WS/TestOps") {
								executeTests(operations)
							}
						}
				    }
				}
			}

			parallel testers
		}			  
	} catch (e) {
		println(e.toString());
		println(e.getMessage());
		println(e.getStackTrace());
		currentBuild.result = "FAILED"
		throw e
	}
}
	
def call(
		String label = "",
		String operations = "",
		String type = ""
		) {
	  	main(label, operations, type)
	}


