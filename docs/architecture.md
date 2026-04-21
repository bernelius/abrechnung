# Project Architecture

## Overview

**Abrechnung** is a modern, lightweight Kotlin TUI (Terminal User Interface) application designed for generating and managing invoices for freelancers and small businesses. The application combines a retro aesthetic with robust functionality, featuring multi-language support, theming, audio integration, and database persistence.

## Project Structure

```
/home/bernelius/code/abrechnung/
├── app/src/main/kotlin/com/bernelius/abrechnung/    # Main source code
│   ├── App.kt                                      # Application entry point
│   ├── PDF/                                        # PDF generation module
│   ├── audioplayer/                                # Audio playback system
│   ├── cache/                                      # Caching layer
│   ├── config/                                     # Configuration management
│   │   ├── Language.kt                            # Language definitions
│   │   ├── Languages.kt                           # Language loader
│   │   ├── Theme.kt                               # Theme definitions
│   │   ├── Themes.kt                              # Theme loader
│   │   └── UserConfig.kt                          # User configuration
│   ├── database/                                  # Database layer
│   │   ├── DatabaseFactory.kt                     # Database initialization
│   │   ├── Encryption.kt                          # Field encryption
│   │   ├── Mappers.kt                             # Database mapper functions
│   │   └── Schema.kt                              # Database schema definitions
│   ├── dateprovider/                              # Date/time abstraction
│   ├── logging/                                   # Logging configuration
│   ├── mail/                                      # Email integration
│   ├── models/                                    # Data models and DTOs
│   │   ├── DatePattern.kt                         # Date formatting
│   │   ├── DTOs.kt                                # Data transfer objects
│   │   └── Validator.kt                           # Validation logic
│   ├── repository/                                # Data access layer
│   ├── terminal/                                  # TUI interface
│   │   ├── Generalities.kt                        # Common terminal utilities
│   │   ├── HelpScene.kt                           # Help screens
│   │   ├── InvoiceCreator.kt                      # Invoice creation flow
│   │   ├── InvoiceManager.kt                      # Invoice management
│   │   ├── MordantUI.kt                           # Terminal UI wrapper
│   │   ├── NaiveMapper.kt                         # Simple data mapping
│   │   ├── RecipientWorld.kt                      # Customer management
│   │   ├── SettingsManager.kt                     # Settings interface
│   │   └── UserConfigManager.kt                   # User configuration UI
│   ├── theme/                                     # Theme definitions
│   └── utils/                                     # Utility functions
├── app/src/test/                                  # Test code (if present)
├── app/src/main/resources/                        # Resources
│   ├── db/migration/                             # Database migrations
│   ├── fonts/inter/                              # Font files
│   ├── music/                                    # Audio files
│   ├── flf/                                      # Figlet fonts
│   └── META-INF/native-image/                    # GraalVM native-image config
├── docs/                                         # Documentation
├── logos/                                        # Logo designs
├── output/                                       # Generated invoices
└── abrechnung.db                                 # SQLite database
```

## Core Architecture Components

### 1. Entry Point (`App.kt`)

The application starts at `main()` function which:
- Configures logging
- Initializes the terminal UI (Mordant)
- Sets up the database connection
- Starts the audio player
- Loads startup data asynchronously (user config, recipients, invoices)
- Enters the main navigation loop

**Startup Data Loading:**
- Loads user configuration from database
- Loads all recipients sorted by frequency of use
- Loads pending invoices to check for overdue status
- Determines which theme music to play based on invoice status

**Main Menu Navigation:**
Maps key presses to actions using a `Map<Char, suspend () -> Unit>` structure:
- `g` - Generate invoice
- `m` - Manage unpaid invoices
- `r` - Register new recipient
- `u` - Update recipient information
- `c` - User configuration
- `s` - App settings
- `h` - Help
- `q` - Quit

### 2. Layered Architecture

The application follows a layered architecture pattern:

```
┌─────────────────────────────────────┐
│            Terminal UI              │  (Mordant)
├─────────────────────────────────────┤
│           Business Logic            │  (Terminal managers)
├─────────────────────────────────────┤
│         Repository Layer            │  (Data access + caching)
├─────────────────────────────────────┤
│         Database Layer              │  (Exposed + encryption)
├─────────────────────────────────────┤
│         External Services           │  (PDF, Email, Audio)
└─────────────────────────────────────┘
```

