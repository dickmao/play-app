FROM circleci/openjdk:8-jdk-node-browsers

# Cite: hseeberger/scala-sbt (top google hit as of 20180401)
# Usage: 
#   docker build -t circleci:play-app .
#   docker tag circleci:play-app 303634175659.dkr.ecr.us-east-2.amazonaws.com/circleci:play-app
#   eval `aws ecr get-login --no-include-email --region us-east-2`
#   docker push 303634175659.dkr.ecr.us-east-2.amazonaws.com/circleci:play-app

# Env variables
ENV SCALA_VERSION 2.11.11
ENV SBT_VERSION 0.13.15

USER circleci
WORKDIR /home/circleci

# Scala expects this file
RUN sudo touch /usr/lib/jvm/java-8-openjdk-amd64/release

# Install Scala
## Piping curl directly in tar
RUN \
  curl -fsL https://downloads.typesafe.com/scala/$SCALA_VERSION/scala-$SCALA_VERSION.tgz | tar xfz - -C . && \
  echo >> ./.bashrc && \
  echo "export PATH=~/scala-$SCALA_VERSION/bin:$PATH" >> ./.bashrc

# Install sbt
RUN \
  curl -L -o ./sbt-$SBT_VERSION.deb https://dl.bintray.com/sbt/debian/sbt-$SBT_VERSION.deb && \
  sudo dpkg -i sbt-$SBT_VERSION.deb && \
  rm ./sbt-$SBT_VERSION.deb && \
  sudo apt-get -yq update && \
  DEBIAN_FRONTEND=noninteractive sudo apt-get -yq install jq python-pip netcat-openbsd rsync sbt && \
  sudo apt-get clean && \
  sudo rm -rf /var/lib/apt/lists/* && \
  sudo pip install awscli && \
  sbt sbtVersion

# How did that get in there?
RUN \
  rm -rf ./project
