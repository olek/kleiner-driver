#!/bin/bash
set -v -e
GIT_SHA=$(git rev-parse HEAD)
env
echo Building the Docker image...
echo Tagging as $ECR_IMAGE_NAME:$GIT_SHA
docker build -t $ECR_IMAGE_NAME:$GIT_SHA .
docker tag $ECR_IMAGE_NAME:$GIT_SHA $ECR_REPOSITORY_PATH/$ECR_IMAGE_NAME:$GIT_SHA
docker tag $ECR_IMAGE_NAME:$GIT_SHA $ECR_REPOSITORY_PATH/$ECR_IMAGE_NAME:latest
echo Pushing docker image to tag $GIT_SHA
docker push $ECR_REPOSITORY_PATH/$ECR_IMAGE_NAME:$GIT_SHA
