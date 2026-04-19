package com.bernelius.abrechnung.database

import com.bernelius.abrechnung.utils.getEnv
import com.bernelius.abrechnung.utils.getProjectDir
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.flywaydb.core.Flyway
import javax.sql.DataSource

object DatabaseFactory {
    private var currentTestDb: String? = null

    fun init() {
        val envVar = getEnv("ABRECHNUNG_DB_URL")
        val dbUrl = if (envVar.isNullOrBlank()) {
            "jdbc:sqlite:${getProjectDir()}/abrechnung.db"
        } else {
            envVar
        }

        val isSqlite = dbUrl.startsWith("jdbc:sqlite")

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

        val migrationLocation = if (dbUrl.startsWith("jdbc:sqlite"))
            "classpath:db/migration/sqlite"
        else
            "classpath:db/migration/postgres"

        Database.connect(dataSource)

        val isNativeBuild = System.getProperty("org.graalvm.nativeimage.imagecode") != null
        if (isNativeBuild) {
            EmbeddedMigrations.runMigrations()
        } else {
            val flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(migrationLocation)
                .load()
            flyway.migrate()
        }
    }

    fun initTestDatabase(dbName: String = "test-${System.currentTimeMillis()}.db") {
        currentTestDb = dbName
        Database.connect(url = "jdbc:sqlite:$dbName", driver = "org.sqlite.JDBC")
        transaction { SchemaUtils.create(UserConfigTable, RecipientsTable, InvoicesTable, InvoiceItemsTable) }
    }

    fun cleanupTestDatabase() {
        transaction { SchemaUtils.drop(UserConfigTable, RecipientsTable, InvoicesTable, InvoiceItemsTable) }
        currentTestDb?.let { java.io.File(it).delete() }
        currentTestDb = null
    }
}
