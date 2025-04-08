# Kestra Docker Compose Plugin

> 🚧 WIP.

> Created with Kestra plugins template

A naive implementation of a [Kestra](https://github.com/kestra-io/kestra) plugin to run docker-compose commands.
As this plugin relies on the docker compose cli under the hood, it requires the docker compose cli to be installed
on the Kestra host machine.

## Motivations

This idea emerged as I was setting up a GitOps workflow for my docker-compose deployed projects.
I usually use Portainer when it comes to setting up docker compose based deployments.
But as the community edition lacks webhook features, which I really needed (e.g. to run smoke tests after redeploy),
I decided to put the extra effort into implementing a custom plugin and relies on the Kestra native ecosystem to
achieve desired results.

This plugin aims to provide a simple way to run docker-compose commands from within Kestra and connect their inputs /
outputs with previous / further flow steps.

## Design choices

As the Docker REST API does not expose compose capabilities, this plugin leverages a locally installed
[Docker Compose standalone binary](https://docs.docker.com/compose/install/standalone/) to interact with the Docker Engine.

Running this plugin requires access to the Docker Engine socket, which can be achieved by mounting the socket file
`/var/run/docker.sock` into the container running the plugin.
This plugin is not meant to be a full-fledged replacement for the docker-compose CLI, but rather a simple wrapper
around it to allow for easy integration with Kestra workflows.

## Running the project in local

### Prerequisites

- Java 21
- Docker

### Running tests

```
./gradlew check --parallel
```

### Launching the whole app

```
./gradlew shadowJar && docker build -t kestra-custom . && docker run --rm -p 8080:8080 -v /var/run/docker.sock:/var/run/docker.sock --user root kestra-custom server local
```

> [!NOTE]
> You need to relaunch this whole command everytime you make a change to your plugin

go to http://localhost:8080, your plugin will be available to use

## Documentation

* Full documentation can be found under: [kestra.io/docs](https://kestra.io/docs)
* Documentation for developing a plugin is included in
  the [Plugin Developer Guide](https://kestra.io/docs/plugin-developer-guide/)

## License

Apache 2.0 © [Kestra Technologies](https://kestra.io)

## Stay up to date

We release new versions every month. Give the [main repository](https://github.com/kestra-io/kestra) a star to stay up
to date with the latest releases and get notified about future updates.

![Star the repo](https://kestra.io/star.gif)
