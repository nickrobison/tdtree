package com.nickrobison.tdtree;

import com.nickrobison.tuple.FastTuple;
import com.nickrobison.tuple.codegen.TupleExpressionGenerator;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Created by nrobison on 2/13/17.
 */
public class PointLeaf<Value> extends LeafNode<Value> {
    private static final Logger logger = LoggerFactory.getLogger(PointLeaf.class);
    private final Map<LeafKeySchema, Value> values = new Object2ObjectOpenHashMap<>(100, .7f);
    private int records = 0;

    @SuppressWarnings({"method.invocation.invalid"})
    PointLeaf(int leafID, LeafSchema leafMetadata) {
        super(leafID, leafMetadata);
        logger.trace("Creating Point Node {}", this.getBinaryStringID());
    }

    @SafeVarargs
    final void copyInitialValues(@Nullable LeafKeySchema[] keys, @Nullable Value... vals) {
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] != null && vals[i] != null) {
                this.values.put(keys[i], vals[i]);
                this.records++;
            }
        }
    }

    @Override
    public int getRecordCount() {
        return this.records;
    }

    @Override
    public boolean isSplittable() {
        return false;
    }

    @Override
    public String getLeafType() {
        return this.getClass().getSimpleName();
    }

    @Override
    @Nullable Value getValue(String objectID, long atTime) {
        final TupleExpressionGenerator.BooleanTupleExpression eval = buildFindExpression(objectID, atTime);
        return getValue(eval);
    }

    @Override
    @SuppressWarnings({"type.argument.incompatible", "assignment.type.incompatible"})
    @Nullable Value getValue(TupleExpressionGenerator.BooleanTupleExpression expression) {
        final Optional<Value> value = this.values.entrySet()
                .stream()
                .filter(entry -> expression.evaluate((FastTuple) entry.getKey()))
                .map(Map.Entry::getValue)
                .findAny();
        return value.orElse(null);
    }

    @Override
    @Nullable LeafSplit insert(long objectID, long startTime, long endTime, @NonNull Value value) {
        return insert(buildObjectKey(objectID, startTime, endTime), value);
    }

    @Override
    @Nullable LeafSplit insert(LeafKeySchema newKey, @NonNull Value value) {
        if (!this.values.containsKey(newKey)) {
            this.values.put(newKey, value);
            this.records++;
        }
        return null;
    }

    @Override
    boolean delete(String objectID, long atTime) {
        final TupleExpressionGenerator.BooleanTupleExpression eval = buildFindExpression(objectID, atTime);
        return delete(eval);
    }

    @Override
    boolean delete(TupleExpressionGenerator.BooleanTupleExpression expression) {
        final Optional<LeafKeySchema> matchingKey = this.values.entrySet()
                .stream()
                .filter(entry -> expression.evaluate((FastTuple) entry.getKey()))
                .map(Map.Entry::getKey)
                .findAny();
        if (matchingKey.isPresent()) {
            this.values.remove(matchingKey.get());
            this.records--;
            return true;
        }
        return false;
    }

    @Override
    long deleteKeysWithValue(@NonNull Value value) {
        final List<LeafKeySchema> list = this.values.entrySet()
                .stream()
                .filter(entry -> value.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        list.forEach(this.values::remove);
        return list.size();
    }

    @Override
    boolean update(String objectID, long atTime, @NonNull Value value) {
        final LeafKeySchema key = buildObjectKey(objectID, atTime, atTime);
        if (this.values.containsKey(key)) {
            this.values.replace(key, value);
            return true;
        }
        return false;
    }
    @Override
    Map<LeafKeySchema, @NonNull Value> dumpLeaf() {
        return new HashMap<>(this.values);
    }

    @Override
    double calculateFragmentation() {
        return 0;
    }

    @Override
    public String toString() {
        return "PointLeaf{" +
                "binaryID='" + binaryID + '\'' +
                ", records=" + records +
                ", start=" + Instant.ofEpochMilli(Double.valueOf(leafMetadata.start()).longValue()).atOffset(ZoneOffset.UTC) +
                ", end=" + Instant.ofEpochMilli(Double.valueOf(leafMetadata.end()).longValue()).atOffset(ZoneOffset.UTC) +
                ", direction=" + leafMetadata.direction() +
                '}';
    }
}