### 3. Terminal Layer (`terminal/`)

**MordantUI (`MordantUI.kt`)**: Central terminal wrapper that provides:
- Input/output handling
- Terminal size detection
- Loading states with async operations
- Navigation exception system
- Input validation helpers

**Scene Managers:**
- `InvoiceCreator`: Manages the invoice creation flow
- `InvoiceManager`: Handles viewing, marking as paid, deleting invoices
- `RecipientWorld`: CRUD operations for customer management
- `UserConfigManager`: User profile and email configuration
- `SettingsManager`: Theme and language switching
- `HelpScene`: Help system with introductory guide

**Terminology:**
- **Scene**: A complete terminal screen/view
- **Navigation Loop**: System for returning to previous screens
- **Loading States**: Non-blocking async operations with visual feedback

### 4. Repository Pattern (`repository/`)

The `Repository` object is a singleton that provides all data access operations:

**Key Features:**
- Centralized data access layer
- Async operations using `suspendTransaction`
- Caching integration for performance
- Database-agnostic (supports both SQLite and PostgreSQL)

**Invoice Operations:**
- `saveInvoice()`: Creates new invoices with line items
- `findInvoiceById()`: Retrieves single invoice
- `findAllInvoices()`: Lists invoices with optional filtering
- `markInvoiceAsPaid/Pending/Invalid()`: Status updates

**Recipient Operations:**
- `addRecipient()`: Create new customer
- `updateRecipient()`: Modify existing customer
- `findAllRecipientsSortFrequency()`: Sorted by usage frequency

**Configuration Operations:**
- `getUserConfig()`: Load user profile
- `setUserConfig()`: Save user profile with encryption

**Caching Strategy:**
- `InvoiceCache`: 5-minute TTL for invoices
- `RecipientCache`: 5-minute TTL for recipients
- `UserConfigCache`: 5-minute TTL for user config

### 5. Database Layer (`database/`)

**DatabaseFactory (`DatabaseFactory.kt`)**:
- Supports SQLite (default) and PostgreSQL
- Connection pooling with HikariCP
- Database migrations with Flyway
- Separate migration paths by database type

**Schema (`Schema.kt`)**:
- `UserConfigTable`: User profile, email config (encrypted fields)
- `RecipientsTable`: Customer information
- `InvoicesTable`: Invoice headers and metadata
- `InvoiceItemsTable`: Line items per invoice

**Encryption (`Encryption.kt`)**:
- AES-256-GCM encryption for sensitive fields
- Configurable via environment variables:
  - `ABRECHNUNG_KEY`: (minimum 16 chars)
  - `ABRECHNUNG_SALT`: Salt for key derivation
- Default fallback values for development (insecure)
- Encrypted fields: bank account number, email password

**Mappers (`Mappers.kt`)**:
- Extension functions to map database rows to DTOs
- Handles encrypted field decryption
- Maintains separation between database and domain models

### 6. Models (`models/`)

**Data Transfer Objects:**
- `UserConfigDTO`: User profile information
- `RecipientDTO`: Customer information
- `InvoiceDTO`: Complete invoice with items
- `InvoiceItemDTO`: Individual line items
- `VatDateResolvedDTO`: VAT rate and dates
- `EmailUserDTO`: Email credentials

**Validation (`Validator.kt`)**:
- Field validators for user input
- Pre-built validators for common types
- Custom validation rules for business logic

### 7. Configuration System (`config/`)

**Theme System:**
- TOML-based theme definitions
- 8 built-in themes (`retro-glitch`, `blood-moon`, etc.)
- Custom hex color values
- Supports figlet font selection
- Primary/secondary/tertiary accent colors
- Semantic colors (success, error, warning, info)

**Language System:**
- TOML-based language files
- 2 built-in languages: English (`en`), Norwegian (`no`)
- Customizable field labels for invoices
- Date pattern customization per language
- Invoice PDF text content

**User Configuration:**
- Stored in database (encrypted)
- User profile: name, address, postal, org number
- Email configuration: SMTP host, port, credentials
- Invoice defaults: due date offset, VAT rate, currency

### 8. External Services Integration

**PDF Generation (`PDF/InvoicePDFGenerator.kt`)**:
- OpenPDF library for PDF creation
- Custom fonts (Inter family)
- A4 invoice format
- Professional layout with logo area
- Multi-currency and language support

