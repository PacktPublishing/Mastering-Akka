# chapter 8 - bookstore-app-complete
Example code from the Mastering Akka book

# Difference From chapter 8 content instructions
If you have the print version of the book, the instructions at the end of chapter 8 refer to starting up the 2 cluster nodes without using docker.  Please note that this is a mistake and was not updated properly when the switch to fully using Docker in all chapters was made.  It's much simpler now in that all you need to do as usual is run `docker.build.sh` and then `launch.sh` and you will have both cluster nodes running.

Also note that the instructions from the chapter 8 content in the print version of the book discuss clearing out cassandra and elasticsearch and this is actually not necessary as starting up the containers within docker will start with a clean system.

# How to build
To build and package the bookstore example as a docker application, run the script `docker-build.sh`. This script
will instruct sbt to build the application, package the application and build a docker image, tag it and store
it in the local docker repository.

To launch the project which includes launching the bookstore example, the Cassandra database and Elasticsearch.

When docker has been installed on OSX for example, using Virtualbox, a virtual machine should be available on the 
default IP address: 192.168.99.100, which is simple to give an alias by adding the following line to your /etc/hosts file:

```
192.168.99.100 boot2docker
```

Now you can reference the docker environment in the URL by `boot2docker` instead of the ip address. 

To create a user, simply do:

```
http -v post boot2docker:8080/api/user firstName=Chris lastName=Baxter email=chris@masteringakka.com
```