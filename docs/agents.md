# Agent Guidelines for Architecture Documentation

## Purpose

This file provides instructions for AI agents (and human developers) regarding the maintenance of the `docs/architecture.md` file. The architecture documentation must accurately reflect the current state of the codebase. As changes are made, the documentation must be reviewed and updated accordingly.

## Ongoing Maintenance Rule

**After every code change, the `docs/architecture.md` file MUST be checked for accuracy and completeness.**

## When to Update Architecture Documentation

### Mandatory Updates Required

Update `docs/architecture.md` when:

1. **New packages or modules are created** - Document the purpose and relationship to existing components
2. **Database schema changes** - Update schema definitions, table descriptions, and migration patterns
3. **New external dependencies are added** - Document how they integrate with the architecture
4. **Configuration system changes** - Update configuration file formats and validation rules
5. **Security model changes** - Document new encryption methods, key management, or authentication
6. **Async/coroutine patterns change** - Update concurrency patterns and scope management
7. **Build system modifications** - Reflect changes to Gradle configuration or deployment options
8. **New terminal scenes are added** - Document the navigation flow and component interactions
9. **Caching strategy changes** - Update cache implementations and invalidation patterns
10. **New extension points are created** - Document how users can extend the system

### Review Recommended

Review architecture documentation when:

1. Refactoring existing code without changing behavior
2. Updating dependencies to newer versions
3. Modifying existing UI components
4. Changing method signatures in public APIs (Repository, ConfigManager, etc.)
5. Adjusting logging or error handling patterns
6. Modifying resource loading behavior

## How to Verify Architecture Documentation

### Pre-Change Verification (Before Modifying Code)

1. **Read the relevant section** in `docs/architecture.md`
2. **Cross-reference with actual implementation** by reading source files
3. **Note discrepancies** between documentation and code
4. **Update documentation first** if refactoring or changing behavior
5. **Make code changes** to match the updated documentation

### Post-Change Verification (After Modifying Code)

1. **Check all modified files** against architecture documentation
2. **Update any affected sections** with new behavior or structure
3. **Verify example code blocks** in documentation still work
4. **Check for stale references** to old package names or class names
5. **Update diagrams/plantuml** if included in the architecture docs

## Architecture Documentation Sections

### Section 1: Overview
**Check for accuracy when:**
- Project purpose changes
- Target audience shifts
- Core features are added or removed
- Tech stack components change

**What to verify:**
- Feature list matches actual capabilities
- Tech stack versions are current
- Project description accurately reflects reality

### Section 2: Project Structure
**Check for accuracy when:**
- Creating new packages or directories
- Renaming existing packages
- Adding new resource types
- Changing build output locations

**What to verify:**
- Directory tree matches actual structure
- All packages are documented
- File paths are correct and up-to-date
- New architectures (like adding tests) are reflected

**Commands to check structure:**
```bash
# Compare documented structure with actual
find app/src/main/kotlin/com/bernelius/abrechnung -type d | sort
ls -la /home/bernelius/code/abrechnung/docs
find app/src/main/resources -type f | wc -l  # Verify resource count
```

### Section 3: Core Architecture Components
**Check for accuracy when:**
- Modifying `App.kt` main entry point
- Changing menu structure or key mappings
- Updating startup data loading
- Adding new navigation patterns

**What to verify:**
- Menu options and key bindings are correct
- Startup data loading sequence matches code
- Navigation flow documentation is accurate
- New scenes are integrated into the navigation system

### Section 4: Layered Architecture
**Check for accuracy when:**
- Adding new architectural layers
- Changing layer responsibilities
- Modifying inter-layer communication
- Adding new design patterns

**What to verify:**
- Layer diagram reflects actual dependencies
- Data flow direction is accurate
- No circular dependencies exist
- Each layer's responsibility is correctly described

### Section 5: Terminal Layer
**Check for accuracy when:**
- Adding new terminal scenes
- Modifying MordantUI wrapper
- Changing navigation exception handling
- Updating input validation

**What to verify:**
- Scene manager descriptions match implementation
- Terminology is consistent (scene, navigation, etc.)
- UI components are properly categorized
- Key features of MordantUI are documented

