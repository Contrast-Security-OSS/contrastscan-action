# Container image that runs your code
FROM node:alpine

RUN npm install -g @contrast/contrast-cli && npm install -g node-jq

# Copies your code file from your action repository to the filesystem path `/` of the container
COPY entrypoint.sh /entrypoint.sh

# Code file to execute when the docker container starts up (`entrypoint.sh`)
ENTRYPOINT ["/entrypoint.sh"]
