# initial-example-app
Example code from the Mastering Akka book

# How to build
To build and package the bookstore example as a docker application, run the script `docker-build.sh`. This script
will instruct sbt to build the application, package the application and build a docker image, tag it and store
it in the local docker repository.

To launch the project which includes launching the bookstore example and the postgres database including provisioning it
with a schema run the script `launch.sh`. 

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