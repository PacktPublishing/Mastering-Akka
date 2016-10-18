#!/bin/bash
eval "$(docker-machine env default)"
docker rm -f $(docker ps -aq)