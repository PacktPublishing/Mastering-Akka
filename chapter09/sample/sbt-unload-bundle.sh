#!/bin/bash
export CONDUCTR_IP=$(docker-machine ip default)
sbt "conduct unload --ip 192.168.99.100 chapter9-sample"