package org.company

class DeploymentManager implements Serializable {

    private def steps
    private String environment

    DeploymentManager(def steps, String environment) {
        if (!environment) {
            throw new IllegalArgumentException("Environment cannot be null or empty.")
        }
        this.steps = steps
        this.environment = environment.toLowerCase()
    }

    def validate() {
        steps.echo "--- Starting Validation Stage ---"
        if (!['dev', 'staging', 'prod'].contains(this.environment)) {
            steps.error "Deployment failed: Invalid environment '${this.environment}'. Must be dev, staging, or prod."
        }
        steps.echo "Validation successful! Target environment: ${this.environment.toUpperCase()}"
    }

    def deploy() {
        steps.echo "--- Starting Deployment Stage ---"
        steps.echo "Deploying applications to ${this.environment.toUpperCase()} environment..."

        switch(this.environment) {
            case 'dev':
                steps.echo "Applying dev configurations... Fast tracking deployment."
                // steps.sh "kubectl apply -f k8s/dev/"
                break
            case 'staging':
                steps.echo "Applying staging configurations... Running integration tests."
                // steps.sh "kubectl apply -f k8s/staging/"
                break
            case 'prod':
                steps.echo "CRITICAL: Preparing Production Deployment!"
                steps.echo "Applying prod configurations with high-availability settings."
                // steps.sh "kubectl apply -f k8s/prod/"
                break
            default:
                steps.error "Unknown environment: ${this.environment}. Aborting."
                break
        }
        steps.echo "Deployment to ${this.environment.toUpperCase()} completed successfully."
    }

    def rollback() {
        steps.echo "--- Triggering Rollback Logic ---"
        steps.echo "Rollback initiated for ${this.environment.toUpperCase()} environment."
        // steps.sh "kubectl rollout undo deployment/backend-service -n ${this.environment}"
        steps.echo "Rollback completed successfully."
    }
}
