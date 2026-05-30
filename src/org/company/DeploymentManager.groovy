package org.company

class DeploymentManager implements Serializable {
    // Jenkins steps context required to run native pipeline steps (like echo, sh, error)
    private def steps
    private String environment

    // Constructor that accepts the Jenkins steps context and the environment parameter
    DeploymentManager(def steps, String environment) {
        this.steps = steps
        this.environment = environment.toLowerCase()
    }

    // Method to validate environment configuration
    def validate() {
        steps.echo "--- Starting Validation Stage ---"
        if (!['dev', 'staging', 'prod'].contains(this.environment)) {
            steps.error "Deployment failed: Invalid environment '${this.environment}'. Must be dev, staging, or prod."
        }
        steps.echo "Validation successful! Target environment: ${this.environment.toUpperCase()}"
    }

    // Method to handle deployment logic based on environment
    def deploy() {
        steps.echo "--- Starting Deployment Stage ---"
        steps.echo "Deploying applications to ${this.environment.toUpperCase()} environment..."
        
        // Environment-specific configurations
        switch(this.environment) {
            case 'dev':
                steps.echo "Applying dev configurations... Fast tracking deployment without manual approval."
                // Example: steps.sh "kubectl apply -f k8s/dev/"
                break
            case 'staging':
                steps.echo "Applying staging configurations... Running automated integration tests pre-deploy."
                // Example: steps.sh "kubectl apply -f k8s/staging/"
                break
            case 'prod':
                steps.echo "CRITICAL: Preparing Production Deployment!"
                steps.echo "Applying prod configurations with high-availability configurations."
                // Example: steps.sh "kubectl apply -f k8s/prod/"
                break
        }
        steps.echo "Deployment to ${this.environment.toUpperCase()} completed successfully."
    }

    // Method to handle rollback logic in case of failure
    def rollback() {
        steps.echo "--- Triggering Rollback Logic ---"
        steps.echo "Rollback initiated for ${this.environment.toUpperCase()} environment."
        // Example: steps.sh "kubectl rollout undo deployment/backend-service -n ${this.environment}"
        steps.echo "Rollback completed successfully."
    }
}
