package com.ecommercesample;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Map;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonReader;
import javax.json.JsonObject;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.javaoperatorsdk.operator.api.*;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;

@Controller
public class ECommerceSampleController implements ResourceController<ECommerceSample> {

    private final KubernetesClient client;

    public ECommerceSampleController(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public void init(EventSourceManager eventSourceManager) {
    }

    @Override
    public UpdateControl<ECommerceSample> createOrUpdateResource(ECommerceSample resource, Context<ECommerceSample> context) {
        System.out.println("ECommerceSampleController.createOrUpdateResource invoked");        

        // Cache not initialized yet (?)
        if (resource.getSpec().getAppTitle() == null) {
            return UpdateControl.updateCustomResource(resource);
        }
        else {
            System.out.println("App title: " + resource.getSpec().getAppTitle());
        }
        if (resource.getSpec().getSqlUrl() == null) {
            return UpdateControl.updateCustomResource(resource);
        }
        else {
            System.out.println("SQL URL: " + resource.getSpec().getSqlUrl());
        }

        String resourceName = resource.getMetadata().getName();
        System.out.println("Resource name: " + resourceName);
        String namespace = resource.getMetadata().getNamespace();
        System.out.println("Namespace: " + namespace);
        
        // No appTitle defined -> Invalid resource
        // This path is never reached. Is there a way to check whether a variable exists?
        /*
        if (resource.getSpec().getAppTitle().isEmpty()) {
            System.err.println("Fatal error. appTitle not defined");
            return UpdateControl.noUpdate(); // or rather UpdateControl.updateCustomResourceAndStatus(resource); ?
        }
        */

        Secret secret;
        String ibmOperatorBindingSecretName = resourceName + "-secret";
        try {
            String SERVICE_PART1 = "{\"apiVersion\": \"ibmcloud.ibm.com/v1\",\"kind\": \"Service\"," + "\"metadata\": {\"namespace\":\"";
            String SERVICE_PART2 = "\", \"name\": \"" + resourceName + "\"},\"spec\": {\"plan\": \"standard\",\"serviceClass\": \"databases-for-postgresql\"}}";
            String postgresServiceJson = SERVICE_PART1 + namespace + SERVICE_PART2;
            CustomResourceDefinitionContext customResourceDefinitionContext = new CustomResourceDefinitionContext.Builder()
                .withGroup("ibmcloud.ibm.com")
                .withName("services.ibmcloud.ibm.com")
                .withScope("Namespaced") 
                .withPlural("services")   
                .withVersion("v1")
                .build(); 
            GenericKubernetesResource postgresServiceResource = 
                client.genericKubernetesResources(customResourceDefinitionContext)
                .inNamespace(namespace).withName(resourceName).get();
            if (postgresServiceResource == null) {
                System.out.println("Creating Postgres Service ...");
                client.genericKubernetesResources(customResourceDefinitionContext).inNamespace(namespace)
                    .createOrReplace(Serialization.unmarshal(postgresServiceJson, GenericKubernetesResource.class));
            } 

            String BINDING_PART1 = "{\"apiVersion\": \"ibmcloud.ibm.com/v1\",\"kind\": \"Binding\",\"metadata\": {\"namespace\":\"";
            String BINDING_PART2 = "\", \"name\": \"binding-" + resourceName + "\"},\"spec\": {\"serviceName\": \"" + resourceName + "\",\"secretName\": \"" + ibmOperatorBindingSecretName + "\"}}";
            String postgresBindingJson = BINDING_PART1 + namespace + BINDING_PART2;
            customResourceDefinitionContext = new CustomResourceDefinitionContext.Builder()
                .withGroup("ibmcloud.ibm.com")
                .withName("bindings.ibmcloud.ibm.com")
                .withScope("Namespaced") 
                .withPlural("bindings")   
                .withVersion("v1")
                .build();
            GenericKubernetesResource postgresBindingResource = 
                client.genericKubernetesResources(customResourceDefinitionContext)
                .inNamespace(namespace).withName("binding-" + resourceName).get();
            if (postgresBindingResource == null) {
                System.out.println("Creating Postgres Binding ...");
                client.genericKubernetesResources(customResourceDefinitionContext).inNamespace(namespace)
                    .createOrReplace(Serialization.unmarshal(postgresBindingJson, GenericKubernetesResource.class));
            } 
          
            secret = client.secrets().inNamespace(namespace).withName(ibmOperatorBindingSecretName).get();        
            if (secret != null) {
                System.out.println("Secret " + ibmOperatorBindingSecretName + " exists");
            }
            else { 
                System.out.println("Secret " + ibmOperatorBindingSecretName + " does not exist");
                return UpdateControl.updateCustomResource(resource);
            }
        } catch (Exception e) {
            return UpdateControl.updateCustomResource(resource);
        }   

        String postgresUserName = null;
        String postgresPassword = null;
        String postgresCertificateData = null;
        String postgresUrl = null;
        try {  
            Map<String, String> postgresStringDataMap = secret.getData();
            String postgresConnectionJSON = postgresStringDataMap.get("connection");
            byte[] decodedBytes = Base64.getDecoder().decode(postgresConnectionJSON);
            postgresConnectionJSON = new String(decodedBytes);

            JsonReader jsonReader = Json.createReader(new StringReader(postgresConnectionJSON));
            JsonObject parentObject = jsonReader.readObject();
            JsonObject postgresObject = parentObject.getJsonObject("postgres");
            JsonArray hostsObject = postgresObject.getJsonArray("hosts");
            JsonObject authenticationObject = postgresObject.getJsonObject("authentication");
            JsonObject certificateObject = postgresObject.getJsonObject("certificate");
            postgresUserName = authenticationObject.getString("username");
            postgresPassword = authenticationObject.getString("password");
            postgresCertificateData = certificateObject.getString("certificate_base64");
            decodedBytes = Base64.getDecoder().decode(postgresCertificateData);
            postgresCertificateData = new String(decodedBytes);
            String postgresHostname = hostsObject.getJsonObject(0).getString("hostname");
            int postgresPort = hostsObject.getJsonObject(0).getInt("port");
            String postgresDatabase = postgresObject.getString("database");            
            postgresUrl = "jdbc:postgresql://" + postgresHostname + ":" + postgresPort + "/" + postgresDatabase + "?sslmode=verify-full&sslrootcert=/cloud-postgres-cert";
            
            if ((postgresCertificateData == null) || (postgresCertificateData.isEmpty()) ||
                (postgresUserName == null) || (postgresUserName.isEmpty()) ||
                (postgresPassword == null) || (postgresPassword.isEmpty()) ||
                (postgresUrl == null) || (postgresUrl.isEmpty())) {
            return UpdateControl.updateCustomResource(resource);
        }
        } catch (Exception e) {
            return UpdateControl.updateCustomResource(resource);
        }
        
        try {
            String secretNamePostgresUserName = "postgres.username";
            secret = client.secrets().inNamespace(namespace).withName(secretNamePostgresUserName).get();        
            if (secret == null) {
                Secret secretPostgresUserName = new SecretBuilder()
                    .withNewMetadata().withName(secretNamePostgresUserName).endMetadata()
                    .addToStringData("POSTGRES_USERNAME", postgresUserName)
                    .build();
                client.secrets().inNamespace(namespace).create(secretPostgresUserName);
            }
            
            String secretNamePostgresPassword = "postgres.password";
            secret = client.secrets().inNamespace(namespace).withName(secretNamePostgresPassword).get();        
            if (secret == null) {
                Secret secretPostgresUserPassword = new SecretBuilder()
                    .withNewMetadata().withName(secretNamePostgresPassword).endMetadata()
                    .addToStringData("POSTGRES_PASSWORD", postgresPassword)
                    .build();
                client.secrets().inNamespace(namespace).create(secretPostgresUserPassword);
            }
            
            String secretNamePostgresUrl = "postgres.url";
            secret = client.secrets().inNamespace(namespace).withName(secretNamePostgresUrl).get();        
            if (secret == null) {
                Secret secretPostgresUrl = new SecretBuilder()
                    .withNewMetadata().withName(secretNamePostgresUrl).endMetadata()
                    .addToStringData("POSTGRES_URL", postgresUrl)
                    .build();
                client.secrets().inNamespace(namespace).create(secretPostgresUrl);
            }
            
            String secretNamePostgresCertificate = "postgres.certificate-data";
            secret = client.secrets().inNamespace(namespace).withName(secretNamePostgresCertificate).get();        
            if (secret == null) {
                Secret secretPostgresCertificate = new SecretBuilder()
                    .withNewMetadata().withName(secretNamePostgresCertificate).endMetadata()
                    .addToStringData("POSTGRES_CERTIFICATE_DATA", postgresCertificateData)
                    .build();
                client.secrets().inNamespace(namespace).create(secretPostgresCertificate);
            }
            
            // to be done
            String hardcoded = "https://eu-de.appid.cloud.ibm.com/oauth/v4/e1b4e68e-f1ea-44b2-b8f3-eed95fa21c13";
            String secretNameAppIdUrl = "appid.oauthserverurl";
            secret = client.secrets().inNamespace(namespace).withName(secretNameAppIdUrl).get();        
            if (secret == null) {
                Secret secretAppIdUrl = new SecretBuilder()
                    .withNewMetadata().withName(secretNameAppIdUrl).endMetadata()
                    .addToStringData("APPID_AUTH_SERVER_URL", hardcoded)
                    .build();
                client.secrets().inNamespace(namespace).create(secretAppIdUrl);
            }
            
            // to be done
            hardcoded = "b12a05c3-8164-45d9-a1b8-af1dedf8ccc3";
            String secretNameAppIdClientId = "appid.client-id-catalog-service";
            secret = client.secrets().inNamespace(namespace).withName(secretNameAppIdClientId).get();        
            if (secret == null) {
                Secret secretAppIdClientId = new SecretBuilder()
                    .withNewMetadata().withName(secretNameAppIdClientId).endMetadata()
                    .addToStringData("APPID_CLIENT_ID", hardcoded)
                    .build();
                client.secrets().inNamespace(namespace).create(secretAppIdClientId);
            }
        } catch (Exception e) {
            return UpdateControl.updateCustomResource(resource);
        }
        
        try {
            String deploymentName = resourceName + "-deployment";
            Deployment existingDeployment = client
                .apps()
                .deployments()
                .inNamespace(namespace)
                .withName(deploymentName)
                .get();
            if (existingDeployment == null) {
                System.out.println("Deployment " + deploymentName + " does not exist");

                Deployment deployment = new DeploymentBuilder()
                    .withNewMetadata()
                        .withName("service-backend")
                        .withNamespace(namespace)
                        .addToLabels("test", "deployment")
                    .endMetadata()
                    .withNewSpec()
                        .withReplicas(1)
                        .withNewSelector()
                            .addToMatchLabels("app","service-backend")
                        .endSelector()
                        .withNewTemplate()
                            .withNewMetadata()
                                .addToLabels("app", "service-backend")
                            .endMetadata()
                            .withNewSpec()
                                .addNewContainer()
                                    .withName("service-backend")
                                    .withImage("quay.io/nheidloff/service-catalog:latest")
                                    .addNewEnv()
                                        .withName("POSTGRES_USERNAME")
                                        .withNewValueFrom()
                                            .withNewSecretKeyRef()
                                                .withName("postgres.username")
                                                .withKey("POSTGRES_USERNAME")
                                            .endSecretKeyRef()
                                        .endValueFrom()
                                    .endEnv()
                                    .addNewEnv()
                                        .withName("POSTGRES_CERTIFICATE_DATA")
                                        .withNewValueFrom()
                                            .withNewSecretKeyRef()
                                                .withName("postgres.certificate-data")
                                                .withKey("POSTGRES_CERTIFICATE_DATA")
                                            .endSecretKeyRef()
                                        .endValueFrom()
                                    .endEnv()
                                    .addNewEnv()
                                        .withName("POSTGRES_PASSWORD")
                                        .withNewValueFrom()
                                            .withNewSecretKeyRef()
                                                .withName("postgres.password")
                                                .withKey("POSTGRES_PASSWORD")
                                            .endSecretKeyRef()
                                        .endValueFrom()
                                    .endEnv()
                                    .addNewEnv()
                                        .withName("POSTGRES_URL")
                                        .withNewValueFrom()
                                            .withNewSecretKeyRef()
                                                .withName("postgres.url")
                                                .withKey("POSTGRES_URL")
                                            .endSecretKeyRef()
                                        .endValueFrom()
                                    .endEnv()
                                    .addNewEnv()
                                        .withName("APPID_AUTH_SERVER_URL")
                                        .withNewValueFrom()
                                            .withNewSecretKeyRef()
                                                .withName("appid.oauthserverurl")
                                                .withKey("APPID_AUTH_SERVER_URL")
                                            .endSecretKeyRef()
                                        .endValueFrom()
                                    .endEnv()
                                    .addNewEnv()
                                        .withName("APPID_CLIENT_ID")
                                        .withNewValueFrom()
                                            .withNewSecretKeyRef()
                                                .withName("appid.client-id-catalog-service")
                                                .withKey("APPID_CLIENT_ID")
                                            .endSecretKeyRef()
                                        .endValueFrom()
                                    .endEnv()
                                    .addNewPort()
                                        .withContainerPort(8081)
                                    .endPort()
                                    .withNewLivenessProbe()
                                        .withNewExec()
                                            .addToCommand(0, "sh")
                                            .addToCommand(1, "-c")
                                            .addToCommand(2, "curl -s http://localhost:8081/q/health/live")
                                        .endExec()
                                        .withInitialDelaySeconds(20)
                                    .endLivenessProbe()
                                    .withNewReadinessProbe()
                                        .withNewExec()
                                            .addToCommand(0, "sh")
                                            .addToCommand(1, "-c")
                                            .addToCommand(2, "curl -s http://localhost:8081/q/health/ready")
                                        .endExec()
                                        .withInitialDelaySeconds(40)
                                    .endReadinessProbe()
                                .endContainer()
                                .withRestartPolicy("Always")
                            .endSpec()
                        .endTemplate()
                    .endSpec()
                .build();
             
                client.apps().deployments().inNamespace(namespace).create(deployment);
            }
            else {
                System.out.println("Deployment " + deploymentName + " exists");
            }
        } catch (Exception e) {
            return UpdateControl.updateCustomResource(resource);
        }

        return UpdateControl.noUpdate();
    }
}