### Section 6: Repository Pattern
**Check for accuracy when:**
- Adding new repository methods
- Modifying caching strategy
- Changing transaction behavior
- Adding new database operations

**What to verify:**
- All public repository methods are documented
- Caching behavior matches implementation
- Transaction patterns are accurate
- New data types are included

**Verify repository interface:**
```bash
# Count public methods in Repository object
grep -E "suspend fun|fun" /home/bernelius/code/abrechnung/app/src/main/kotlin/com/bernelius/abrechnung/repository/Repository.kt
```

### Section 7: Database Layer
**Check for accuracy when:**
- Adding/modifying database tables
- Changing encryption approach
- Updating migration logic
- Modifying connection pooling

**What to verify:**
- All tables are documented in schema section
- Field-level encryption status is accurate
- Migration paths are correct for each DB type
- Environment variable names match code

**Verify database schema:**
```bash
# Check tables defined in Schema.kt
grep "object.*Table" /home/bernelius/code/abrechnung/app/src/main/kotlin/com/bernelius/abrechnung/database/Schema.kt
```

### Section 8: Models
**Check for accuracy when:**
- Adding new DTO classes
- Modifying existing DTO fields
- Changing validation rules
- Adding new enums or sealed classes

**What to verify:**
- All DTO types are listed
- Field descriptions are accurate
- Validation logic is documented
- Relationships between models are clear

### Section 9: Configuration System
**Check for accuracy when:**
- Adding new configuration options
- Changing config file format
- Adding new themes or languages
- Modifying config loading logic

**What to verify:**
- Config file structure matches actual TOML format
- All configuration options are documented
- Theme and language examples are accurate
- Default values match implementation

**Verify configuration loading:**
```bash
# Check config defaults and loading
rg "loadConfig|ConfigManager" /home/bernelius/code/abrechnung/app/src/main/kotlin/com/bernelius/abrechnung/
```

### Section 10: External Services Integration
**Check for accuracy when:**
- Adding new PDF features
- Changing email functionality
- Modifying audio playback
- Updating library versions

**What to verify:**
- All libraries are listed with correct versions
- Feature descriptions match capabilities
- Configuration requirements are accurate
- Error handling is documented

### Section 11: Caching Layer
**Check for accuracy when:**
- Adding new cache implementations
- Changing cache TTL or behavior
- Modifying cache invalidation
- Adding new cached entities

**What to verify:**
- Cache types match implementation
- TTL values are accurate
- Invalidation triggers are documented
- Cache hierarchy is clear

### Section 12: Utilities
**Check for accuracy when:**
- Adding new utility functions
- Modifying path resolution
- Changing environment handling
- Adding new helper methods

**What to verify:**
- Utility categories are comprehensive
- Function purposes are clearly described
- Example usage is provided where helpful

### Section 13: Build System
**Check for accuracy when:**
- Updating dependencies
- Changing Gradle version
- Modifying build plugins
- Adding new build tasks

**What to verify:**
- All major dependencies are listed
- Version numbers are current
- Build commands are accurate
- Plugin purposes are explained

**Verify build configuration:**
```bash
# Check for dependency updates
cat /home/bernelius/code/abrechnung/app/build.gradle.kts | grep -E "implementation|version"
```

### Section 14: Configuration File Structure
**Check for accuracy when:**
- Adding new config sections
- Changing config file paths
- Modifying config validation
- Updating default values

**What to verify:**
- Example configurations are valid TOML
- All config options have examples
- Path substitutions are accurate
- File structure is clearly documented

### Section 15: Security Architecture
**Check for accuracy when:**
- Changing encryption approach
- Modifying key derivation
- Adding new sensitive fields
- Updating security documentation

**What to verify:**
- Encryption algorithm details are accurate
- Environment variable names match code
- Security warnings are prominent
- Migration paths are documented

### Section 16: Testing Architecture
**Check for accuracy when:**
- Adding new test patterns
- Changing test utilities
- Modifying test database setup
- Updating test documentation

**What to verify:**
- Test structure matches actual test layout
- Database lifecycle documentation is accurate
- Test utilities are properly documented
- Coverage goals are stated if applicable

