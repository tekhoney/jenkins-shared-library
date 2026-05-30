// vars/deployOTService.groovy
import org.company.DeploymentManager

/**
 * Global pipeline step callable from any Jenkinsfile:
 *
 *   deployOTService(
 *       environment : 'dev',
 *       serviceName : 'employee',
 *       imageTag    : 'v1.2.0'          // optional
 *   )
 */
def call(Map config) {
    def env         = config.environment ?: error('deployOTService: environment is required')
    def serviceName = config.serviceName ?: error('deployOTService: serviceName is required')
    def imageTag    = config.imageTag    ?: null

    def manager = new DeploymentManager(this, env)

    try {
        manager.validate()
        manager.deploy(serviceName, imageTag)
    } catch (Exception e) {
        echo "⚠ Deployment failed: ${e.message}"
        echo "⚠ Initiating Recreate Rollback for '${serviceName}' in '${env}'..."
        manager.rollback(serviceName, imageTag)
        error("Pipeline failed after rollback. Original error: ${e.message}")
    }
}
