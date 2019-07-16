package de.klg71.keycloakmigration.changeControl

import de.klg71.keycloakmigration.changeControl.actions.MigrationException
import de.klg71.keycloakmigration.model.Attributes
import de.klg71.keycloakmigration.model.User
import de.klg71.keycloakmigration.rest.KeycloakClient
import org.apache.commons.codec.digest.DigestUtils.sha256Hex
import org.koin.standalone.KoinComponent
import org.koin.standalone.inject
import org.slf4j.LoggerFactory
import java.util.*
import java.util.Objects.isNull

/**
 * Service keeping track of the already executed migrations and which new migrations should be executed
 */
internal class MigrationChangelog(private val migrationUserId: UUID, private val realm: String) : KoinComponent {

    private val client by inject<KeycloakClient>()

    companion object {
        val LOG = LoggerFactory.getLogger(MigrationChangelog::class.java)!!
    }

    /**
     * Calculate which Changeset should be executed and which are already done depending on information in keycloak
     */
    internal fun changesTodo(changes: List<ChangeSet>): List<ChangeSet> {
        val changeHashes = getMigrationsHashes()

        return changes.apply {
            changeHashes.forEachIndexed { i, it ->
                if (get(i).hash() != it) {
                    throw MigrationException("Invalid hash expected: $it (remote) got ${get(i).hash()} (local) in migration: ${get(i).id}")
                }
                LOG.info("Skipping migration: ${get(i).id}")
            }
        }.run {
            subList(changeHashes.size, size)
        }
    }

    /**
     * Write the information about an executed changeSet to keycloak
     */
    internal fun writeChangesToUser(changes: ChangeSet) {
        client.user(migrationUserId, realm).run {
            userAttributes().run {
                addMigration(changes)
            }.let {
                client.updateUser(id, User(id, createdTimestamp, username, enabled, emailVerified, it,
                        notBefore, totp, access, disableableCredentialTypes, requiredActions, email, firstName, lastName, null), realm)
            }
        }
    }

    private fun Attributes.addMigration(change: ChangeSet): Attributes = toMutableMap().apply {
        put("migrations", migrations().addChangeHash(change))
    }

    private fun List<String>.addChangeHash(change: ChangeSet) =
            toMutableList().apply {
                add(change.hash())
            }

    private fun User.userAttributes() = attributes ?: emptyMap()

    private fun Attributes.migrations() = get("migrations") ?: emptyList()

    private fun getMigrationsHashes(): List<String> =
            client.user(migrationUserId, realm).run {
                if (isNull(attributes)) {
                    return emptyList()
                }
                if ("migrations" !in attributes!!) {
                    return emptyList()
                }
                return attributes["migrations"]!!
            }


    private fun ChangeSet.hash() = StringBuilder().let { builder ->
        builder.append(author)
        builder.append(id)
        changes.forEach {
            it.path = path
            builder.append(it.hash())
        }
        builder.toString()
    }.let {
        sha256Hex(it)
    }!!
}