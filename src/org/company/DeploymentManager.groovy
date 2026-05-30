// src/org/company/DeploymentManager.groovy
package org.company

class DeploymentManager implements Serializable {

    // ── Fields ────────────────────────────────────────────────────────────────
    private final Script steps            // Jenkins pipeline DSL context
    private final String environment      // dev | staging | prod
    private final Map    envConfig        // per-env settings

    // ── Constructor ───────────────────────────────────────────────────────────
    /**
     * @param steps       Pass 'this' from the Jenkinsfile so shell/echo work
     * @param environment Target environment: dev, staging, or prod
     */
    DeploymentManager(Script steps, String environment) {
        this.steps       = steps
        this.environment = environment.toLowerCase().trim()

        /*
         * Environment configuration map.
         * Each environment carries:
         *   namespace       – Kubernetes namespace for this env
         *   replicas        – desired pod count
         *   imageTag        – default Docker image tag if none supplied
         *   approvalRequired – whether a human gate is needed before deploy
         *   dockerRegistry  – registry prefix
         */
        this.envConfig = [
            dev: [
                namespace       : 'ot-dev',
                replicas        : 1,
                imageTag        : 'latest',
                approvalRequired: false,
                dockerRegistry  : 'opstree'
            ],
            staging: [
                namespace       : 'ot-staging',
                replicas        : 2,
                imageTag        : 'rc',
                approvalRequired: false,
                dockerRegistry  : 'opstree'
            ],
            prod: [
                namespace       : 'ot-prod',
                replicas        : 3,
                imageTag        : 'stable',
                approvalRequired: true,
                dockerRegistry  : 'opstree'
            ]
        ]
    }

    // ── validate() ────────────────────────────────────────────────────────────
    /**
     * Validates the environment name and enforces a manual approval gate
     * for production deployments.
     *
     * @return true if validation passes; calls steps.error() on failure
     */
    boolean validate() {
        steps.echo "╔══════════════════════════════════════════════════╗"
        steps.echo "║  [DeploymentManager] VALIDATE                    ║"
        steps.echo "╚══════════════════════════════════════════════════╝"
        steps.echo "  Environment : ${environment}"

        // Guard: only allow known environments
        def validEnvs = envConfig.keySet().toList()
        if (!(environment in validEnvs)) {
            steps.error(
                "[DeploymentManager] Unknown environment '${environment}'. " +
                "Allowed values: ${validEnvs.join(', ')}"
            )
        }

        def config = envConfig[environment]
        steps.echo "  Namespace   : ${config.namespace}"
        steps.echo "  Replicas    : ${config.replicas}"
        steps.echo "  Default tag : ${config.imageTag}"

        // Production gate — pause for a human to click "Proceed"
        if (config.approvalRequired) {
            steps.echo "  ⚠ PRODUCTION deployment — requesting manual approval..."
            steps.input(
                message: "Are you sure you want to deploy to PRODUCTION?",
                ok     : "Yes, deploy to prod"
            )
            steps.echo "  ✔ Approval granted."
        }

        steps.echo "  ✔ Validation passed for '${environment}'"
        return true
    }

