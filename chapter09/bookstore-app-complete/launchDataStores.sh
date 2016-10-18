#!/bin/bash
export VM_HOST=boot2docker

# Wait for a certain service to become available
# Usage: wait 3306 Mysql
wait() {
while true; do
  if ! nc -z $VM_HOST $1
  then
    echo "$2 not available, retrying..."
    sleep 1
  else
    echo "$2 is available"
    break;
  fi
done;
}

echo "Launching Cassandra and Elasticsearch"
echo "This can take a couple of seconds"
docker rm -f $(docker ps -aq)
docker-compose up -d
wait 9200 Elasticsearch
wait 9042 Cassandra