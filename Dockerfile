# Container image that runs your code
FROM openjdk:jdk

# Copies your code file from your action repository to the filesystem path `/` of the container
COPY entrypoint.sh /entrypoint.sh
COPY contrastscan-remote/target/contrastscan-remote-*.jar /contrastscan-remote.jar

# Code file to execute when the docker container starts up (`entrypoint.sh`)
ENTRYPOINT ["/entrypoint.sh"]
