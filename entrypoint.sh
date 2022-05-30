#!/bin/sh -l

echo "Org ID: $INPUT_ORGID"
echo "Project Name: $INPUT_PROJECTNAME"
echo "Project ID: $INPUT_PROJECTID"
echo "API URL: $INPUT_APIURL"
echo "Artifact: $INPUT_ARTIFACT"
echo "Language: $INPUT_LANGUAGE"
echo "Timeout: $INPUT_TIMEOUT"

[ -z "$INPUT_ORGID" ] && echo "Organization ID is required but not present" && exit 1;
[ -z "$INPUT_ARTIFACT" ] && echo "Artifact is required but not present" && exit 1;
[ -z "$INPUT_APIKEY" ] && echo "Contrast API Key is required but not present" && exit 1;
[ -z "$INPUT_AUTHHEADER" ] && echo "Contrast Authorization Header is required but not present" && exit 1;

/usr/bin/contrast scan --file "$INPUT_ARTIFACT" --api-key "$INPUT_APIKEY" --authorization "$INPUT_AUTHHEADER" \
 --organization-id "$INPUT_ORGID" --host "$INPUT_APIURL" \
 ${INPUT_PROJECTNAME:+"--name"} ${INPUT_PROJECTNAME:+"$INPUT_PROJECTNAME"} \
 ${INPUT_PROJECTID:+"--project-id"} ${INPUT_PROJECTID:+"$INPUT_PROJECTID"}  \
 --timeout "${INPUT_TIMEOUT}" -s sarif

 CONTRAST_RET_VAL=$?
 if [ $CONTRAST_RET_VAL -ne 0 ]; then
     echo "An error occurred while executing the Scan. Please contact support."
 fi

exit $CONTRAST_RET_VAL