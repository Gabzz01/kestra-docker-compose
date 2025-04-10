# Kestra Docker Compose Plugin

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
[Docker Compose standalone binary](https://docs.docker.com/compose/install/standalone/) to interact with the Docker
Engine.

Running this plugin requires access to the Docker Engine socket, which can be achieved by mounting the socket file
`/var/run/docker.sock` into the container running the plugin.
This plugin is not meant to be a full-fledged replacement for the docker-compose CLI, but rather a simple wrapper
around it to allow for easy integration with Kestra workflows.

## Examples

### Alerting

This examples shows how to use this plugin Trigger to implement a simple alerting system for a docker-compose
based project. A Slack notification is emitted when a container neither running nor healthy.

```yaml
id: alert-docker
namespace: fr.rtz.markeat.devops

tasks:
  - id: alert
    type: io.kestra.plugin.notifications.slack.SlackIncomingWebhook
    url: myWebhookUrl
    payload: |
      {
        "text": "Containers stopped : {{ containers | jq('.[] | select((.State != \"running\") and .State != \"healthy\")') }}"
      }
triggers:
  - id: poll-docker
    type: fr.rtz.kestra.docker.compose.Ps
    projectName: test
    outputCondition: "{{ containers | jq('.[] | select((.State != \"running\") and .State != \"healthy\")') | length > 0 }}"
```

### GitOps

This example shows how to use the plugin to implement a GitOps workflow for a docker-compose based project. The
workflows watches the docker-compose.yaml file for changes in the remote repository and triggers a redeploy when a
change is detected.

// TODO https://kestra.io/docs/expressions#example-with-indent-and-nindent
pas besoin de script custom

```yaml
id: gitops
namespace: company.team.devops

tasks:
  # List all images in the docker-compose.yaml file
  - id: list_images
    type: io.kestra.plugin.scripts.python.Script
    beforeCommands:
      - pip install pyyaml
    script: |
      from kestra import Kestra
      from yaml import safe_load
      raw = """
      {{ trigger.body }}
      """
      data = safe_load(raw)
      services = data.get("services", {})
      images = [s.get("image") for s in services.values() if "image" in s]
      Kestra.outputs({"images": images})
  # Pull all images with credentials in advance to limit down time.
  - id: 1_each
    type: io.kestra.plugin.core.flow.ForEach
    values: "{{ outputs.list_images.vars.images }}"
    tasks:
      - id: pull-img
        type: io.kestra.plugin.docker.Pull
        image: "{{taskrun.value}}"
        credentials:
          username: gabzz01
          password: "{{ secrets('GH_PAT') }}"
  - id: redeploy
    type: fr.rtz.kestra.docker.compose.Up
    stackDefinition: "{{ trigger.body }}"
  - id: notify
    type: io.kestra.plugin.notifications.slack.SlackIncomingWebhook
    payload: Successfully deployed to staging environment
    url: "{{ secrets('SLACK_WEBHOOK_URL') }}"

triggers:
  - id: poll-stack
    type: io.kestra.plugin.core.http.Trigger
    uri: https://raw.githubusercontent.com/RTZ-Developments/MarkEat/refs/heads/master/environments/staging/docker-compose.yaml
    headers:
      Accept: application/vnd.github.v3.raw
      Authorization: "token {{ secrets('GH_PAT') }}"
    responseCondition: "{{ response.body != kv('stack', errorOnMissing=false) }}"
    interval: PT30S
```

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
