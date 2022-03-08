# quarkus-operator-micrservice-database

This repo contains a relative simple Kubernetes operator to deploy a backend application to a Kubernetes cluster. The ecommerce sample application contains one microservice and a managed database in the cloud.

Technically the project uses the [Operator SDK](https://sdk.operatorframework.io/) and the [Java Operator SDK](https://javaoperatorsdk.io/).

## Documentation

There isn't much documentation yet, but I've planned to blog about different parts over the next days.

* [Developing and Debugging Kubernetes Operators in Java](http://heidloff.net/article/developing-debugging-kubernetes-operators-java/)
* [Accessing Kubernetes Resources from Java Operators](http://heidloff.net/article/accessing-kubernetes-resources-from-java-operators/)
* [Leveraging third party Operators in Kubernetes Operators](http://heidloff.net/article/leveraging-third-party-operators-in-kubernetes-operators/)
* [Creating Database Schemas in Kubernetes Operators](http://heidloff.net/article/creating-database-schemas-kubernetes-operators/)


## Setup

*Prereqs*

* Kubernetes (e.g. IBM Kubernetes Service)
* Operator SDK
* Java Operator SDK
* IBM Cloud Operator
* IBM API key

*Steps for IBM Cloud*

```
$ export IBMCLOUD_API_KEY="..."
$ curl -sL https://raw.githubusercontent.com/IBM/cloud-operators/master/hack/configure-operator.sh | bash -s -- install
$ curl -sL https://raw.githubusercontent.com/IBM/cloud-operators/master/hack/configure-operator.sh | bash -s -- install
$ kubectl create namespace tenant1
$ mvn clean quarkus:dev
$ kubectl apply -f kubernetes/ecommercesample.yaml
```
