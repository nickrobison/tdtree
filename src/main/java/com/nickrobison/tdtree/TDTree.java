package com.nickrobison.tdtree;

import com.nickrobison.tuple.TupleSchema;
import com.nickrobison.tuple.codegen.TupleExpressionGenerator;
import org.apache.commons.math3.util.FastMath;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.nickrobison.tdtree.TDTreeHelpers.*;

/**
 * Created by nrobison on 2/9/17.
 */
public class TDTree<Value> {

    private static final Logger logger = LoggerFactory.getLogger(TDTree.class);
    private static final String EMPTY_LEAF_VALUE = "Leaf {} does not have {}@{}";
    static long maxValue;
    static final TupleSchema leafSchema = buildLeafSchema();

    private final int blockSize;
    private List<LeafNode<Value>> leafs = new ArrayList<>();
    private int maxDepth;
    private final LeafSchema rootTuple;
    private final AtomicLong cacheSize = new AtomicLong();

    static {
        TDTree.resetMaxValue();
    }

    /**
     * Resets TDTree max value to what it's supposed to be, this is handle the funky unit tests and should NOT be called during normal operation
     */
    static void resetMaxValue() {
        maxValue = Duration.between(LocalDate.of(0, 1, 1).atStartOfDay(),
                LocalDate.of(5000, 1, 1).atStartOfDay()).toMillis();
    }

    public TDTree(int blockSize) throws Exception {
        logger.debug("Creating TD-Tree index");
        this.blockSize = blockSize;
        this.maxDepth = 0;

//        Init the root node
        rootTuple = leafSchema.createTypedTuple(LeafSchema.class);
        rootTuple.start(0);
        rootTuple.end(maxValue);
        rootTuple.direction(7);
        leafs.add(new SplittableLeaf<>(1, rootTuple, this.blockSize));
    }


    public void insertValue(String objectID, long startTime, @NonNull Value value) {
        insertValue(objectID, startTime, maxValue, value);
    }


    public void insertValue(String objectID, long startTime, long endTime, @NonNull Value value) {
        insertValue(longHashCode(objectID), startTime, endTime, value);
    }

    private void insertValue(long objectID, long startTime, long endTime, @NonNull Value value) {
//        Verify that the start and end times don't over/under flow. This addresses TRESTLE-559.
        if (startTime < 0) {
            throw new IllegalArgumentException("Cache cannot handle negative temporal values");
        }
        if (endTime > maxValue) {
            throw new IllegalArgumentException("End temporal exceeds max value for cache");
        }

//        Find the leaf at maxDepth that would contain the objectID
        final int matchingLeaf = getMatchingLeaf(startTime, endTime);
//        Find the region in list with the most number of matching bits
//        Notice the l2/l1 reordering, otherwise it finds the leaf with the fewest number of matching bits, because why not?
        //                .sorted(comparator)
        final Optional<LeafNode<Value>> first = leafs
                .stream().min((l1, l2) -> Integer.compare(matchLength(l2.getID(), matchingLeaf), matchLength(l1.getID(), matchingLeaf)));

//        We can do this because it will always match on, at least, the root node
        final LeafNode<Value> firstLeaf = first.orElseThrow(RuntimeException::new);
        final LeafSplit split = firstLeaf.insert(objectID, startTime, endTime, value);
//        If we split, we need to add the new leafs to the tree, and remove the old ones
        if (split != null) {
            leafs.remove(firstLeaf);
            parseSplit(split);
        }
        this.cacheSize.incrementAndGet();
    }

    public @Nullable Value getValue(String objectID, long atTime) {

        final List<LeafNode<Value>> candidateLeafs = findCandidateLeafs(atTime);
        if (candidateLeafs.isEmpty()) {
            return null;
        }

//        Build the find expression
        final TupleExpressionGenerator.BooleanTupleExpression findExpression = LeafNode.buildFindExpression(objectID, atTime);
        for (LeafNode node : candidateLeafs) {
            //noinspection unchecked
            @Nullable final Value value = (Value) node.getValue(findExpression);
            if (value != null) {
                logger.trace("Returning value {} for {} @ {} from {}", value, objectID, atTime, node.getBinaryStringID());
                return value;
            }
            logger.trace(EMPTY_LEAF_VALUE, node.getBinaryStringID(), objectID, atTime);
        }
        return null;
    }

    public void deleteValue(String objectID, long atTime) {
        final List<LeafNode<Value>> candidateLeafs = findCandidateLeafs(atTime);
        if (candidateLeafs.isEmpty()) {
            return;
        }
        final TupleExpressionGenerator.BooleanTupleExpression expression = LeafNode.buildFindExpression(objectID, atTime);
        for (LeafNode<Value> node : candidateLeafs) {
            if (node.delete(expression)) {
                logger.trace("Deleted {}@{} from {}", objectID, atTime, node.getBinaryStringID());
                this.cacheSize.decrementAndGet();
                return;
            }
            logger.trace(EMPTY_LEAF_VALUE, node.getBinaryStringID(), objectID, atTime);
        }
    }

