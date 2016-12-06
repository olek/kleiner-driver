#!/usr/bin/env bash

DOCKERHOST_IP=$(ifconfig utun0 2>/dev/null|grep inet\ |awk '{print $2}')
: ${DOCKERHOST_IP:=$(ifconfig en0|grep inet\ |awk '{print $2}')}

export DOCKERHOST_IP

echo ================================================================================
echo 'Stopping all containers'
docker stop $(docker ps -a -q) faa

echo ================================================================================
echo 'Removing all containers'
docker rm $(docker ps -a -q) faa

docker ps -a -q

# exit 0

cd ..

echo ================================================================================
echo 'Building Router'
cd kleiner-router
git pull
docker build --rm -t kleiner_router .

echo ================================================================================
echo 'Building Sentiment'
cd ../kleiner-sentiment
git pull
docker build --rm -t kleiner_sentiment .

echo ================================================================================
echo 'Building Scaler'
cd ../kleiner-scaler
git pull
docker build --rm -t kleiner_scaler .

echo ================================================================================
echo 'Building Driver'
cd ../kleiner-driver
git pull
docker build --rm -t kleiner_driver .

echo ================================================================================
echo 'Building UI'
cd ../kleiner-ui
git pull
docker build --rm -t kleiner_ui .

docker pull progrium/consul

echo ================================================================================
echo 'Starting consul'
docker run \
     --name consul \
     -d \
     -p 8400:8400 \
     -p 8500:8500 \
     -p 8600:53/udp \
     -h node1 \
     progrium/consul \
     -server \
     -bootstrap \
     -ui-dir /ui

echo ================================================================================
echo 'Starting kleiner_scaler'
docker run \
     --name scaler \
     -d \
     kleiner_scaler \
     java -XX:+UseG1GC -XX:+UseCompressedOops -Xmx250m -jar /app/build/libs/kleiner-scaler-1.0-SNAPSHOT-all.jar

echo ================================================================================
echo 'Starting kleiner_sentiment'
docker run \
     --name sentiment \
     -d \
     kleiner_sentiment

echo ================================================================================
echo 'Starting kleiner_router'
docker run \
     --name router \
     --link scaler \
     --link sentiment \
     -d \
     -e HOST_PORT=8081 \
     -e SCALER_HOST=scaler \
     -e CONSUL_HOST=$DOCKERHOST_IP \
     -e OVERRIDE_MODEL_API_HOST=sentiment \
     -e WORKER_THREADS=1000 \
     kleiner_router \
     java -XX:+UseG1GC -XX:+UseCompressedOops -Xmx1g -jar /app/build/libs/kleiner-router-1.0-SNAPSHOT-all.jar

sleep 1

echo ================================================================================
echo 'Starting kleiner_driver'
docker run \
     --name driver \
     --link router \
     -d \
     -e THREADPOOL_SIZE=100 \
     -e TARGET_PORT=8081 \
     -e TARGET_HOST=router \
     -e TARGET_PATH="/" \
     -p 8080:8080 \
     kleiner_driver

echo ================================================================================
echo 'Starting kleiner_ui'
docker run \
     --name ui \
     --link driver \
     -d \
     -e DRIVER_HOST=driver \
     -e DRIVER_PORT=8080 \
     -p 4001:4000 \
     kleiner_ui

echo ================================================================================
echo 'Monitoring logs of kleiner_driver'
docker logs -f $(docker ps --quiet --filter="ancestor=kleiner_driver")
