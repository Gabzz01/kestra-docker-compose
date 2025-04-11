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
based project. A Slack notification is emitted every minute when a container is neither running nor healthy.

```yaml
id: alert-docker
namespace: company.team.devops

tasks:
  # Prebuild msg to avoid struggling with quotes escaping etc ...
  - id: build_msg
    type: "io.kestra.plugin.core.output.OutputValues"
    values:
      msg: ":warning: Containers stopped : {{ trigger.containers | jq('[.[] | select((.State != \"running\") and .State != \"healthy\") | .Name] | join(\", \")') }}"
  - id: alert
    type: io.kestra.plugin.notifications.slack.SlackIncomingWebhook
    url: "{{ kv('SLACK_WEBHOOK_URL') }}"
    payload: "{{ {'text': outputs['build_msg'].values.msg } | toJson }}"
triggers:
  - id: poll_docker
    type: fr.rtz.kestra.docker.compose.Ps
    projectName: my-compose-project
    outputCondition: "{{ containers | jq('.[] | select((.State != \"running\") and .State != \"healthy\")') | length > 0 }}"
```

### GitOps

This example shows how to use the plugin to implement a GitOps workflow for a docker-compose based project. The
workflows watches the docker-compose.yaml file for changes in the remote repository and triggers a redeployment when a
change is detected.

```yaml
id: gitops
namespace: company.team.devops

tasks:
  # Concurrently pull all images with credentials in advance to limit downtime.
  - id: list-img
    type: io.kestra.plugin.core.flow.ForEach
    concurrencyLimit: 0
    values: "{{ yaml(trigger.body).services | values }}"
    tasks:
      - id: pull-img
        type: io.kestra.plugin.docker.Pull
        image: "{{ yaml(taskrun.value).image }}"
        credentials:
          auth: "{{ '{{ kv('GH_USR') }}: {{ kv('GH_TOKEN') }}' | base64encode }}"
    # Deploy updated stack definition
  - id: redeploy
    projectName: my-compose-project
    type: fr.rtz.kestra.docker.compose.Up
    detached: true
    # wait: true
    yaml: "{{ trigger.body }}"
    # Update stack definition in keyvault
  - id: update_kv
    type: io.kestra.plugin.core.kv.Set
    key: stack
    kvType: STRING
    overwrite: true
    value: "{{ trigger.body }}"
    # Notify via Slack
  - id: notify
    type: io.kestra.plugin.notifications.slack.SlackIncomingWebhook
    payload: "{{ {'text': ':rocket: Successfully deployed to staging environment' } | toJson }}"
    url: "{{ kv('SLACK_WEBHOOK_URL') }}"

triggers:
  - id: poll-stack
    type: io.kestra.plugin.core.http.Trigger
    uri: "https://raw.githubusercontent.com/{{ kv('RAW_STACK_URL') }}"
    headers:
      Accept: application/vnd.github.v3.raw
      Authorization: "token {{ kv('GH_TOKEN') }}"
    responseCondition: "{{ response.body != kv('stack', errorOnMissing=false) }}"
    interval: PT30S
```

## Running the project locally

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
