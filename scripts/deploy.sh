#!/bin/bash
set -e

# -------------------
# Set these variables for your service
IMAGE_REPOSITORY="kleiner/driver"
NOMAD_JOB="nomad.hcl"
# -------------------

GIT_SHA=$(git rev-parse HEAD)

echo "* Authenticating to the docker registry"
eval $(aws ecr get-login --region us-west-2)
echo

echo "* Creating docker repository if it doesn't exist"
aws ecr describe-repositories --region us-west-2 --repository-names $IMAGE_REPOSITORY 1>/dev/null ||
	aws ecr create-repository --region us-west-2 --repository-name $IMAGE_REPOSITORY ||
echo

echo "* Building and tagging 483510943701.dkr.ecr.us-west-2.amazonaws.com/$IMAGE_REPOSITORY:$GIT_SHA"
docker pull 483510943701.dkr.ecr.us-west-2.amazonaws.com/$IMAGE_REPOSITORY:latest || true
docker pull 483510943701.dkr.ecr.us-west-2.amazonaws.com/$IMAGE_REPOSITORY:$GIT_SHA ||
	docker build -t 483510943701.dkr.ecr.us-west-2.amazonaws.com/$IMAGE_REPOSITORY:$GIT_SHA .
echo

echo "* Pushing 483510943701.dkr.ecr.us-west-2.amazonaws.com/$IMAGE_REPOSITORY:$GIT_SHA"
docker push 483510943701.dkr.ecr.us-west-2.amazonaws.com/$IMAGE_REPOSITORY:$GIT_SHA
echo

echo "* Pushing 483510943701.dkr.ecr.us-west-2.amazonaws.com/$IMAGE_REPOSITORY:latest"
docker tag 483510943701.dkr.ecr.us-west-2.amazonaws.com/$IMAGE_REPOSITORY:$GIT_SHA 483510943701.dkr.ecr.us-west-2.amazonaws.com/$IMAGE_REPOSITORY:latest
docker push 483510943701.dkr.ecr.us-west-2.amazonaws.com/$IMAGE_REPOSITORY:latest

echo "* Changing nomad image reference to 483510943701.dkr.ecr.us-west-2.amazonaws.com/$IMAGE_REPOSITORY:$GIT_SHA"
scp $NOMAD_JOB ubuntu@nomad-server.kleiner.ml:/tmp/$NOMAD_JOB
ssh ubuntu@nomad-server.kleiner.ml sed -i -- "s/latest/$GIT_SHA/g" /tmp/$NOMAD_JOB
echo

echo "* Planning nomad job"
RUN_COMMAND=$(ssh ubuntu@nomad-server.kleiner.ml nomad plan /tmp/$NOMAD_JOB | grep 'nomad run')
ssh ubuntu@nomad-server.kleiner.ml nomad plan /tmp/$NOMAD_JOB || true
echo

echo -n "* Run above plan? [Y/n] "
read REPLY </dev/tty

case "$REPLY" in
    Y*|y*) ssh ubuntu@nomad-server.kleiner.ml $RUN_COMMAND ;;
    *) echo "  * Aborted" ;;
esac
