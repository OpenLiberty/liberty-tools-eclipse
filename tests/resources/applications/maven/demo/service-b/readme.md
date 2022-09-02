# MicroProfile generated Application

## Introduction

MicroProfile Starter has generated this MicroProfile application for you containing some endpoints which are called from the main application (see the `service-a` directory)

The generation of the executable jar file can be performed by issuing the following command

    mvn clean package

This will create an executable jar file **demo.jar** within the _target_ maven folder. This can be started by executing the following command
    java -jar target/demo.jar 



### Liberty's Dev Mode

During development, you can use Liberty's development mode (dev mode) to code while observing and testing your changes on the fly.
With the dev mode, you can code along and watch the change reflected in the running server right away; 
unit and integration tests are run on pressing Enter in the command terminal; you can attach a debugger to the running server at any time to step through your code.


    mvn liberty:dev




## Specification examples




### Rest Client

A type safe invocation of HTTP rest endpoints. Specification [here](https://microprofile.io/project/eclipse/microprofile-rest-client)

The example calls one endpoint from another JAX-RS resource where generated Rest Client is injected as CDI bean.
