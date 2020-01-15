# Spring Boot and Kubernetes (K8s) Integration

This repository contains sample code and instructions that would help you get started with Spring Boot based application integration with the following K8s features:

 * Readiness and liveness probes
 * Config Maps
 * Service Discovery - See branch `Service Discovery`

These samples use [Fabric8 Maven Plugin](https://github.com/fabric8io/fabric8-maven-plugin) and [Spring Cloud Kubernetes](https://github.com/spring-cloud/spring-cloud-kubernetes) to make this happen. It is not necessary for you to use the `Fabric8` plugin, however it just makes deploying a Spring Boot app to K8s much easier. Some of the cool features of the plugin is:

1. Consistent and easy K8s deployment descriptor generation for Java/Spring Boot apps
2. Spring Boot Actuator based Readiness and Liveness probes [K8s Probes](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-probes/)
3. Enabling Spring Boot features based on the presence of the dependency withing the `pom` file

## Build instructions

### Prerequisites:

* A local or remote K8s cluster 
* If using MiniKube make sure to execute `eval $(minikube docker-env)` so that the built image available as part of the MiniKube Docker daemon
* If not using MiniKube, make sure your image is available on DockerHub or an internal private docker registry

### Kubernetes PropertySource

The most common approach to configure your spring boot application is to edit the `application.yaml` file. Often the user may override properties by specifying system properties or env variables.

#### ConfigMap PropertySource
    
This feature works somewhat like Spring Cloud Config server, but without a Git or Vault based backend configuration store.

Kubernetes has the notion of [ConfigMap](http://kubernetes.io/docs/user-guide/configmap/) for passing configuration to the application.

The `ConfigMap` `PropertySource` when enabled will lookup Kubernetes for a `ConfigMap` named after the application (see `spring.application.name`). If the map is found it will read its data and do the following:

- apply individual configuration properties.
- apply as yaml the content of any property named `application.yaml`
- apply as properties file the content of any property named `application.properties`

Example:

```yaml
kind: ConfigMap
apiVersion: v1
metadata:
  name: spring-boot-k8s-recipes
data:
  application.yml: |-
    greeting:
      message: From default Spring profile
    ---
    spring:
      profiles: development
    greeting:
      message: From development Spring profile
    ---
    spring:
      profiles: kubernetes
    greeting:
      message: From kubernetes Spring profile
```    

#### Secrets PropertySource

Kubernetes has the notion of [Secrets](http://kubernetes.io/docs/user-guide/secrets/) for storing sensitive data such as password, OAuth tokens, etc.

The `Secrets` `PropertySource` when enabled will lookup Kubernetes for `Secrets` from the following sources:
1. reading recursively from secrets mounts
2. named after the application (see `spring.application.name`)
3. matching some labels

Please note that by default, consuming Secrets via API (points 2 and 3 above) **is not enabled**.

If the secrets are found theirs data is made available to the application.

**Example:**

```yaml
    apiVersion: v1
    kind: Secret
    metadata:
      name: activemq-secrets
      labels:
        broker: activemq
    type: Opaque
    data:
      amq.username: bXl1c2VyCg==
      amq.password: MWYyZDFlMmU2N2Rm
```

You can select the Secrets to consume in a number of ways:    

1. By listing the directories were secrets are mapped:
    ```
    -Dspring.cloud.kubernetes.secrets.paths=/etc/secrets/activemq,etc/secrets/postgres
    ```

    If you have all the secrets mapped to a common root, you can set them like:

    ```
    -Dspring.cloud.kubernetes.secrets.paths=/etc/secrets
    ```

2. By setting a named secret:
    ```
    -Dspring.cloud.kubernetes.secrets.name=postgres-secrets
    ```

3. By defining a list of labels:
    ```
    -Dspring.cloud.kubernetes.secrets.labels.broker=activemq
    -Dspring.cloud.kubernetes.secrets.labels.db=postgres
    ```

**Properties:**

| Name                                      | Type    | Default                    | Description
| ---                                       | ---     | ---                        | ---
| spring.cloud.kubernetes.secrets.enabled   | Boolean | true                       | Enable Secrets PropertySource
| spring.cloud.kubernetes.secrets.name      | String  | ${spring.application.name} | Sets the name of the secret to lookup
| spring.cloud.kubernetes.secrets.labels    | Map     | null                       | Sets the labels used to lookup secrets
| spring.cloud.kubernetes.secrets.paths     | List    | null                       | Sets the paths were secrets are mounted /example 1)
| spring.cloud.kubernetes.secrets.enableApi | Boolean | false                      | Enable/Disable consuming secrets via APIs (examples 2 and 3)

**Notes:**
- The property spring.cloud.kubernetes.secrets.labels behave as defined by [Map-based binding](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-Configuration-Binding#map-based-binding).
- The property spring.cloud.kubernetes.secrets.paths behave as defined by [Collection-based binding](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-Configuration-Binding#collection-based-binding).
- Access to secrets via API may be restricted for security reasons, the preferred way is to mount secret to the POD.

#### PropertySource Reload

Some applications may need to detect changes on external property sources and update their internal status to reflect the new configuration.
The reload feature of Spring Cloud Kubernetes is able to trigger an application reload when a related ConfigMap or Secret change.

This feature is disabled by default and can be enabled using the configuration property `spring.cloud.kubernetes.reload.enabled=true`
 (eg. in the *bootstrap.properties* file).

The following levels of reload are supported (property `spring.cloud.kubernetes.reload.strategy`):

- **refresh (default)**: only configuration beans annotated with `@ConfigurationProperties` or `@RefreshScope` are reloaded. 
This reload level leverages the refresh feature of Spring Cloud Context.
- **restart_context**: the whole Spring _ApplicationContext_ is gracefully restarted. Beans are recreated with the new configuration.
- **shutdown**: the Spring _ApplicationContext_ is shut down to activate a restart of the container.
 When using this level, make sure that the lifecycle of all non-daemon threads is bound to the ApplicationContext 
 and that a replication controller or replica set is configured to restart the pod.


The reload feature supports two operating modes:

- **event (default)**: watches for changes in config maps or secrets using the Kubernetes API (web socket). 
Any event will produce a re-check on the configuration and a reload in case of changes. 
The `view` role on the service account is required in order to listen for config map changes. A higher level role (eg. `edit`) is required for secrets 
(secrets are not monitored by default).
- **polling**: re-creates the configuration periodically from config maps and secrets to see if it has changed.
The polling period can be configured using the property `spring.cloud.kubernetes.reload.period` and defaults to *15 seconds*.
It requires the same role as the monitored property source. 
This means, for example, that using polling on file mounted secret sources does not require particular privileges.

Properties:

| Name                                                   | Type    | Default                    | Description
| ---                                                    | ---     | ---                        | ---
| spring.cloud.kubernetes.reload.enabled                 | Boolean | false                      | Enables monitoring of property sources and configuration reload
| spring.cloud.kubernetes.reload.monitoring-config-maps  | Boolean | true                       | Allow monitoring changes in config maps
| spring.cloud.kubernetes.reload.monitoring-secrets      | Boolean | false                      | Allow monitoring changes in secrets
| spring.cloud.kubernetes.reload.strategy                | Enum    | refresh                    | The strategy to use when firing a reload (*refresh*, *restart_context*, *shutdown*)
| spring.cloud.kubernetes.reload.mode                    | Enum    | event                      | Specifies how to listen for changes in property sources (*event*, *polling*)
| spring.cloud.kubernetes.reload.period                  | Long    | 15000                      | The period in milliseconds for verifying changes when using the *polling* strategy

Notes:
- Properties under *spring.cloud.kubernetes.reload.** should not be used in config maps or secrets: changing such properties at runtime may lead to unexpected results;
- Deleting a property or the whole config map does not restore the original state of the beans when using the *refresh* level.

### Service Discovery

The `ServiceDiscovery` feature allows you to query Kubernetes endpoints *(see [services](http://kubernetes.io/docs/user-guide/services/))* by name.
To get make this work, add the following dependency inside your project:

```xml
<dependency>
    <groupId>io.fabric8</groupId>
    <artifactId>spring-cloud-starter-kubernetes</artifactId>
    <version>${latest.version}</version>
</dependency>
```

Then you can inject the client in your cloud simply by:

```java
@Autowired
private DiscoveryClient discoveryClient;
```

If for any reason you need to disable the `DiscoveryClient` you can simply set the following property:

```
spring.cloud.kubernetes.discovery.enabled=false
```

### Pod Health Indicator

Spring Boot uses [HealthIndicator](https://github.com/spring-projects/spring-boot/blob/master/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/health/HealthIndicator.java) to expose info about the health of an application.
That makes it really useful for exposing health related information to the user and are also a good fit for use as [readiness probes](http://kubernetes.io/docs/user-guide/production-pods/#liveness-and-readiness-probes-aka-health-checks).

The Kubernetes health indicator which is part of the core modules exposes the following info:

- pod name
- visible services
- flag that indicates if app is internal or external to Kubernetes


### Kubernetes Profile Autoconfiguration

When the application is run inside Kubernetes a profile named `kubernetes` will automatically get activated.

This allows the user to customize the configuration that will be applied in and out of kubernetes *(e.g. different dev and prod configuration)*.

### Build and Deploy

* Clone this repo
* `mvn clean install -Pintegration`
* `mvn fabric8:deploy`
* `mvn fabric8:log`
   
The required K8s RBAC configuration is stored in `src/main/k8s`. This will be picked by the `fabric8` plugin and will be applied as part of the `deploy` task. That is, a new `ServiceAccount` with the required permissions would be created.
If you already have a provisioned `ServiceAccount`, just update the `deployment.yml` located in `src/main/fabric8` to reflect the name of the service account and delete `sa.yml` and `rb.yml`

### Reference Documentation

For further reference, please consider the following sections:

* [Fabric8 Maven Plugin](https://github.com/fabric8io/fabric8-maven-plugin)
* [Spring Cloud Kubernetes](https://github.com/spring-cloud/spring-cloud-kubernetes)
* [k8s RBAC Authorization](https://kubernetes.io/docs/reference/access-authn-authz/rbac/)

