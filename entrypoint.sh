#!/bin/sh -l

echo "Org ID: $INPUT_ORGID"
echo "Project Name: $INPUT_PROJECTNAME"
echo "Project ID: $INPUT_PROJECTID"
echo "API URL $INPUT_APIURL"
echo "Artifact: $INPUT_ARTIFACT"
echo "SARIF Output: $INPUT_SARIF"
echo "Languages: $INPUT_LANGUAGE"
echo "Timeout: $INPUT_TIMEOUT"
echo "SaveScanResults: $INPUT_SAVESCANRESULTS"

set -x #echo on

/usr/bin/contrast scan --file "$INPUT_ARTIFACT" --api-key "$INPUT_APIKEY" --authorization "$INPUT_AUTHHEADER" \
 ${INPUT_ORGID:+"--organization-id"} ${INPUT_ORGID:+"$INPUT_ORGID"} --host "$INPUT_APIURL" \
 ${INPUT_PROJECTNAME:+"--name"} ${INPUT_PROJECTNAME:+"$INPUT_PROJECTNAME"} \
 ${INPUT_PROJECTID:+"--project-id"} ${INPUT_PROJECTID:+"$INPUT_INPUT_PROJECTID"}  \
 ${INPUT_LANGUAGE:+"--language"} ${INPUT_LANGUAGE:+"$INPUT_LANGUAGE"} --timeout "${INPUT_TIMEOUT}" \
 -s sarif