#!/bin/bash
echo "=================================================================="
echo "Cassandra: https://hub.docker.com/_/cassandra/"
echo "Doc: http://docs.datastax.com/en/cassandra/2.1/cassandra/cql.html"
echo "==================     Help for cqlsh    ========================="
echo "DESCRIBE tables            : Prints all tables in the current keyspace"
echo "DESCRIBE keyspaces         : Prints all keyspaces in the current cluster"
echo "DESCRIBE <table_name>      : Prints table detail information"
echo "help                       : for more cqlsh commands"
echo "help [cqlsh command]       : Gives information about cqlsh commands"
echo "quit                       : quit"
echo "=================================================================="
docker exec -it cassandra cqlsh