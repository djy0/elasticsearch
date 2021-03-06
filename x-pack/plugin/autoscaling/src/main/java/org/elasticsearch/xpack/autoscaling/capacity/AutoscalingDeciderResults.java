/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.autoscaling.capacity;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Represents a collection of individual autoscaling decider results that can be aggregated into a single autoscaling capacity for a
 * policy
 */
public class AutoscalingDeciderResults implements ToXContent, Writeable {

    private final AutoscalingCapacity currentCapacity;
    private final SortedMap<String, AutoscalingDeciderResult> results;

    /**
     * Return map of results, keyed by decider name.
     */
    public Map<String, AutoscalingDeciderResult> results() {
        return results;
    }

    public AutoscalingDeciderResults(final AutoscalingCapacity currentCapacity, final SortedMap<String, AutoscalingDeciderResult> results) {
        Objects.requireNonNull(currentCapacity);
        this.currentCapacity = currentCapacity;
        Objects.requireNonNull(results);
        if (results.isEmpty()) {
            throw new IllegalArgumentException("results can not be empty");
        }
        this.results = results;
    }

    public AutoscalingDeciderResults(final StreamInput in) throws IOException {
        this.currentCapacity = new AutoscalingCapacity(in);
        this.results = new TreeMap<>(in.readMap(StreamInput::readString, AutoscalingDeciderResult::new));
    }

    @Override
    public void writeTo(final StreamOutput out) throws IOException {
        currentCapacity.writeTo(out);
        out.writeMap(results, StreamOutput::writeString, (output, result) -> result.writeTo(output));
    }

    @Override
    public XContentBuilder toXContent(final XContentBuilder builder, final Params params) throws IOException {
        builder.startObject();
        AutoscalingCapacity requiredCapacity = requiredCapacity();
        if (requiredCapacity != null) {
            builder.field("required_capacity", requiredCapacity);
        }
        builder.field("current_capacity", currentCapacity);
        builder.startObject("deciders");
        for (Map.Entry<String, AutoscalingDeciderResult> entry : results.entrySet()) {
            builder.startObject(entry.getKey());
            entry.getValue().toXContent(builder, params);
            builder.endObject();
        }
        builder.endObject();
        builder.endObject();
        return builder;
    }

    public AutoscalingCapacity requiredCapacity() {
        if (results.values().stream().map(AutoscalingDeciderResult::requiredCapacity).anyMatch(Objects::isNull)) {
            // any undetermined decider cancels out all required capacities
            return null;
        }
        Optional<AutoscalingCapacity> result = results.values()
            .stream()
            .map(AutoscalingDeciderResult::requiredCapacity)
            .reduce(AutoscalingCapacity::upperBound);
        assert result.isPresent();
        return result.get();
    }

    public AutoscalingCapacity currentCapacity() {
        return currentCapacity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AutoscalingDeciderResults that = (AutoscalingDeciderResults) o;
        return currentCapacity.equals(that.currentCapacity) && results.equals(that.results);
    }

    @Override
    public int hashCode() {
        return Objects.hash(currentCapacity, results);
    }
}
