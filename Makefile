# Antiyoy — desktop build/run helpers.
#
# The upstream repo ships only core/src + assets; the Gradle scaffolding and
# desktop launcher are additions. See RUNNING.md for details.
#
#   make run     build if needed, then launch the game
#   make build   compile only
#   make apk     build a debug Android APK
#   make clean   remove build output
#   make sprites rebuild atlas textures + _low/_lowest pngs from full-size pngs

# Gradle needs a JDK (a JRE is not enough). Respect an already-exported
# JAVA_HOME if it points at a real JDK, otherwise fall back to the one
# unpacked under /opt/java. The glob keeps this working across patch
# version bumps.
JDK_FALLBACK := $(firstword $(wildcard /opt/java/jdk-21*))

ifeq ($(wildcard $(JAVA_HOME)/bin/javac),)
  ifneq ($(strip $(JDK_FALLBACK)),)
    export JAVA_HOME := $(JDK_FALLBACK)
  endif
endif

GRADLE := ./gradlew

.DEFAULT_GOAL := run

.PHONY: run build apk clean check-jdk sprites validate-ai-docs

# Atlas directories to rebuild. field_elements is where sprite editing happens;
# add more (e.g. assets/fog_of_war) if their pngs get edited too.
SPRITE_DIRS := assets/field_elements

sprites: check-jdk
	@mkdir -p build/tools
	"$(JAVA_HOME)/bin/javac" tools/RebuildAtlas.java -d build/tools
	@for dir in $(SPRITE_DIRS); do \
		echo "== $$dir"; \
		"$(JAVA_HOME)/bin/java" -Djava.awt.headless=true -cp build/tools RebuildAtlas $$dir || exit 1; \
	done

validate-ai-docs:
	@sh tools/validate_ai_docs.sh

run: check-jdk
	$(GRADLE) :desktop:run

build: check-jdk
	$(GRADLE) :desktop:build

apk: check-jdk
	$(GRADLE) :android:assembleDebug
	@echo ""
	@echo "APK: android/build/outputs/apk/debug/android-debug.apk"

clean: check-jdk
	$(GRADLE) clean

check-jdk:
	@if [ ! -x "$(JAVA_HOME)/bin/javac" ]; then \
		echo "No JDK found (JAVA_HOME=$(JAVA_HOME))."; \
		echo ""; \
		echo "Install one without root:"; \
		echo "  mkdir -p ~/.local/opt && cd ~/.local/opt"; \
		echo "  curl -L -o jdk21.tar.gz \\"; \
		echo "    'https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse'"; \
		echo "  tar xzf jdk21.tar.gz && rm jdk21.tar.gz"; \
		echo ""; \
		echo "Or point JAVA_HOME at an existing JDK 17+."; \
		exit 1; \
	fi
