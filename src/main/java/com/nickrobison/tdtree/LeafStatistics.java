package com.nickrobison.tdtree;

import java.io.Serializable;

public class LeafStatistics implements Serializable {
    public static final long serialVersionUID = 42L;

    private final int leafID;
    private final String binaryID;
    private final String type;
    private final double[] coordinates;
    private final int direction;
    private final int records;

    public LeafStatistics(int leafID, String binaryID, String type, double[] coordinates, int direction, int records) {
        this.leafID = leafID;
        this.binaryID = binaryID;
        this.type = type;
        this.coordinates = coordinates;
        this.direction = direction;
        this.records = records;
    }

    public int getLeafID() {
        return leafID;
    }

    public String getBinaryID() {
        return binaryID;
    }

    public String getType() {
        return type;
    }

    public double[] getCoordinates() {
        return coordinates;
    }

    public int getDirection() {
        return direction;
    }

    public int getRecords() {
        return records;
    }
}
