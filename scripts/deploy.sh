#!/bin/bash
set -e

# -------------------
# Set these variables for your service
PORT="8080"
NAME="driver"
IMAGE_REPOSITORY="kleiner/$NAME"
NOMAD_JOB="nomad.hcl"
# -------------------

GIT_SHA=$(git rev-parse HEAD)

echo "* Authenticating to the docker registry"
eval $(aws ecr get-login --region us-west-2)
echo

echo "* Creating docker repository if it doesn't exist"
aws ecr describe-repositories --region us-west-2 --repository-names $IMAGE_REPOSITORY 1>/dev/null ||
        aws ecr create-repository --region us-west-2 --repository-name $IMAGE_REPOSITORY
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

export KUBECONFIG="$(pwd)/../kleinernetes/kubeconfig"

cat <<EOF > /tmp/kubes.yaml
apiVersion: extensions/v1beta1
kind: Deployment
metadata:
  name: $NAME
  labels:
    app: $NAME
spec:
  replicas: 1
  selector:
    matchLabels:
      app: $NAME
  strategy:
    type: Recreate
  revisionHistoryLimit: 1
  template:
    metadata:
      labels:
        app: $NAME
        version: $GIT_SHA
    spec:
      containers:
      - name: $NAME
        image: 483510943701.dkr.ecr.us-west-2.amazonaws.com/$IMAGE_REPOSITORY:$GIT_SHA
        ports:
        - containerPort: $PORT
        resources:
          requests:
            cpu: "2"
            memory: 1Gi
        env:
        - name: TARGET_HOST
          value: router
        - name: TARGET_PORT
          value: "80"
        - name: TARGET_PATH
          value: "/api/sentiment/v1"
        - name: MAX_TARGET_CONNECTIONS
          value: "50"
        - name: API_THREADPOOL_SIZE
          value: "9"
EOF

echo "Kubes resource configuration:"
echo ================================================================================
cat /tmp/kubes.yaml
echo ================================================================================

# initial/first deployment command
#RUN_COMMAND="kubectl create -f /tmp/kubes.yaml"

# iterative deployment command
RUN_COMMAND="kubectl apply -f /tmp/kubes.yaml"


echo "About to run"
echo $RUN_COMMAND

echo -n "* Deploy for realsies? [y/N] "
read REPLY </dev/tty

case "$REPLY" in
    Y*|y*) cd ../kleinernetes; $RUN_COMMAND ;;
    *) echo "  * Aborted" ;;
esac

# ======================================== Detritus lives here

#apiVersion: v1
#kind: Service
#metadata:
#  name: $NAME
#  labels:
#    app: $NAME
#spec:
#  type: LoadBalancer
#  ports:
#  - port: $PORT
#  selector:
#    app: $NAME
#---

#PATCH=$(cat <<EOF
#{
#  "spec":{
#    "template":{
#      "metadata":{
#        "labels":{
#          "app":"driver",
#          "version":"$GIT_SHA"
#        }
#      },
#      "spec":{
#        "containers":[
#          {"name":"driver","image":"483510943701.dkr.ecr.us-west-2.amazonaws.com/kleiner/driver:$GIT_SHA"}
#        ]
#      }
#    }
#  }
#}
#EOF
#)
#PATCH="${PATCH//$'\n'/}"
#PATCH="${PATCH// /}"
#RUN_COMMAND="kubectl patch deployment driver --record -p$PATCH"

#RUN_COMMAND="kubectl \
#rolling-update \
#driver \
#--image-pull-policy=IfNotPresent \
#--image=483510943701.dkr.ecr.us-west-2.amazonaws.com/$IMAGE_REPOSITORY:$GIT_SHA"

#RUN_COMMAND="kubectl \
#run \
#driver \
#--image=483510943701.dkr.ecr.us-west-2.amazonaws.com/$IMAGE_REPOSITORY:$GIT_SHA \
#--port=8080 \
#--env="TARGET_HOST=router" \
#--env="TARGET_PORT=80" \
#--env="MAX_TARGET_CONNECTIONS=300" \
#--env="API_THREADPOOL_SIZE=309" \
#--labels=version=$GIT_SHA,name=driver"

#echo "* Changing nomad image reference to 483510943701.dkr.ecr.us-west-2.amazonaws.com/$IMAGE_REPOSITORY:$GIT_SHA"
#scp $NOMAD_JOB ubuntu@nomad-server.kleiner.ml:/tmp/$NOMAD_JOB
#ssh ubuntu@nomad-server.kleiner.ml sed -i -- "s/latest/$GIT_SHA/g" /tmp/$NOMAD_JOB
#echo

#echo "* Planning nomad job"
#RUN_COMMAND=$(ssh ubuntu@nomad-server.kleiner.ml nomad plan /tmp/$NOMAD_JOB | grep 'nomad run')
#ssh ubuntu@nomad-server.kleiner.ml nomad plan /tmp/$NOMAD_JOB || true
#echo

#    Y*|y*) ssh ubuntu@nomad-server.kleiner.ml $RUN_COMMAND ;;
