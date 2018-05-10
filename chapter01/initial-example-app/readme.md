# initial-example-app

Example code from the Mastering Akka book

## How to build

To build and package the bookstore example as a docker application, run the script `docker-build.sh`. This script
will instruct `sbt` to build the application, package the application and build a docker image, tag it and store
it in the local docker repository.

To launch the project which includes launching the bookstore example and the postgres database including provisioning it
with a schema run the script `launch.sh`. 

Because we gave you a choice in which Docker flavor to run, and because each different flavor will bind to different
local addresses, we need a consistent way to refer to the host address that is being used by Docker.
The easiest way to do this is to add an entry to your hosts file, setting up an alias for a host called `boot2docker`.
We can then use that alias going forward to when referring to the local Docker bind address,
both in the scripts provided in the code content for this book and in any examples in the book content.

The entry we need to add to this file will be the same regardless of if you are on Windows or a Mac.
This is the format of the entry that you will need to add to that file:
```
<docker_ip>    boot2docker
```

You will need to replace the `<docker_ip>` portion of that line with whatever local host your Docker install is using.
So for example, if you installed the **native Docker app**, then the line would look like this:
```
127.0.0.1      boot2docker
```

And if you installed **Docker Toolkit** and are thus using `docker-machine`, then the line would look like this:
```
192.168.99.100 boot2docker
```

The location of that file will be different depending on if you are running Windows or are on a Mac.
If you are on Windows, then the file an be found at the following location: `C:\Windows\System32\Drivers\etc\hosts`.
If you are running in a Mac, then the file can be found here: `/etc/hosts`.

## Try it

Now you can reference the docker environment in the URL by `boot2docker` instead of the ip address. 

To create a user, simply do:

```
http -v post boot2docker:8080/api/user firstName=Chris lastName=Baxter email=chris@masteringakka.com
```
