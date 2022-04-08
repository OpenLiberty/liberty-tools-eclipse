#!/bin/bash

set -Ex

# Current time.
currentTime=(date +"%Y/%m/%d-%H:%M:%S:%3N")

main() {
    echo -e "\n> $(${currentTime[@]}): Build: Building the plugin"

    # Tell the terminal session to use display port 77.
    export DISPLAY=:77.0

    # Start the X display server on port 77.
    Xvfb -ac :77 -screen 0 1280x1024x16 > /dev/null 2>&1 &

    #  Start the window manager.
    metacity --sm-disable --replace 2> metacity.err &

    # Run the plugin's install goal.
    mvn clean install 

    # If there were any errors, gather some debug data before exiting.
    rc=$?
    if [ "$rc" -ne 0 ]; then
        echo "ERROR: Failure while driving mvn install on plugin. rc: ${rc}."

        echo "DEBUG: Liberty messages.log:\n"
        cat tests/applications/maven/liberty-maven-test-app/target/liberty/wlp/usr/servers/defaultServer/logs/messages.log

        echo "DEBUG: Environment variables:\n"
        env

        exit -1
    fi
}

main "$@"