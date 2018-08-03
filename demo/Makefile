# Just a Makefile for manual testing
.PHONY: all

ARTIFACT_ID = jenkins-external-task-logging-elk-demo
VERSION = 2.131-elk-SNAPSHOT
CWP_VERSION= 1.0

all: clean build

clean:
	rm -rf tmp
	mkdir tmp

build: tmp/output/target/${ARTIFACT_ID}-${VERSION}.war

tmp/output/target/${ARTIFACT_ID}-${VERSION}.war:
	mvn com.googlecode.maven-download-plugin:download-maven-plugin:1.4.0:artifact \
		-DgroupId=io.jenkins.tools.custom-war-packager \
		-DartifactId=custom-war-packager-cli \
		-Dclassifier=jar-with-dependencies \
		-Dversion=${CWP_VERSION} \
		-DoutputDirectory=tmp \
		-DoutputFileName=cwp-cli.jar
	java -jar tmp/cwp-cli.jar \
	    -configPath packager-config.yml -version ${VERSION}

run: tmp/output/target/${ARTIFACT_ID}-${VERSION}.war
	docker-compose rm -fv
	docker-compose up --build --force-recreate jenkins elk

debug: tmp/output/target/${ARTIFACT_ID}-${VERSION}.war
	docker-compose rm -fv
	docker-compose up --build --force-recreate jenkinsDebug elk
