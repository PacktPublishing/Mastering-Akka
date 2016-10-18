#!/bin/bash
export CONDUCTR_IP=boot2docker
export HELLOWORLD_ENDPOINT=web
http GET $CONDUCTR_IP:9000/$HELLOWORLD_ENDPOINT/helloworld
http POST $CONDUCTR_IP:9000/$HELLOWORLD_ENDPOINT/person name=John age:=35
