#!/bin/bash
docker rm -f $(docker ps -aq)
docker run -d -p 8080:8080 initial-example-server:latest