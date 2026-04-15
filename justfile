build:
    ./gradlew :app:shadowJar

build-native:
    ./gradlew :app:nativeCompile

run:
    java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow -jar app/build/libs/abrechnung-all.jar AppKt

run-local:
    ABRECHNUNG_DB_URL="" java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow -jar app/build/libs/abrechnung-all.jar AppKt

run-native:
    ./app/build/native/nativeCompile/app

run-native-local:
    ABRECHNUNG_DB_URL="" ./app/build/native/nativeCompile/app

test ARG="":
    ./gradlew :app:test {{ ARG }}

TEST ARG="":
    ./gradlew :app:test --rerun-tasks {{ ARG }}

check:
    ./gradlew ktlintCheck

format:
    ./gradlew ktlintFormat

report:
    ./gradlew koverHtmlReport
