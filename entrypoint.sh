#!/bin/sh -l

echo "Org ID: $INPUT_ORGID"
echo "Project Name: $INPUT_PROJECTNAME"
echo "API URL $INPUT_APIURL"
echo "Artifact: $INPUT_ARTIFACT"
echo "SARIF Output: $INPUT_SARIF"
echo "Languages: $INPUT_LANGUAGE"
echo "Timeout: $INPUT_TIMEOUT"
echo "WaitForScan: $INPUT_WAITFORSCAN"
echo "SaveScanResults: $INPUT_SAVESCANRESULTS"

set -x #echo on

contrast-cli --scan "$INPUT_ARTIFACT" --api_key "$INPUT_APIKEY" \
 --authorization "$INPUT_AUTHHEADER" --organization_id "$INPUT_ORGID" --host "$INPUT_APIURL" \
 --project_name "$INPUT_PROJECTNAME" ${INPUT_LANGUAGE:+"--language"} ${INPUT_LANGUAGE:+"$INPUT_LANGUAGE"} --scan_timeout "${INPUT_TIMEOUT:-300}" \
 ${INPUT_WAITFORSCAN:+"--wait_for_scan"} ${INPUT_SAVESCANRESULTS:+"--save_scan_results"} ${INPUT_SAVESCANRESULTS:+"--results_file_name"} ${INPUT_SAVESCANRESULTS:+"$INPUT_SARIF"}

export CONTRAST_CLI_EXIT_CODE=$?

if [ ${CONTRAST_CLI_EXIT_CODE} != 0 ]; then
  echo "Contrast Scan failed. Please contact support"
  exit 1
fi
