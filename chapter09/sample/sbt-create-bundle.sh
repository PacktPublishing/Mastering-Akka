#!/bin/bash
export CONDUCTR_IP=$(docker-machine ip default)
sbt bundle:dist