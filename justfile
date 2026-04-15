build:
    ./gradlew :app:shadowJar

build-native:
    export JAVA_HOME="$HOME/.sdkman/candidates/java/24-graal" && export PATH="$JAVA_HOME/bin:$PATH" && ./gradlew :app:nativeCompile --no-daemon --info

run:
    java --enable-native-access=ALL-UNNAMED -jar app/build/libs/abrechnung-all.jar AppKt

run-local:
    ABRECHNUNG_DB_URL="" java --enable-native-access=ALL-UNNAMED -jar app/build/libs/abrechnung-all.jar AppKt

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
