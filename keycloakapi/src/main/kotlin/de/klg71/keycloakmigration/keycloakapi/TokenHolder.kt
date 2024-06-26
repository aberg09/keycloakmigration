package de.klg71.keycloakmigration.keycloakapi

import de.klg71.keycloakmigration.keycloakapi.model.AccessToken
import org.slf4j.LoggerFactory
import java.lang.System.currentTimeMillis
import java.lang.System.nanoTime
import java.util.concurrent.TimeUnit

/**
 * Manages the keycloak access tokens and refreshes if needed
 */
internal class TokenHolder(private val client: KeycloakLoginClient,
    private val adminUser: String, private val adminPassword: String,
    private val realm: String, private val clientId: String,
    private val totp: String, private val tokenRefreshOffsetMs: Long = 1000) {

    companion object {
        val LOG = LoggerFactory.getLogger(TokenHolder::class.java)!!
    }

    private var token: AccessToken = client.login(realm, "password", clientId, adminUser, adminPassword, totp)
    private var tokenReceived: Long = currentTimeMillis()
    private var tokenReceivedNs: Long = nanoTime()

    private fun tokenExpired() =
        currentTimeMillis() - tokenReceived > TimeUnit.SECONDS.toMillis(token.expiresIn) - tokenRefreshOffsetMs

    private fun refreshExpired() =
        currentTimeMillis() - tokenReceived > TimeUnit.SECONDS.toMillis(token.refreshExpiresIn) - tokenRefreshOffsetMs

    fun token(): AccessToken {
        if (tokenExpired()) {
            LOG.info("Token expired retrieving new one.")
            token = getNewToken()
            tokenReceived = currentTimeMillis()
            tokenReceivedNs = nanoTime()
        }
        return token
    }

    private fun getNewToken() = if (!refreshExpired()) {
        LOG.info("using refreshToken")
        client.login(realm, "refresh_token", token.refreshToken, clientId)
    } else {
        LOG.info("using password")
        client.login(realm, "password", clientId, adminUser, adminPassword, totp)
    }
}
