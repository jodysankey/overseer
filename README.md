#Overseer

## Overview

Overseer is a small Java application to run tasks periodically while certain conditions are met, and to report
the success or failure of those tasks over a WebSocket interface. Some functionality might work on Windows, but
I've only tested on Linux.

Specifically I use it to run a couple of different [unison](https://www.cis.upenn.edu/~bcpierce/unison/)
synchronization commands periodically while I'm connected to my home network. There is a KDE5 plasmoid I leave
in a toolbar to show the time since the last execution and the outcome of that execution, and to trigger a
restart if necessary. This makes it easy to keep files synchronized continually, and to verify the age of the
last sync. 

The invocation I use is as follows

```
/usr/bin/java -jar /use/local/bin/Overseerbundle.jar \
  --command "unison jody -batch" \
  --command "unison family -batch" \
  --socket 4321 \
  --status_file /home/%u/.overseer.status \
  --log_file /var/log/overseer_unison.log
```

This starts Overseer to run unison in batch mode on two different configurations, starts the status reporting
socket on port 4321, writes a status log (to retain state across restarts) in the user's home directory, and
a log file in `/var/log`.

More invocation details are available using the `--help` flag.


## Components

* `src/main/java/com/jsankey` contains source files for the Java application, described in more detail below
* `src/test/java/com/jsankey` contains [partial] unit tests for the Java application
* `build.gradle` is a gradle build configuration for the project, including a `jarBundle` task that uses OneJar
  to compile Overseer and all its dependencies into a single jar at `build/libs/OverseerBundle.jar`
* `overseer_check.html` is a web page that uses WebSockets to communicate with Overseer on a [currently hardcoded]
  port to display status and trigger restarts
* `overseer_check.py` is a simple python script that communicates with Overseer over a plaintext socket to display
  status. Since this is the only thing not using websockets it might not be available in the future
* `plasmoid` contains a KDE5 plasmoid to display status. This has both a compact form suitable for a toolbar and an
  expanded form showing more details about the outcome of each command and facilitating status. The port is again
  hardcoded


## Package Level Design

* `com.jsankey.overser` Contains the core of the program, including *Configuration* to interpret and store command
  line options and *Executive* to handle the scheduling.
* `com.jsankey.overser.checks` contains the gating conditions on execution. Currently the only one of these is
  to wait for a named Wifi network. The current implementation depends on `iwconfig` so is not cross platform.
* `com.jsankey.overser.history` maintains a history of recent executions for each command and handles its
  serialzation/deserialization to the status file.
* `com.jsankey.overser.io` handles everything to do with sockets including creation, thread management, WebSocket
  upgrade and command parsing.
* `com.jsankey.overser.runner` handles the initiation and termination of the external commands Overseer exists
  to oversee.
* `com.jsankey.overser.util` contains a couple of generic utility classes.


## Known Issues

* Unit test coverage isn't complete
* Plasmoid makefile isn't complete
* Plasmoid and HTML should have a configuration to set the port
* Proper packaging (e.g. Deb file) would be nice


## Future Plans

Right now the project is meeting my needs and I'm not planning on doing a lot of maintenance unless things break
for my use case. However, it there is something that you'd like or if you find any bugs let me know. I'd consider
adding extra features or taking this further.