    // ── deploy() ──────────────────────────────────────────────────────────────
    /**
     * Deploys the specified OT-Microservices service to the target environment
     * using environment-specific logic.
     *
     * Recreate strategy (instead of RollingUpdate):
     *   1. Scale the existing deployment down to 0 (all old pods killed)
     *   2. Update the image
     *   3. Scale back up to the desired replica count
     *
     * @param serviceName  One of: attendance, employee, salary, notification, frontend
     * @param imageTag     Optional override; uses envConfig default if omitted
     */
    void deploy(String serviceName, String imageTag = null) {
        def config   = envConfig[environment]
        def tag      = imageTag ?: config.imageTag
        def image    = "${config.dockerRegistry}/${serviceName}:${tag}"
        def ns       = config.namespace

        steps.echo "╔══════════════════════════════════════════════════╗"
        steps.echo "║  [DeploymentManager] DEPLOY                      ║"
        steps.echo "╚══════════════════════════════════════════════════╝"
        steps.echo "  Service     : ${serviceName}"
        steps.echo "  Image       : ${image}"
        steps.echo "  Namespace   : ${ns}"
        steps.echo "  Replicas    : ${config.replicas}"
        steps.echo "  Strategy    : Recreate"

        // ── RECREATE DEPLOY STRATEGY ──────────────────────────────────────────
        // Phase 1: Tear down all running pods (scale to 0)
        steps.echo "  → Phase 1: Scaling down existing pods to 0..."
        steps.sh """
            kubectl scale deployment ${serviceName} \
                --replicas=0 \
                -n ${ns} \
                --timeout=120s || true
        """

        // Phase 2: Update the container image
        steps.echo "  → Phase 2: Updating container image to ${image}..."
        steps.sh """
            kubectl set image deployment/${serviceName} \
                ${serviceName}=${image} \
                -n ${ns}
        """

        // Phase 3: Scale back up to desired replicas
        steps.echo "  → Phase 3: Scaling up to ${config.replicas} replica(s)..."
        steps.sh """
            kubectl scale deployment/${serviceName} \
                --replicas=${config.replicas} \
                -n ${ns}
        """

        // Phase 4: Wait for rollout to complete
        steps.echo "  → Phase 4: Waiting for rollout to complete..."
        steps.sh """
            kubectl rollout status deployment/${serviceName} \
                -n ${ns} \
                --timeout=180s
        """

        steps.echo "  ✔ Deploy of '${serviceName}' to '${environment}' completed."
    }

    // ── rollback() ────────────────────────────────────────────────────────────
    /**
     * RECREATE ROLLBACK STRATEGY:
     * Instead of `kubectl rollout undo` (which does a rolling swap),
     * this method:
     *   1. Scales the broken deployment to 0  (kills all bad pods immediately)
     *   2. Re-sets the image back to the previous known-good tag
     *   3. Scales back up fresh (clean recreate from the good image)
     *
     * @param serviceName      Service to roll back
     * @param previousImageTag The known-good image tag to restore
     */
    void rollback(String serviceName, String previousImageTag = null) {
        def config   = envConfig[environment]
        def goodTag  = previousImageTag ?: config.imageTag  // fall back to env default
        def image    = "${config.dockerRegistry}/${serviceName}:${goodTag}"
        def ns       = config.namespace

        steps.echo "╔══════════════════════════════════════════════════╗"
        steps.echo "║  [DeploymentManager] ROLLBACK (Recreate)         ║"
        steps.echo "╚══════════════════════════════════════════════════╝"
        steps.echo "  Service           : ${serviceName}"
        steps.echo "  Rollback image    : ${image}"
        steps.echo "  Namespace         : ${ns}"
        steps.echo "  Strategy          : Recreate (scale-to-0 then restore)"

        // ── RECREATE ROLLBACK STRATEGY ────────────────────────────────────────
        // Step 1: Kill ALL running pods instantly (no gradual drain)
        steps.echo "  → Step 1: Terminating all running pods (scale to 0)..."
        steps.sh """
            kubectl scale deployment/${serviceName} \
                --replicas=0 \
                -n ${ns} \
                --timeout=120s
        """

        // Step 2: Pin the image back to the last known-good version
        steps.echo "  → Step 2: Restoring image to ${image}..."
        steps.sh """
            kubectl set image deployment/${serviceName} \
                ${serviceName}=${image} \
                -n ${ns}
        """

        // Step 3: Bring pods back up fresh from the good image
        steps.echo "  → Step 3: Recreating ${config.replicas} pod(s) from good image..."
        steps.sh """
            kubectl scale deployment/${serviceName} \
                --replicas=${config.replicas} \
                -n ${ns}
        """

        // Step 4: Confirm healthy
        steps.echo "  → Step 4: Verifying rollback rollout..."
        steps.sh """
            kubectl rollout status deployment/${serviceName} \
                -n ${ns} \
                --timeout=180s
        """

        steps.echo "  ✔ Rollback of '${serviceName}' in '${environment}' completed."
    }
}
