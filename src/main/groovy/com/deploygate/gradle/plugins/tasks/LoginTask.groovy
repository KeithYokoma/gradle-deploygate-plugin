package com.deploygate.gradle.plugins.tasks

import com.deploygate.gradle.plugins.dsl.DeployGateExtension
import com.deploygate.gradle.plugins.internal.http.HttpClient
import com.deploygate.gradle.plugins.internal.http.GetCredentialsResponse
import com.deploygate.gradle.plugins.tasks.inputs.Credentials
import com.deploygate.gradle.plugins.utils.BrowserUtils
import com.deploygate.gradle.plugins.utils.UrlUtils
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.VisibleForTesting

import javax.inject.Inject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

abstract class LoginTask extends DefaultTask {

    @Input
    @Optional
    final Property<String> appOwnerName

    @Input
    @Optional
    final Property<String> apiToken

    @Internal
    DeployGateExtension deployGateExtension // TODO remove this reference

    @Internal
    final Property<HttpClient> httpClient

    @Internal
    String onetimeKey

    @Internal
    CountDownLatch countDownLatch

    @Internal
    final Credentials credentials

    @Inject
    LoginTask(@NotNull ObjectFactory objectFactory) {
        credentials = objectFactory.newInstance(Credentials)
        appOwnerName = objectFactory.property(String)
        apiToken = objectFactory.property(String)
        httpClient = objectFactory.property(HttpClient)

        description = "Check the configured credentials and launch the authentication flow if they are not enough."
        group = Constants.TASK_GROUP_NAME
    }

    @TaskAction
    def setup() {
        if (!(appOwnerName.isPresent() && apiToken.isPresent())) {
            if (!setupCredential()) {
                throw new RuntimeException('We could not retrieve DeployGate credentials. Please make sure you have configured app owner name and api token or any browser application is available to launch the authentication flow.')
            }

            def store = deployGateExtension.credentialStore

            println "Welcome ${store.name}!"

            logger.info("The authentication has succeeded. The application owner name is ${store.name}.")

            // We can set the values unless they are found because of the idempotency.
            credentials.appOwnerName.set(appOwnerName.getOrElse(store.name))
            credentials.apiToken.set(apiToken.getOrElse(store.token))
        } else {
            credentials.appOwnerName.set(appOwnerName)
            credentials.apiToken.set(apiToken)
        }

        credentials.normalizeAndValidate()
    }

    /**
     * Launch the authentication flow and fetch the credentials if possible
     * @return true if the credentials are persisted, otherwise false.
     */
    @VisibleForTesting
    boolean setupCredential() {
        if (BrowserUtils.hasBrowser()) {
            setupBrowser()
        } else {
            setupTerminal()
        }

        if (!onetimeKey) {
            return false
        }

        this.httpClient.get().notificationKey.set(onetimeKey)

        if (!fetchAndSaveCredentials()) {
            return false
        }

        this.httpClient.get().lifecycleNotificationClient.notifyOnCredentialSaved()

        return true
    }

    private void setupTerminal() {
        logger.error("The authentication flow within the terminal is not supported yet.")
        // @TODO implement the terminal authentication flow
        false
    }

    /**
     * Launch the authentication flow by using a browser application.
     */
    private void setupBrowser() {
        def server
        try {
            server = startLocalServer()
            openBrowser(server.address.port)
            waitForResponse()
        } catch (e) {
            logger.error("Failed to log in with browser: ${e.message}", e)
        } finally {
            if (server) {
                server.stop(1)
            }
        }
    }

    void openBrowser(int port) {
        def url = "${deployGateExtension.endpoint}/cli/login?port=${port}&client=gradle"

        if (!BrowserUtils.openBrowser(url)) {
            logger.error('Could not open a browser on current environment.')
            println 'Please log in to DeployGate by opening the following URL on your browser:'
            println url
        }
    }

    def startLocalServer() {
        countDownLatch = new CountDownLatch(1)

        def address = new InetSocketAddress("localhost", 0)
        def httpServer = HttpServer.create(address, 0)

        httpServer.createContext("/token", { HttpExchange httpExchange ->
            httpExchange.sendResponseHeaders(204, -1)
            httpExchange.close()

            logger.info("Got a response: ${httpExchange.requestURI.query}")

            try {
                def query = UrlUtils.parseQueryString(httpExchange.requestURI.query)

                if (!query.containsKey('cancel')) {
                    onetimeKey = query.key
                }
            } finally {
                countDownLatch.countDown()
            }
        })
        httpServer.start()
        httpServer
    }


    def waitForResponse() {
        def timeout = TimeUnit.MINUTES.toMillis(3)
        def start = System.currentTimeMillis()

        while (!countDownLatch.await(5, TimeUnit.SECONDS)) {
            logger.info(".")

            if (System.currentTimeMillis() - start > timeout) {
                throw new TimeoutException('Timeout while waiting for browser response')
            }
        }
    }

    boolean fetchAndSaveCredentials() {
        HttpClient.Response<GetCredentialsResponse> response

        try {
            response = httpClient.get().lifecycleNotificationClient.getCredentials()
        } catch (Throwable th) {
            logger.error('failed to retrieve credential', th)
            return false
        }

        def store = deployGateExtension.credentialStore

        if (!store.saveLocalCredentialFile(response.rawResponse)) {
            throw new GradleException("failed to save the fetched credentials")
        }

        return store.load()
    }
}
