package com.bernelius.abrechnung.database

import com.bernelius.abrechnung.utils.getEnv
import com.bernelius.abrechnung.utils.getProjectDir
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway

object DatabaseFactory {
    private var currentTestDb: String? = null

    fun init() {
        val envVar = getEnv("ABRECHNUNG_DB_URL")
        val dbUrl = if (envVar.isNullOrBlank()) {
            "jdbc:sqlite:${getProjectDir()}/abrechnung.db"
        } else {
            envVar
        }

        val driver = if (dbUrl.startsWith("jdbc:sqlite")) "org.sqlite.JDBC" else "org.postgresql.Driver"
        val poolSize = if (driver == "org.sqlite.JDBC") 1 else 10
        val finalDbUrl = if (driver == "org.sqlite.JDBC") dbUrl else "$dbUrl&prepareThreshold=0"

        val config = HikariConfig().apply {
            jdbcUrl = finalDbUrl
            driverClassName = driver
            maximumPoolSize = poolSize
            connectionTestQuery = "SELECT 1"
            isAutoCommit = false
            validate()
        }
        val dataSource = HikariDataSource(config)

        val migrationLocation = if (driver == "org.sqlite.JDBC")
            "classpath:db/migration/sqlite"
        else
            "classpath:db/migration/postgres"

        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations(migrationLocation)
            .load()
        flyway.migrate()

        Database.connect(dataSource)
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
