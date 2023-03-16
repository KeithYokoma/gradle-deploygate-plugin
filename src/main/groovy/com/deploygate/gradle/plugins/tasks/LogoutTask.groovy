package com.deploygate.gradle.plugins.tasks

import com.deploygate.gradle.plugins.internal.credentials.CliCredentialStore
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class LogoutTask extends DefaultTask {

    @Internal
    CliCredentialStore credentialStore

    @Inject
    LogoutTask() {
        description = "Remove the local persisted credentials."
        group = Constants.TASK_GROUP_NAME
    }

    @TaskAction
    def remove() {
        credentialStore.delete()
        logger.info("The local credentials have been removed.")
    }
}
