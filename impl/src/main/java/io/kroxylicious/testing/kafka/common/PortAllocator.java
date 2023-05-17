/*
 * Copyright Kroxylicious Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.kroxylicious.testing.kafka.common;

import java.net.ServerSocket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.kroxylicious.testing.kafka.common.KafkaClusterConfig.KafkaEndpoints.Listener;

public class PortAllocator {

    /**
     * Tracks ports that are in-use by listener.
     * The inner map is a mapping of kafka <code>node.id</code> to a closed server socket, with a port number
     * previously defined from the ephemeral range.
     * Protected by lock of {@link PortAllocator itself.}
     */
    private final Map<Listener, Map<Integer, ServerSocket>> ports;

    public PortAllocator() {
        ports = new HashMap<>();
    }

    public synchronized int getPort(Listener listener, int nodeId) {
        return ports.get(listener).get(nodeId).getLocalPort();
    }

    public synchronized void allocate(Set<Listener> listeners, int brokerId) {
        allocate(listeners, brokerId, brokerId + 1);
    }

    public synchronized void allocate(Set<Listener> listeners, int firstBrokerIdInclusive, int lastBrokerIdExclusive) {
        if (lastBrokerIdExclusive <= firstBrokerIdInclusive) {
            throw new IllegalArgumentException(
                    "attempted to allocate ports to an invalid range of broker ids: [" + firstBrokerIdInclusive + "," + lastBrokerIdExclusive + ")");
        }
        int toAllocate = lastBrokerIdExclusive - firstBrokerIdInclusive;
        try (var preallocator = new ListeningSocketPreallocator()) {
            for (Listener listener : listeners) {
                List<ServerSocket> sockets = preallocator.preAllocateListeningSockets(toAllocate);
                Map<Integer, ServerSocket> listenerPorts = ports.computeIfAbsent(listener, listener1 -> new HashMap<>());
                for (int i = 0; i < toAllocate; i++) {
                    listenerPorts.put(firstBrokerIdInclusive + i, sockets.get(i));
                }
            }
        }
    }

    public synchronized boolean containsPort(Listener listener, int nodeId) {
        Map<Integer, ServerSocket> portsMap = ports.get(listener);
        return portsMap != null && portsMap.containsKey(nodeId);
    }

    public synchronized void deallocate(int nodeId) {
        for (var value : ports.values()) {
            value.remove(nodeId);
        }
    }
}
