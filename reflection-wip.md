# HikariCP Native Image Reflection Configuration - COMPLETE

## Status: ✅ WORKING

HikariCP connection pooling is **fully functional** in the GraalVM native image! 

## Solution Summary

### Root Cause
The NPE in `PoolBase.newConnection()` was caused by missing reflection configuration for:
1. `com.zaxxer.hikari.util.ConcurrentBag$IConcurrentBagEntry[]` - needs `unsafeAllocated: true`
2. Several HikariCP and PostgreSQL classes needed `allDeclared*` instead of `allPublic*`

### Key Changes

#### 1. DatabaseFactory.kt - Use PGSimpleDataSource
Instead of relying on HikariCP's reflection-based `DriverDataSource`, we configure HikariCP to use `PGSimpleDataSource` directly:

```kotlin
val hikariConfig = HikariConfig().apply {
    dataSourceClassName = "org.postgresql.ds.PGSimpleDataSource"
    // Parse jdbc:postgresql://host:port/db?user=x&password=y
    val afterProto = dbUrl.substringAfter("jdbc:postgresql://")
    val (hostPortDb, params) = afterProto.split("?", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    val (hostPort, db) = hostPortDb.split("/", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    val (host, port) = hostPort.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "5432" }.toInt() }
    val p = params.split("&").map { it.split("=", limit = 2).let { kv -> kv[0] to kv.getOrElse(1) { "" } } }.toMap()
    addDataSourceProperty("serverName", host)
    addDataSourceProperty("portNumber", port)
    addDataSourceProperty("databaseName", db.ifEmpty { "postgres" })
    addDataSourceProperty("user", p["user"] ?: "")
    addDataSourceProperty("password", p["password"] ?: "")
    // Disable prepared statement caching for connection pooler compatibility
    addDataSourceProperty("prepareThreshold", "0")
    // ... pool config
}
```

#### 2. Reflection Configuration

**Critical entries (must use `allDeclared*` not `allPublic*`):**

```json
{
  "name": "com.zaxxer.hikari.pool.PoolEntry",
  "allDeclaredConstructors": true,
  "allDeclaredMethods": true,
  "allDeclaredFields": true
}
{
  "name": "com.zaxxer.hikari.util.ConcurrentBag$IConcurrentBagEntry",
  "allDeclaredConstructors": true,
  "allDeclaredMethods": true,
  "allDeclaredFields": true
}
{
  "name": "com.zaxxer.hikari.util.ConcurrentBag$IConcurrentBagEntry[]",
  "allDeclaredConstructors": true,
  "unsafeAllocated": true
}
{
  "name": "org.postgresql.ds.common.BaseDataSource",
  "allDeclaredConstructors": true,
  "allDeclaredMethods": true,
  "allDeclaredFields": true
}
{
  "name": "org.postgresql.ds.PGSimpleDataSource",
  "allDeclaredConstructors": true,
  "allDeclaredMethods": true,
  "allDeclaredFields": true
}
```

**Other required entries:**
```json
{
  "name": "com.zaxxer.hikari.HikariConfig",
  "allDeclaredConstructors": true,
  "allDeclaredMethods": true,
  "allDeclaredFields": true
}
{
  "name": "com.zaxxer.hikari.HikariDataSource",
  "allDeclaredConstructors": true,
  "allDeclaredMethods": true,
  "allDeclaredFields": true
}
{
  "name": "com.zaxxer.hikari.pool.HikariPool",
  "allDeclaredConstructors": true,
  "allDeclaredMethods": true,
  "allDeclaredFields": true
}
{
  "name": "com.zaxxer.hikari.pool.PoolBase",
  "allDeclaredConstructors": true,
  "allDeclaredMethods": true,
  "allDeclaredFields": true
}
{
  "name": "org.postgresql.Driver",
  "allPublicConstructors": true,
  "allPublicMethods": true,
  "allPublicFields": true
}
```

#### 3. Build Configuration

**build.gradle.kts:**
```kotlin
nativeImage {
    buildArgs.add("--initialize-at-build-time=com.zaxxer.hikari")
    buildArgs.add("--initialize-at-build-time=ch.qos.logback")
    buildArgs.add("--initialize-at-build-time=org.slf4j")
}
```

## Verification

Build and run:
```bash
just build-native
just run-native
```

Expected output:
```
INFO com.zaxxer.hikari.HikariDataSource -- AbrechnungPool - Starting...
INFO com.zaxxer.hikari.HikariDataSource -- AbrechnungPool - Start completed.
```

## Files Modified

1. `app/src/main/kotlin/com/bernelius/abrechnung/database/DatabaseFactory.kt`
   - Use `dataSourceClassName` with `PGSimpleDataSource` for PostgreSQL
   - Parse JDBC URL to extract connection properties
   - Add `prepareThreshold=0` for pooler compatibility

2. `app/src/main/resources/META-INF/native-image/reflect-config.json`
   - Added HikariCP reflection entries with `allDeclared*`
   - Added PostgreSQL DataSource reflection entries
   - Added `ConcurrentBag$IConcurrentBagEntry[]` with `unsafeAllocated`

3. `app/build.gradle.kts`
   - Added `--initialize-at-build-time` for HikariCP and Logback

## Notes

- The `prepareThreshold=0` setting is required for Supabase/PgBouncer compatibility
- Using `PGSimpleDataSource` instead of `jdbcUrl` avoids DriverDataSource reflection issues
- `allDeclared*` is required instead of `allPublic*` for proper field/method access
