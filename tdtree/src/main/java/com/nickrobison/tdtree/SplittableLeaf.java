package com.nickrobison.tdtree;

import com.nickrobison.tuple.FastTuple;
import com.nickrobison.tuple.codegen.TupleExpressionGenerator;
import org.apache.commons.lang3.ArrayUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static com.nickrobison.tdtree.TDTreeHelpers.getIDLength;

/**
 * Created by nrobison on 2/9/17.
 */
class SplittableLeaf<Value> extends LeafNode<Value> {
    private static final Logger logger = LoggerFactory.getLogger(SplittableLeaf.class);
    private final int blockSize;
    final @Nullable LeafKeySchema[] keys;
    final @Nullable Value[] values;
    private int records = 0;


    @SuppressWarnings({"method.invocation.invalid"})
    SplittableLeaf(int leafID, LeafSchema leafMetadata, int blockSize) {
        super(leafID, leafMetadata);
        this.blockSize = blockSize;
//            Allocate Key array
        try {
            keys = splittableKeySchema.createTypedTupleArray(LeafKeySchema.class, blockSize);
            //noinspection unchecked
            values = (Value[]) new Object[blockSize];
        } catch (Exception e) {
            throw new RuntimeException("Unable to allocate key/value memory for leaf", e);
        }
        logger.trace("Creating splittable leaf {}", this.getBinaryStringID());
    }

    @Override
    public int getRecordCount() {
        return this.records;
    }