### Section 17: Deployment Options
**Check for accuracy when:**
- Adding deployment methods
- Changing build outputs
- Updating documentation
- Modifying distribution

**What to verify:**
- All deployment options are documented
- Prerequisites are clearly listed
- Commands are accurate and tested
- Environment-specific details are noted

### Section 18: Async/Await Architecture
**Check for accuracy when:**
- Modifying coroutine scopes
- Adding new async operations
- Changing exception handling
- Updating concurrency patterns

**What to verify:**
- Scope management matches implementation
- Cited patterns (structured concurrency, etc.) are accurate
- Error handling approaches are documented
- Performance considerations are noted

### Section 19: Extension Points
**Check for accuracy when:**
- Creating new extension mechanisms
- Modifying plugin interfaces
- Adding customization options
- Updating integration APIs

**What to verify:**
- Extension guides are complete
- No undocumented extension points exist
- Breaking changes are highlighted
- Migration paths are provided

### Section 20: Architecture Principles
**Check for accuracy when:**
- Core architectural philosophy changes
- New principles are adopted
- Deprecated approaches are removed
- Project direction shifts

**What to verify:**
- Principles are consistently applied in code
- Examples support each principle
- No contradictions between principles and implementation
- Principles reflect actual practice, not ideals

## Documentation Integrity Checklist

Before committing code changes, verify:

- [ ] All new packages are documented in project structure
- [ ] New classes in existing packages are reflected in relevant sections
- [ ] Database schema changes are documented with table descriptions
- [ ] New dependencies are added to tech stack and build system sections
- [ ] Configuration changes are documented with examples
- [ ] New terminal scenes are integrated into navigation flow documentation
- [ ] New repository methods are documented in data access patterns
- [ ] API changes (if any) are documented
- [ ] Error handling patterns are updated if changed
- [ ] Performance considerations are noted for significant changes
- [ ] Breaking changes are clearly marked
- [ ] Version compatibility is noted if dependencies changed

## Documentation Style Guidelines

When updating `docs/architecture.md`, follow these conventions:

1. **Use code section references**: Link to specific Kotlin files and line numbers where helpful
2. **Include examples**: Provide code snippets showing patterns or usage
3. **Be precise**: Use exact class names, method names, and package paths
4. **Keep it current**: Remove references to deprecated or removed features
5. **Cross-reference**: When modifying one section, check related sections for consistency
6. **Include commands**: Add shell commands for verification where applicable

## Automated Verification

Periodically run these commands to identify discrepancies:

```bash
# Check for undocumented packages
find app/src/main/kotlin/com/bernelius/abrechnung -type d | while read dir; do
    pkg=$(basename "$dir")
    if [ "$pkg" != "abrechnung" ]; then
        grep -q "$pkg" docs/architecture.md || echo "Undocumented package: $pkg"
    fi
done

# Check for new files not mentioned
find app/src/main/kotlin -name "*.kt" -newer docs/architecture.md | while read file; do
    filename=$(basename "$file" .kt)
    grep -q "$filename" docs/architecture.md || echo "New file not documented: $file"
done

# Verify all documented packages exist
awk '/^[[:space:]]*├──/ {print $0}' docs/architecture.md | sed 's/├── //;s/\//;/' | while read pkg; do
    [ -z "$pkg" ] && continue
    [ "$pkg" = "# Main source code" ] && continue
    [ -d "app/src/main/kotlin/com/bernelius/abrechnung/$pkg" ] || echo "Documented package not found: $pkg"
done
```

## Handoff Procedure

When work on this codebase is being handed to another agent or human developer:

1. **Review the architecture document** together
2. **Run verification commands** to identify any gaps
3. **Note any discrepancies** discovered during review
4. **Update documentation** for any inconsistencies found
5. **Document known issues** or areas needing clarification
6. **Update this agents.md** file if procedures have changed

## Conclusion

The architecture documentation is a living document that must evolve with the codebase. Keeping it accurate ensures:

- Faster onboarding for new developers
- Better architectural decision-making
- Clear understanding of system behavior
- Prevention of regressions through awareness of design patterns
- Effective codebase maintenance and extension

**Remember**: The goal is not perfection but accuracy. It's better to have incomplete documentation marked with TODOs than incorrect documentation that misleads developers.
