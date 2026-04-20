package com.bernelius.abrechnung.database

import com.bernelius.abrechnung.utils.getEnv
import com.bernelius.abrechnung.utils.getProjectDir
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import javax.sql.DataSource

private val logger = LoggerFactory.getLogger("DatabaseFactory")

object DatabaseFactory {
    private var currentTestDb: String? = null

    private fun isNativeImage(): Boolean {
        return System.getProperty("org.graalvm.nativeimage.kind") != null
    }

    /**
     * Extracts migration SQL files from the native image's resource bundle to a temporary directory.
     *
     * Flyway's classpath scanning does not work in GraalVM native images because it relies on
     * Java classpath APIs that don't properly handle the resource:// protocol. This function
     * works around that by:
     * 1. Using GraalVM's native resource:// filesystem to enumerate all .sql files
     * 2. Extracting them to a temp directory
     * 3. Passing that directory to Flyway via filesystem: location
     *
     * See: https://github.com/flyway/flyway/issues/2927
     */
    private fun extractMigrations(dbType: String): Path {
        logger.info("Extracting $dbType migrations from native image...")

        val dir = Files.createTempDirectory("migrations-$dbType")
        logger.debug("Created temp directory: $dir")

        // Must explicitly create the resource filesystem in native images
        // See: https://github.com/oracle/graalvm/issues/7682
        val fs = FileSystems.newFileSystem(URI.create("resource:/"), emptyMap<String, Any>())
        val base = fs.getPath("db/migration/$dbType")

        val migrationFiles = Files.walk(base)
            .filter { it.toString().endsWith(".sql") }
            .toList()

        if (migrationFiles.isEmpty()) {
            throw IllegalStateException(
                "No migration files found for database type '$dbType' at path 'db/migration/$dbType/*.sql'. " +
                "Ensure migration files exist and are included in the native image."
            )
        }

        logger.info("Found ${migrationFiles.size} migration file(s)")

        migrationFiles.forEach { resourcePath ->
            val fileName = resourcePath.fileName.toString()
            val target = dir.resolve(fileName)
            resourcePath.toUri().toURL().openStream().use { input ->
                Files.copy(input, target)
            }
            logger.debug("Extracted migration: $fileName")
        }

        logger.info("Successfully extracted migrations to: $dir")
        return dir
    }

    private fun cleanupMigrations(dir: Path) {
        logger.debug("Cleaning up migration directory: $dir")
        try {
            Files.walk(dir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
            logger.debug("Migration directory cleaned up successfully")
        } catch (e: Exception) {
            logger.warn("Failed to clean up migration directory: $dir", e)
        }
    }

    fun init() {
        val envVar = getEnv("ABRECHNUNG_DB_URL")
        val dbUrl = if (envVar.isNullOrBlank()) {
            "jdbc:sqlite:${getProjectDir()}/abrechnung.db"
        } else {
            envVar
        }

        val isSqlite = dbUrl.startsWith("jdbc:sqlite")
        val dbType = if (isSqlite) "sqlite" else "postgres"

        logger.info("Initializing database: $dbUrl (type: $dbType)")

        // Configure HikariCP connection pool
        val hikariConfig = HikariConfig().apply {
            if (isSqlite) {
                jdbcUrl = dbUrl
            } else {
                // Use PGSimpleDataSource directly to avoid DriverDataSource reflection issues in native images
                dataSourceClassName = "org.postgresql.ds.PGSimpleDataSource"
                // Parse jdbc:postgresql://host:port/db?user=x&password=y
                val afterProto = dbUrl.substringAfter("jdbc:postgresql://")
                val (hostPortDb, params) = afterProto.split("?", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                val (hostPort, db) = hostPortDb.split("/", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                val (host, port) = hostPort.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "5432" }.toInt() }
                val p = mutableMapOf<String, String>()

                for (param in params.split("&")) {
                    val parts = param.split("=", limit = 2)
                    val key = parts[0]
                    val value = if (parts.size > 1) parts[1] else ""
                    p[key] = value
                }
                addDataSourceProperty("serverName", host)
                addDataSourceProperty("portNumber", port)
                addDataSourceProperty("databaseName", db.ifEmpty { "postgres" })
                addDataSourceProperty("user", p["user"] ?: "")
                addDataSourceProperty("password", p["password"] ?: "")
                // Disable prepared statement caching for connection pooler compatibility (Supabase/PgBouncer)
                addDataSourceProperty("prepareThreshold", "0")
            }
            maximumPoolSize = if (isSqlite) 1 else 10
            minimumIdle = if (isSqlite) 1 else 2
            connectionTimeout = 30000
            idleTimeout = 600000
            maxLifetime = 1800000
            poolName = "AbrechnungPool"
        }

        val dataSource: DataSource = HikariDataSource(hikariConfig)

        val isNative = isNativeImage()
        logger.debug("Running on native image: $isNative")

        val migrationLocations: List<String>
        val migrationDir: Path? = if (isNative) {
            // In native image, extract migrations to temp files for Flyway filesystem scanning
            // See: https://github.com/flyway/flyway/issues/2927
            val dir = extractMigrations(dbType)
            migrationLocations = listOf("filesystem:${dir.toAbsolutePath()}")
            dir
        } else {
            // On JVM, use standard classpath scanning
            migrationLocations = listOf("classpath:db/migration/$dbType")
            null
        }

        Database.connect(dataSource)

        logger.info("Running Flyway migrations...")
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations(*migrationLocations.toTypedArray())
            .load()
        flyway.migrate()
        logger.info("Flyway migrations completed successfully")

        // Cleanup IMMEDIATELY after migrations complete
        migrationDir?.let {
            cleanupMigrations(it)
        }
    }

    fun initTestDatabase(dbName: String = "test-${System.currentTimeMillis()}.db") {
        currentTestDb = dbName
        Database.connect(url = "jdbc:sqlite:$dbName", driver = "org.sqlite.JDBC")
        transaction { SchemaUtils.create(UserConfigTable, RecipientsTable, InvoicesTable, InvoiceItemsTable) }
    }

    fun cleanupTestDatabase() {
        transaction { SchemaUtils.drop(UserConfigTable, RecipientsTable, InvoicesTable, InvoiceItemsTable) }
        currentTestDb?.let { File(it).delete() }
        currentTestDb = null
    }
}