    public void deleteKeysWithValue(@NonNull Value value) {
        this.leafs
                .stream()
                .filter(leaf -> leaf.getRecordCount() > 0)
                .forEach(leaf -> {
                    final long deletedKeys = leaf.deleteKeysWithValue(value);
                    if (deletedKeys > 0) {
                        this.cacheSize.addAndGet(deletedKeys * -1);
                    }
                });
    }

    public void updateValue(String objectID, long atTime, @NonNull Value value) {
        final List<LeafNode<Value>> candidateLeafs = findCandidateLeafs(atTime);

        for (LeafNode<Value> node : candidateLeafs) {
            if (node.update(objectID, atTime, value)) {
                logger.trace("Updated {}@{} to {} from {}", objectID, atTime, value, node.getBinaryStringID());
                return;
            }
            logger.trace(EMPTY_LEAF_VALUE, node.getBinaryStringID(), objectID, atTime);
        }
    }


    public void replaceKeyValue(String objectID, long atTime, long startTime, long endTime, @NonNull Value value) {
        deleteValue(objectID, atTime);
        insertValue(objectID, startTime, endTime, value);
    }


    public void setKeyTemporals(String objectID, long atTime, long startTime) {
        setKeyTemporals(objectID, atTime, startTime, maxValue);
    }


    public void setKeyTemporals(String objectID, long atTime, long startTime, long endTime) {
        final @Nullable Value value = getValue(objectID, atTime);
        if (value != null) {
            deleteValue(objectID, atTime);
            insertValue(objectID, startTime, endTime, value);
        }
    }

    public void rebuildIndex() {
        logger.info("Rebuilding TD-Tree");
//        Dump the tree, create a new one, and reinsert all the values
        final Instant start = Instant.now();
        final List<LeafNode<Value>> copiedLeaves = new ArrayList<>(this.leafs);
        this.leafs = new ArrayList<>();
        this.leafs.add(new SplittableLeaf<>(1, rootTuple, this.blockSize));
        copiedLeaves
                .stream()
                .filter(leaf -> leaf.getRecordCount() > 0)
                .map(LeafNode::dumpLeaf)
                .forEach(values -> values.forEach((key, value) -> this.insertValue(key.objectID(),
                        key.start(),
                        key.end(),
                        value)));

        final Instant end = Instant.now();
        logger.info("Rebuilding index took {} ms", Duration.between(start, end).toMillis());
    }


    public void dropIndex() {
        this.leafs = new ArrayList<>();
        this.leafs.add(new SplittableLeaf<>(1, rootTuple, this.blockSize));
    }

    public double calculateFragmentation() {
        return this.leafs
                .stream()
                .filter(leaf -> leaf.getRecordCount() > 0)
                .mapToDouble(LeafNode::calculateFragmentation)
                .average()
                .orElse(0.0);
    }

    public List<LeafStatistics> getLeafStatistics() {
        logger.debug("Computing index leaf statistics");
//        For each leaf, calculate the triangle coordinates and return it
        return this.leafs
                .stream()
                .map(leaf -> new LeafStatistics(leaf.getID(),
                        leaf.getBinaryStringID(),
                        leaf.getLeafType(),
                        leaf.getLeafVerticies(),
                        leaf.leafMetadata.direction(),
                        leaf.getRecordCount()))
                .collect(Collectors.toList());
    }

    public long getIndexSize() {
        return cacheSize.get();
    }

    public long getMaxValue() {
        return maxValue;
    }

    int getLeafCount() {
        return this.leafs.size();
    }

    private List<LeafNode<Value>> findCandidateLeafs(long atTime) {
        List<LeafNode<Value>> candidateLeafs = new ArrayList<>();
        long[] rectApex = {atTime, atTime};
        int length = 1;
        final ArrayDeque<LeafNode<Value>> populatedLeafs = this.leafs.stream()
                .filter(leaf -> leaf.getRecordCount() > 0)
                .collect(Collectors.toCollection(ArrayDeque::new));
//        If we only have 1 leaf (such as when things split down into a single leaf), return it and try to match against it
//        We don't really need to go through all the
//        This is a really gross hack, and I'm pretty sure it doesn't actually work. But whatever.
        if (populatedLeafs.size() == 1) {
            candidateLeafs.add(populatedLeafs.pop());
            return candidateLeafs;
        }

        while (!populatedLeafs.isEmpty()) {
            final LeafNode<Value> first = populatedLeafs.pop();
            final int firstID = first.getID();
            int overlappingPrefix = firstID >> (getIDLength(firstID) - length);

            final TDTreeHelpers.TriangleApex triangleApex = calculateTriangleApex(overlappingPrefix, 0, 7, 0., maxValue);
            final int triangleDirection = calculateTriangleDirection(overlappingPrefix, 0, 7);
//            Filter the triangle results
            final int intersection = checkRectangleIntersection(triangleApex, triangleDirection, length, rectApex, maxValue);
//                If the triangle is fully contained within the rectangle, add all the leafs with the same prefix
            if (intersection == 1) {
                final int currentSize = populatedLeafs.size();
                for (int i = 0; i < currentSize; i++) {
                    final LeafNode<Value> next = populatedLeafs.pop();
                    if (idSimilarity(next.getID(), overlappingPrefix) == length) {
                        candidateLeafs.add(next);
                    } else {
                        populatedLeafs.add(next);
                    }
                }
                candidateLeafs.add(first);
                length = 1;
//                If it's fully disjoint from the rectangle, remove all sub-leafs
            } else if (intersection == -2) {
                final int currentSize = populatedLeafs.size();
                for (int i = 0; i < currentSize; i++) {
                    final LeafNode<Value> next = populatedLeafs.pop();
                    if ((idSimilarity(next.getID(), overlappingPrefix) != length)) {
                        populatedLeafs.add(next);
                    }
                }
                length = 1;
//                If R matches F, add it to the candidate list
            } else if (firstID == overlappingPrefix) {
                candidateLeafs.add(first);
                length = 1;
            } else {
                populatedLeafs.push(first);
                length++;
            }
        }

        return candidateLeafs;
    }