**Email (`mail/MailSender.kt`)**:
- Jakarta Mail for SMTP
- TLS/SSL support
- Invoice PDF attachments
- Custom email templates

**Audio (`audioplayer/`):**
- LWJGL OpenAL for cross-platform audio
- OGG Vorbis format for music
- Crossfading between tracks
- 4 theme songs mapped to different app states
- Streaming source support

### 9. Caching Layer (`cache/`)

**TTL-Cache Implementation:**
- Time-based expiration (default: 5 minutes)
- Async-aware cache operations
- Invalidation on data modification
- Reduces database queries for frequently accessed data

**Cache Types:**
- `InvoiceCache`: All invoice data
- `RecipientCache`: Customer information
- `UserConfigCache`: User configuration

### 10. Utilities (`utils/`)

- **Exit Program**: Clean shutdown handler
- **Environment Variables**: Safe environment variable access
- **Logo Rendering**: Figlet-based ASCII art generation
- **Project Directory**: Path resolution helpers

## Build System

**Gradle Configuration:**
- Kotlin DSL (`build.gradle.kts`)
- Shadow JAR for fat JAR creation
- Java 24 toolchain requirement (GraalVM vendor)
- ktlint for code formatting
- Kover for code coverage
- GraalVM Native Image support via `org.graalvm.buildtools.native` plugin

**Plugins:**
- `org.jetbrains.kotlin.jvm` 2.2.21
- `org.jetbrains.kotlin.plugin.serialization` 2.3.20-RC2
- `com.gradleup.shadow` 9.3.0
- `org.jlleitschuh.gradle.ktlint` 14.0.1
- `org.graalvm.buildtools.native` 1.0.0
- `org.jetbrains.kotlinx.kover` 0.9.8

**Key Dependencies:**
- Kotlin 2.2.21 with coroutines 1.8.0
- Mordant 3.0.2 (Terminal UI)
- Exposed 1.0.0 (Type-safe SQL)
- OpenPDF 3.0.3 (PDF generation)
- Jakarta Mail 2.1.0-M1 (Email)
- HikariCP 7.0.2 (Connection pooling)
- LWJGL 3.4.1 (Audio)
- Flyway 12.3.0 (Database migrations)

**Resource Management:**
- Fonts: Complete Inter font family (18pt, 24pt, 28pt)
- Music: 4 OGG Vorbis theme songs
- Figlet: 15+ ASCII art fonts for logo rendering
- Database migrations: Per-database-type SQL
- GraalVM: Native image configuration in `META-INF/native-image/native-image.properties`

**GraalVM Native Image Configuration:**
Located at `app/src/main/resources/META-INF/native-image/native-image.properties`:
```properties
# GraalVM Native Image configuration
Args = --no-fallback --enable-url-protocols=https,http --enable-all-security-services --enable-native-access=ALL-UNNAMED
```

Additional build arguments configured in `build.gradle.kts`:
- `-J--sun-misc-unsafe-memory-access=allow` (suppresses LWJGL deprecation warnings)
- Metadata repository disabled (versions >= 0.3.33 use incompatible format)

## Configuration File Structure

**User Configuration (TOML):**
```toml
[terminalConfig]
theme = "retro-glitch"
outerHotkeys = true

[invoiceConfig]
dueDateOffset = 14
vatRate = 0
currency = "NOK"
language = "en"
```

**Theme Files (`~/.config/abrechnung/themes/`):**
```toml
name = "retro-glitch"
primary = "#ff00ff"
secondary = "#00ffff"
tertiary = "#ffff00"
success = "#00ff00"
error = "#ff0000"
warning = "#ff8800"
info = "#0000ff"
primaryFont = "Big Money-ne"
```

**Language Files (`~/.config/abrechnung/languages/`):**
```toml
name = "en"
headline = "INVOICE"
invoiceNumber = "Invoice Number"
# ... other field labels
```

## Security Architecture

**Encryption at Rest:**
- AES-256-GCM for sensitive fields
- Password-derived key using PBKDF2
- Environment variable-based key management
- Graceful degradation with warning for missing keys

**Data Locations:**
- Configuration: `$XDG_CONFIG_HOME/abrechnung/` (or `~/.config/abrechnung/`)
- Database: `$PROJECT_ROOT/abrechnung.db` (configurable via `ABRECHNUNG_DB_URL`)
- Output: `$PROJECT_ROOT/output/` (invoices directory)

