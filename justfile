build:
    ./gradlew :app:shadowJar

run:
    java --enable-native-access=ALL-UNNAMED -jar app/build/libs/abrechnung-all.jar AppKt

run-local:
    ABRECHNUNG_DB_URL="" java --enable-native-access=ALL-UNNAMED -jar app/build/libs/abrechnung-all.jar AppKt

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