    /**
     * Recursively parse a {@link .LeafSplit} to add all the new leaves, with records to the directory
     *
     * @param split {@link LeafSplit}
     */
    @SuppressWarnings("unchecked")
    private void parseSplit(LeafSplit split) {
        if (split.higherSplit == null) {
//        Increment the max depth, if we need to
            if (this.maxDepth < getIDLength(split.higherLeaf.getID())) {
                this.maxDepth++;
            }
            if (!this.leafs.contains(split.higherLeaf)) {
                this.leafs.add(split.higherLeaf);
            }
        } else {
            parseSplit(split.higherSplit);
        }

        if (split.lowerSplit == null) {
//        Increment the max depth, if we need to
            if (this.maxDepth < getIDLength(split.lowerLeaf.getID())) {
                this.maxDepth++;
            }
            if (!this.leafs.contains(split.lowerLeaf)) {
                this.leafs.add(split.lowerLeaf);
            }
        } else {
            parseSplit(split.lowerSplit);
        }
    }

    private int getMatchingLeaf(long startTime, long endTime) {
        if (this.maxDepth == 0) {
            return 1;
        }
        return getMatchingLeaf(startTime, endTime, 1, 7, new TDTreeHelpers.TriangleApex(0, maxValue));
    }

    private int getMatchingLeaf(long startTime, long endTime, int leafID, int parentDirection, TDTreeHelpers.TriangleApex parentApex) {
        final int idLength = getIDLength(leafID);
        if (idLength > this.maxDepth) {
            return leafID;
        }
        final TDTreeHelpers.ChildDirection childDirection = TDTreeHelpers.calculateChildDirection(parentDirection);
        final TDTreeHelpers.TriangleApex childApex = TDTreeHelpers.calculateChildApex(idLength + 1, parentDirection, parentApex.start, parentApex.end);
//                Intersects with low child?
        if (TDTreeHelpers.checkPointIntersection(childApex, childDirection.lowerChild, idLength + 1, startTime, endTime)) {
            return getMatchingLeaf(startTime, endTime, leafID << 1, childDirection.lowerChild, childApex);
        }
        return getMatchingLeaf(startTime, endTime, (leafID << 1) | 1, childDirection.higherChild, childApex);
    }

    /**
     * Determines how many bits match between two numbers
     * Shifts the numbers so that they're the same length
     *
     * @param leafID  - LeafID to match
     * @param matchID - matchID to match LeafID against
     * @return - Number of common bits left->right
     */
    private static int idSimilarity(int leafID, int matchID) {
        if (leafID == 0 || matchID == 0) {
            return 0;
        }
        final int idLength = TDTreeHelpers.getIDLength(leafID);
        final int matchLength = TDTreeHelpers.getIDLength(matchID);
        if (matchLength > idLength) {
            return idLength - Integer.bitCount(leafID ^ (matchID >> (matchLength - idLength)));
        } else {
            return matchLength - Integer.bitCount(matchID ^ (leafID >> (idLength - matchLength)));
        }
    }

    /**
     * Moves left->right through a string representation of two binary numbers, and counts the number of bits in common, until they start to diverge
     *
     * @param leafID  - Leaf ID to match
     * @param matchID - Match ID to match leaf against
     * @return - number of bits in common, until they diverge
     */
    private static int matchLength(int leafID, int matchID) {
        final int minIDLength = FastMath.min(getIDLength(leafID), getIDLength(matchID));
        String leafString = Integer.toBinaryString(leafID);
        String matchString = Integer.toBinaryString(matchID);
        int match = 0;
        while ((match < minIDLength) && (leafString.charAt(match) == matchString.charAt(match))) {
            match++;
        }
        return match;
    }

    private static TupleSchema buildLeafSchema() {
        try {
            return TupleSchema
                    .builder()
                    .addField("start", Double.TYPE)
                    .addField("end", Double.TYPE)
                    .addField("direction", Integer.TYPE)
                    .implementInterface(LeafSchema.class)
                    .heapMemory()
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Unable to build leaf schema", e);
        }
    }

}
