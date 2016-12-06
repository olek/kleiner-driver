#!/usr/bin/env bash

lein var-file

docker run -it --rm \
       --env-file .env-vars \
       -p 8080:8080 \
       kleiner/driver
