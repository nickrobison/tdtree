package com.nickrobison.tdtree;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Created by nrobison on 2/10/17.
 */
public class LeafSplit<Value> {

    final int leafID;
    final LeafNode<Value> lowerLeaf;
    final LeafNode<Value> higherLeaf;
    @Nullable
    LeafSplit<Value> lowerSplit;
    @Nullable
    LeafSplit<Value> higherSplit;

    LeafSplit(int leafID, LeafNode<Value> lowerLeaf, LeafNode<Value> higherLeaf) {
        this.leafID = leafID;
        this.lowerLeaf = lowerLeaf;
        this.higherLeaf = higherLeaf;
        this.lowerSplit = null;
        this.higherSplit = null;
    }
}
