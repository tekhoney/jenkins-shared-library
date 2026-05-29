package org.company

class DeploymentManager implements Serializable {

    String environment
    def steps

    // Constructor
    DeploymentManager(steps, String environment) {
        this.steps = steps
        this.environment = environment
    }

    // Validation Method
    def validate() {

        steps.echo "================================="
        steps.echo "VALIDATION STARTED"
        steps.echo "Environment: ${environment}"
        steps.echo "================================="

        steps.sh """
            if [ ! -f index.html ]; then
                echo "Application file missing!"
                exit 1
            fi
        """

        steps.echo "Validation Successful"
    }

    // Deploy Method
    def deploy() {

        steps.echo "================================="
        steps.echo "DEPLOYMENT STARTED"
        steps.echo "Environment: ${environment}"
        steps.echo "================================="

    // Create backup folder
    steps.sh """
        mkdir -p deployments/${environment}/backup
    """

    // Backup old deployment if exists
    steps.sh """
        cp deployments/${environment}/index.html \
        deployments/${environment}/backup/index.html \
        2>/dev/null || true
    """

    // Deploy new application
    steps.sh """
        cp index.html deployments/${environment}/
    """

    // Simulate deployment failure for Version 2
    steps.sh """

        if grep -q "Version 2" index.html; then

            echo "================================="
            echo "DEPLOYMENT FAILED"
            echo "Bad Version Detected"
            echo "================================="

            exit 1
        fi
    """

    steps.echo "Deployment Successful"
}

    // Recreate Rollback Method
    def rollback() {

        steps.echo "================================="
        steps.echo "ROLLBACK STARTED"
        steps.echo "Environment: ${environment}"
        steps.echo "================================="

        // Remove failed deployment
        steps.sh """
            rm -f deployments/${environment}/index.html
        """

        // Restore previous version
        steps.sh """
            cp deployments/${environment}/backup/index.html \
            deployments/${environment}/
        """

        steps.echo "Rollback Completed Successfully"
    }
}
