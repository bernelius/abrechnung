[script]
bump-version:
    #!/usr/bin/env bash
    function read_version {
        local last=$1
        read -e -i "$last_tag" -p "New version:  " tag
        echo "$tag"
    }

    set -euo pipefail
    last_tag=$(git describe --tags --abbrev=0)

    echo "Last version: ${last_tag}"
    tag=$(read_version "$last_tag")
    while [[ "$tag" == "$last_tag" ]]; do
        echo "Version must be different from the last version"
        tag=$(read_version "$last_tag")
    done
    echo "New tag: $tag. Ok? [y/n]"
    read -n 1 answer
    echo
    if [[ "$answer" != "y" ]]; then
        echo "Aborted"
        exit 1
    fi
    version="${tag#v}"
    echo "$version" > VERSION
    git tag -a "$tag" -m "Release $tag"
    echo "VERSION set to $version"
    echo "Remember to git push --tags"

build:
    ./gradlew :app:shadowJar

build-native:
    ./gradlew :app:nativeCompile --no-configuration-cache

run:
    java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow -jar app/build/libs/abrechnung-all.jar AppKt

run-local:
    ABRECHNUNG_DB_URL="" java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow -jar app/build/libs/abrechnung-all.jar AppKt

run-native:
    ./app/build/native/nativeCompile/abrechnung

run-native-local:
    ABRECHNUNG_DB_URL="" ./app/build/native/nativeCompile/abrechnung

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
