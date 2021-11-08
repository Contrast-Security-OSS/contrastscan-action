#!/bin/sh -l

echo "scanning artifact $INPUT_ARTIFACT"
echo "output_sarif $INPUT_SARIF"
echo "url $INPUT_API_URL"
java -jar /contrastscan-remote.jar --sarif-out=$INPUT_SARIF --url=$INPUT_API_URL $INPUT_ARTIFACT
