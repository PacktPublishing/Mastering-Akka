#!/bin/bash
docker-machine start default
eval $(docker-machine env default --shell=bash)
export CONDUCTR_IP=$(docker-machine ip default)
echo "Conductr ip: $CONDUCTR_IP"
sandbox run 1.1.8 --feature visualization --nr-of-containers 3 -e DOCKER_HOST_IP=$CONDUCTR_IP
open http://$CONDUCTR_IP:9999