    @Override
    public boolean isSplittable() {
        return true;
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
    @Nullable Value getValue(TupleExpressionGenerator.BooleanTupleExpression expression) {
        for (int i = 0; i < this.records; i++) {
            final LeafKeySchema key = keys[i];
            if (key != null && expression.evaluate((FastTuple) key)) {
                return values[i];
            }
        }
        return null;
    }

    @Override
    @Nullable
    LeafSplit insert(long objectID, long startTime, long endTime, @NonNull Value value) {
        return insert(buildObjectKey(objectID, startTime, endTime), value);
    }

    @Override
//    If a key is non-null, then the value is non-null
    @SuppressWarnings({"argument.type.incompatible"})
    @Nullable
    LeafSplit insert(LeafKeySchema newKey, @NonNull Value value) {
//            Check if we have more space, if we do, insert it.
        if (records < blockSize) {
            return insertValueIntoArray(newKey, value);
        } else {
//                If we don't have any more space, time to split
            final double parentStart = this.leafMetadata.start();
            final double parentEnd = this.leafMetadata.end();
            final int parentDirection = this.leafMetadata.direction();
            final int idLength = getIDLength(this.leafID);
            final TDTreeHelpers.TriangleApex childApex = TDTreeHelpers.calculateChildApex(idLength + 1,
                    parentDirection,
                    parentStart,
                    parentEnd);
            final LeafSchema lowerChild;
            final LeafSchema higherChild;
            final LeafNode<Value> lowerChildLeaf;
            final LeafNode<Value> higherChildLeaf;
            final TDTreeHelpers.ChildDirection childDirection = TDTreeHelpers.calculateChildDirection(parentDirection);
//            If one of the children is a point, pick the lower, turn it into a point and move on
//            We also need to make sure we don't recurse too far, so the length of a leafID can't be more than 30
            if (TDTreeHelpers.triangleIsPoint(TDTreeHelpers.getTriangleVerticies(TDTreeHelpers.adjustedLength[idLength + 1], childDirection.lowerChild, childApex.start, childApex.end)) ||
                    getIDLength(this.leafID) == (getIDLength(Integer.MAX_VALUE) - 1)) {
//                    Convert the leaf to a point leaf and replace the splittable node

                final PointLeaf<Object> pointLeaf = new PointLeaf<>(leafID, leafMetadata);
//                    Copy in all the keys and values
                pointLeaf.copyInitialValues(this.keys, this.values);
//                    Copy the new value
                pointLeaf.insert(newKey, value);

                this.records = 0;

                return new LeafSplit(leafID, pointLeaf, pointLeaf);
            } else {
//            Create the lower and higher leafs
                try {
                    lowerChild = TDTree.leafSchema.createTypedTuple(LeafSchema.class);
                    lowerChild.start(childApex.start);
                    lowerChild.end(childApex.end);
                    lowerChild.direction(childDirection.lowerChild);
                    higherChild = TDTree.leafSchema.createTypedTuple(LeafSchema.class);
                    higherChild.start(childApex.start);
                    higherChild.end(childApex.end);
                    higherChild.direction(childDirection.higherChild);
                } catch (Exception e) {
                    throw new RuntimeException("Unable to build tuples for child triangles", e);
                }

                lowerChildLeaf = new SplittableLeaf<>(leafID << 1, lowerChild, this.blockSize);
                higherChildLeaf = new SplittableLeaf<>((leafID << 1) | 1, higherChild, this.blockSize);
                logger.trace("Splitting {} into {} and {}", this.getBinaryStringID(), lowerChildLeaf.getBinaryStringID(), higherChildLeaf.getBinaryStringID());
            }
            final LeafSplit leafSplit = new LeafSplit(this.leafID, lowerChildLeaf, higherChildLeaf);
//            Divide values into children, by testing to see if they belong to the lower child
            final double[] lowerChildVerticies = TDTreeHelpers.getTriangleVerticies(TDTreeHelpers.adjustedLength[idLength + 1], childDirection.lowerChild, childApex.start, childApex.end);
            for (int i = 0; i < this.blockSize; i++) {
                LeafKeySchema key = keys[i];
                if (key != null) {
                    if (TDTreeHelpers.pointInTriangle(key.start(), key.end(), lowerChildVerticies)) {
                        logger.trace("Inserting {} into lower child", key);
                        final LeafSplit lowerChildSplit = lowerChildLeaf.insert(key, values[i]);
                        if (lowerChildSplit != null) {
                            leafSplit.lowerSplit = lowerChildSplit;
                        }
                    } else {
                        logger.trace("Inserting {} into higher child", key);
                        final LeafSplit higherChildSplit = higherChildLeaf.insert(key, values[i]);
                        if (higherChildSplit != null) {
                            leafSplit.higherSplit = higherChildSplit;
                        }
                    }
                }
            }
//            Don't forget about the new record we're trying to insert
            if (TDTreeHelpers.pointInTriangle(newKey.start(), newKey.end(), lowerChildVerticies)) {
                final LeafSplit lowerChildSplit = lowerChildLeaf.insert(newKey, value);
                if (lowerChildSplit != null) {
                    leafSplit.lowerSplit = lowerChildSplit;
                }
            } else {
                leafSplit.higherSplit = higherChildLeaf.insert(newKey, value);
            }
//            Zero out the records, so we know we've fully split everything
            this.records = 0;
            return leafSplit;
        }
    }

    @Override
    boolean delete(String objectID, long atTime) {
        final TupleExpressionGenerator.BooleanTupleExpression eval = buildFindExpression(objectID, atTime);
        return delete(eval);
    }

    @Override
    boolean delete(TupleExpressionGenerator.BooleanTupleExpression expression) {
        for (int i = 0; i < this.records; i++) {
            final LeafKeySchema key = keys[i];
            if (key != null && expression.evaluate((FastTuple) key)) {
                keys[i] = null;
                values[i] = null;
                return true;
//                    Do we need to collapse?
            }
        }
        return false;
    }

    @Override
    long deleteKeysWithValue(@NonNull Value value) {
        long deletedKeys = 0;
        for (int i = 0; i < this.records; i++) {
            if (value.equals(values[i])) {
                keys[i] = null;
                values[i] = null;
                deletedKeys++;
            }
        }
        return deletedKeys;
    }

    @Override
    boolean update(String objectID, long atTime, @NonNull Value value) {
        final TupleExpressionGenerator.BooleanTupleExpression eval = buildFindExpression(objectID, atTime);
        for (int i = 0; i < this.records; i++) {
            final LeafKeySchema key = keys[i];
            if (key != null && eval.evaluate((FastTuple) key)) {
                values[i] = value;
                return true;
            }
        }
        return false;
    }

    @Override
    Map<LeafKeySchema, @NonNull Value> dumpLeaf() {
        Map<LeafKeySchema, @NonNull Value> leafRecords = new HashMap<>();
        for (int i = 0; i < this.records; i++) {
            final LeafKeySchema key = keys[i];
            if (key != null && values[i] != null) {
                leafRecords.put(key, values[i]);
            }
        }
        return leafRecords;
    }

    @Override
    double calculateFragmentation() {
        double nullRecords = 0;
        for (int i = 0; i < this.records; i++) {
            if (keys[i] == null) {
                nullRecords++;
            }
        }
        return nullRecords / (double) this.records;
    }

    @SuppressWarnings({"argument.type.incompatible"})
    private @Nullable
    LeafSplit insertValueIntoArray(LeafKeySchema key, Value value) {
        if (!ArrayUtils.contains(keys, key)) {
            keys[records] = key;
            values[records] = value;
            records++;
        }
        return null;
    }

    @Override
    public String toString() {
        return "SplittableLeaf{" +
                "binaryID='" + binaryID + '\'' +
                ", records=" + records +
                ", start=" + Instant.ofEpochMilli(Double.valueOf(leafMetadata.start()).longValue()).atOffset(ZoneOffset.UTC) +
                ", end=" + Instant.ofEpochMilli(Double.valueOf(leafMetadata.end()).longValue()).atOffset(ZoneOffset.UTC) +
                ", direction=" + leafMetadata.direction() +
                '}';
    }
}
