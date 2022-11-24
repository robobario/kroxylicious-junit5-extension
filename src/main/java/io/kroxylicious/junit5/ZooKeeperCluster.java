/*
 * Copyright Kroxylicious Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.kroxylicious.junit5;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.kroxylicious.cluster.KafkaCluster;

/**
 * Annotation constraining a {@link KafkaClusterProvisioningStrategy} to use
 * a {@link KafkaCluster} that is ZooKeeper-based.
 */
@Target({ ElementType.PARAMETER, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
@KafkaClusterConstraint
public @interface ZooKeeperCluster {
}
