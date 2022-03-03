package com.ecommercesample;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.Version;

@Version("v1alpha1")
@Group("ecommercesample.com")
@Kind("ECommerceSample")
@Plural("ecommercesamples")
public class ECommerceSample extends CustomResource<ECommerceSampleSpec, ECommerceSampleStatus> implements Namespaced {}

