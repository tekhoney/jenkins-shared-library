package org.company

class DeploymentManager implements Serializable {

    String env

    DeploymentManager(String env) {
        this.env = env
    }

    def validate() {

        println "================================="
        println "Validating deployment for ${env}"
        println "================================="

        if(!(env in ['dev','staging','prod'])) {
            throw new Exception("Invalid Environment")
        }
    }

    def deploy(script) {

        println "Deploying to ${env}"

        switch(env) {

            case "dev":

                script.sh '''
                docker rm -f salary-dev || true
                docker run -d \
                --name salary-dev \
                -p 8081:8080 \
                salary-service:${BUILD_NUMBER}
                '''
                break

            case "staging":

                script.sh '''
                docker rm -f salary-staging || true
                docker run -d \
                --name salary-staging \
                -p 8082:8080 \
                salary-service:${BUILD_NUMBER}
                '''
                break

            case "prod":

                script.sh '''
                docker rm -f salary-prod || true
                docker run -d \
                --name salary-prod \
                -p 8083:8080 \
                salary-service:${BUILD_NUMBER}
                '''
                break
        }
    }

    def rollback(script) {

        println "Rolling back ${env}"

        script.sh """

        docker rm -f salary-${env} || true

        docker run -d \
        --name salary-${env} \
        -p 8080:8080 \
        salary-service:previous

        """
    }
}
