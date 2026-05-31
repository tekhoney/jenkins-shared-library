package org.company

class DeploymentManager implements Serializable {
    
    private def steps
    private String env
    private String appName = "attendance"

    // Constructor accepting the Jenkins pipeline context ('this') and environment
    DeploymentManager(def steps, String env) {
        this.steps = steps
        this.env = env.toLowerCase()
    }

    // Validate if the environment is supported
    def validate() {
        steps.stage("Validate Environment") {
            steps.echo "Checking prerequisites for ${appName}..."
            if (!(env in ['dev', 'staging', 'prod'])) {
                steps.error "Deployment failed: Unsupported environment '${env}'!"
            }
            steps.echo "Validation successful for [${env.toUpperCase()}] environment."
        }
    }

    // Core Deployment Logic
    def deploy() {
        steps.stage("Deploy to ${env.toUpperCase()}") {
            steps.echo "Deploying ${appName} to ${env} environment..."
            
            // Environment-specific configurations
            switch(env) {
                case 'dev':
                    steps.echo "Executing Dev Deployment: Deploying to local minikube/dev cluster..."
                    steps.echo "Applying manifests: kubectl apply -f k8s/attendance-dev.yaml"
                    break
                case 'staging':
                    steps.echo "Executing Staging Deployment: Running integration smoke tests..."
                    steps.echo "Applying manifests: kubectl apply -f k8s/attendance-stage.yaml"
                    break
                case 'prod':
                    steps.echo "⚠️ CRITICAL: Deploying ${appName} to PRODUCTION ⚠️"
                    steps.echo "Applying manifests: kubectl apply -f k8s/attendance-prod.yaml"
                    break
            }
            steps.echo "Successfully deployed ${appName} to ${env.toUpperCase()}."
        }
    }

    // Rollback Logic using the 'Recreate' strategy
    def rollback() {
        steps.stage("Rollback - Recreate Strategy") {
            steps.echo "🚨 Deployment failed or aborted! Initiating Recreate Rollback for ${appName} in ${env}..."
            
            // Recreate Strategy: Hard delete existing deployment instances and spin them back up clean
            steps.echo "Step 1: Terminating existing ${appName} pods/deployments to clear bad state..."
            steps.echo "Command: kubectl delete deployment ${appName}-deployment --namespace=${env}"
            
            steps.echo "Step 2: Re-applying last known stable configuration..."
            steps.echo "Command: kubectl apply -f k8s/attendance-${env}-stable.yaml"
            
            steps.echo "Rollback complete. ${appName} has been recreated in ${env}."
        }
    }
}