**Migration Strategy:**
- Flyway for schema versioning
- Database-specific migration paths
- First-run automatic initialization
- Backward-compatible migrations preferred

## Testing Architecture

**Test Structure:**
- Uses JUnit 5
- Test database lifecycle management
- In-memory SQLite for testing
- Automatic cleanup after tests

**Test Database:**
- `DatabaseFactory.initTestDatabase()`: Creates temporary DB
- `DatabaseFactory.cleanupTestDatabase()`: Removes test DB
- Isolated test environments

## Deployment Options

**JAR Distribution:**
- Shadow JAR (`./gradlew shadowJar` or `just build`)
- Self-contained with resources
- Java 24+ with GraalVM required
- Cross-platform (Linux, macOS, Windows)
- Run with: `java --enable-native-access=ALL-UNNAMED -jar app/build/libs/abrechnung-all.jar`

**Native Compilation:**
- Full GraalVM native-image support via Gradle plugin
- Build: `./gradlew nativeCompile` or `just build-native`
- Output: `app/build/native/nativeCompile/app`
- Run: `just run-native` or `./app/build/native/nativeCompile/app`
- Configuration in `resources/META-INF/native-image/native-image.properties`
- Build arguments:
  - `--enable-url-protocols=https,http`
  - `--enable-native-access=ALL-UNNAMED`
  - `--no-fallback`
  - `--enable-all-security-services`
- GraalVM Community edition support via `setupGraalVMCommunity` task (creates symlink for native-image)

**Windows Native Compilation:**
- Windows native executable: `app.exe`
- Windows Terminal launcher: `abrechnung.bat` (automatically generated during `nativeCompile`)
- The launcher script uses Windows Terminal (`wt.exe`) with explicit Command Prompt profile
- Sets UTF-8 code page (`chcp 65001`) for proper Unicode rendering of box-drawing characters
- Launch command: `abrechnung.bat` (requires Windows Terminal installed)
- All command-line arguments are passed through to the application

**Database Options:**
- Default: SQLite (embedded, zero-config)
- Production: PostgreSQL (via `ABRECHNUNG_DB_URL`)
- Migration path: Export/import utilities

**Build Commands (via justfile):**
- `just build` - Build shadow JAR
- `just build-native` - Build GraalVM native image
- `just run` - Run JAR version
- `just run-native` - Run native binary
- `just test` - Run tests
- `just check` - Run ktlint checks
- `just format` - Format code with ktlint
- `just report` - Generate Kover coverage report

## Async/Await Architecture

**Coroutine Scope Management:**
- Main scope: `Dispatchers.IO + SupervisorJob()`
- Loading states: Non-blocking UI updates
- Repository layer: All DB operations are suspending
- Exception handling: Structured concurrency

**Concurrency Patterns:**
- `async/await` for parallel data loading
- `coroutineScope` for structured concurrency
- `suspendTransaction` for async database operations
- Exception propagation and recovery

## Extension Points

**Adding New Themes:**
1. Create TOML file in `~/.config/abrechnung/themes/`
2. Define color palette and font
3. Select in app settings UI

**Adding New Languages:**
1. Duplicate and rename existing language TOML
2. Translate all field labels
3. Set in config or app settings

**Custom Invoice Fields:**
1. Add to `InvoiceDTO` in `models/DTOs.kt`
2. Update database schema with migration
3. Modify `PDF/InvoicePDFGenerator.kt`
4. Add UI prompts in `terminal/InvoiceCreator.kt`

**Audio Integration:**
1. Add OGG file to `resources/music/`
2. Update `App.kt` `Songs` object
3. Call `audioPlayer.play()` in appropriate scene

## Architecture Principles

1. **Separation of Concerns**: Clear boundaries between UI, business logic, and data
2. **Type Safety**: Kotlin's type system prevents common errors
3. **Async-First**: Non-blocking operations throughout
4. **Abstraction**: Interface-based design for testability
5. **Configuration Over Code**: Themes and languages in external files
6. **Embedded/Portable**: SQLite default enables zero-config deployment
7. **Security by Default**: Encryption for sensitive fields, secure key management
8. **Retro Aesthetic**: Terminal-first design with modern capabilities
