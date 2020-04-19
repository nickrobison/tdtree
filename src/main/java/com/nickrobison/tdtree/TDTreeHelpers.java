package com.nickrobison.tdtree;

import it.unimi.dsi.fastutil.doubles.Double2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Precision;

import java.util.Map;

/**
 * Created by nrobison on 2/10/17.
 */
@SuppressWarnings({"squid:S1244"})
public class TDTreeHelpers {

    static final double ROOTTWO = FastMath.sqrt(2);
    private static final double LOG_10_2 = FastMath.log10(2);
    static final double[] adjustedLength;
    static final Map<Integer, Integer> computedIDLengths = new Int2IntOpenHashMap(2000, .7f);
    static final Map<Double, Double> normalizedZeroes = new Double2DoubleOpenHashMap(2000, 0.7f);
    public static final String ILLEGAL_DIRECTION = "Cannot have a direction that is not 0-7";

    static {
//        Initialize the lookup tables
        adjustedLength = new double[getIDLength(Integer.MAX_VALUE) + 1];
        computeAdjustedLengths();
    }

    private TDTreeHelpers() {

    }

    static void resetCaches() {
        TDTreeHelpers.computedIDLengths.clear();
        TDTreeHelpers.normalizedZeroes.clear();
    }

    /**
     * Pre-compute the AdjustedLength parameter for all values up to the maximum supported leafID, which is the value of Integer MAX_VALUE
     */
    static void computeAdjustedLengths() {
        for (int i = 0; i < adjustedLength.length; i++) {
            final double adjustedLength = computeAdjustedLength(i);
            TDTreeHelpers.adjustedLength[i] = adjustedLength;
        }
    }

    /**
     * Determines if the given triangle is fully contained in the rectangle denoted by the X/Y bottom-right corner
     *
     * @param apex          - {@link TriangleApex}
     * @param direction     - Direction of triangle
     * @param leafLength    - Leaf length
     * @param rectangleApex - bottom-right X/Y coordinates of rectangle
     * @return - <code>1</code> if the triangle is fully within the rectangle. <code>-2</code> if fully outside, and 0 to -1 if it partially intersects.
     */
    static int checkRectangleIntersection(TriangleApex apex, int direction, int leafLength, long[] rectangleApex, long maxValue) {
        final double[] triangleVerticies = getTriangleVerticies(adjustedLength[leafLength], direction, apex.start, apex.end);
        final int apexInside = ((triangleVerticies[0] <= rectangleApex[0]) && (triangleVerticies[0] >= 0)) && ((triangleVerticies[1] >= rectangleApex[1]) && (triangleVerticies[1] <= maxValue)) ? 1 : 0;
        final int p2Inside = ((triangleVerticies[2] <= rectangleApex[0]) && (triangleVerticies[2] >= 0)) && ((triangleVerticies[3] >= rectangleApex[1]) && (triangleVerticies[3] <= maxValue)) ? 1 : 0;
        final int p3Inside = ((triangleVerticies[4] <= rectangleApex[0]) && (triangleVerticies[4] >= 0)) && ((triangleVerticies[5] >= rectangleApex[1]) && (triangleVerticies[5] <= maxValue)) ? 1 : 0;

        return apexInside + p2Inside + p3Inside - 2;
    }

    static boolean checkPointIntersection(TriangleApex apex, int direction, int leafLength, long startTime, long endTime) {
//        find the apex points
        final double[] verticies = getTriangleVerticies(adjustedLength[leafLength], direction, apex.start, apex.end);
        return pointInTriangle(startTime, endTime, verticies);
    }

    /**
     * Determines if a given point in contained within the given triangle
     * Verticies are passed in as an array (X/Y pairs) in a counter-clockwise orientation
     *
     * @param start             - start (X) coordinate of point
     * @param end               - end (Y) coordinate of point
     * @param triangleVerticies - Triangle verticies (x/y counter-clockwise points)
     * @return - point in triangle?
     */
    static boolean pointInTriangle(long start, long end, double[] triangleVerticies) {
        final boolean b1;
        final boolean b2;
        final boolean b3;
        b1 = sign(start, end, triangleVerticies[0], triangleVerticies[1], triangleVerticies[2], triangleVerticies[3]) < 0.0d;
        b2 = sign(start, end, triangleVerticies[2], triangleVerticies[3], triangleVerticies[4], triangleVerticies[5]) < 0.0d;
        b3 = sign(start, end, triangleVerticies[4], triangleVerticies[5], triangleVerticies[0], triangleVerticies[1]) < 0.0d;

        return ((b1 == b2) && (b2 == b3));
    }

    static boolean triangleIsPoint(double[] triangleVerticies) {
        return ((triangleVerticies[0] == triangleVerticies[2]) && (triangleVerticies[1] == triangleVerticies[3])) ||
                ((triangleVerticies[0] == triangleVerticies[4]) && (triangleVerticies[1] == triangleVerticies[5])) ||
                ((triangleVerticies[2] == triangleVerticies[4]) && (triangleVerticies[3] == triangleVerticies[5]));
    }

    //    http://stackoverflow.com/questions/2049582/how-to-determine-if-a-point-is-in-a-2d-triangle
    static double sign(double p1x, double p1y, double p2x, double p2y, double p3x, double p3y) {
        return (p1x - p3x) * (p2y - p3y) - (p2x - p3x) * (p1y - p3y);
    }

    /**
     * Beginning with the triangle apex, returns a counter-clockwise array of double x/y pairs in the form P(n)X, P(n)Y
     *
     * @param adjustedLength - Adjusted length of Triangle side
     * @param direction      - Direction of triangle
     * @param triangleStart  - Triangle start (X)
     * @param triangleEnd    - Triangle end (Y)
     * @return - double Array of triangle vertex coordinates (X/Y)
     */
    static double[] getTriangleVerticies(double adjustedLength, int direction, double triangleStart, double triangleEnd) {
        double[] verticies = new double[6];
        verticies[0] = triangleStart;
        verticies[1] = triangleEnd;
        switch (direction) {
            case 0: {
                final double l2 = (adjustedLength * ROOTTWO) / 2;
                verticies[2] = normalizeZero(triangleStart - l2);
                verticies[3] = normalizeZero(triangleEnd - l2);
                verticies[4] = normalizeZero(triangleStart + l2);
                verticies[5] = normalizeZero(triangleEnd - l2);
                break;
            }
            case 1: {
                verticies[2] = normalizeZero(triangleStart - adjustedLength);
                verticies[3] = triangleEnd;
                verticies[4] = triangleStart;
                verticies[5] = normalizeZero(triangleEnd - adjustedLength);
                break;
            }
            case 2: {
                final double l2 = (adjustedLength * ROOTTWO) / 2;
                verticies[2] = normalizeZero(triangleStart - l2);
                verticies[3] = normalizeZero(triangleEnd + l2);
                verticies[4] = normalizeZero(triangleStart - l2);
                verticies[5] = normalizeZero(triangleEnd - l2);
                break;
            }
            case 3: {
                verticies[2] = triangleStart;
                verticies[3] = normalizeZero(triangleEnd + adjustedLength);
                verticies[4] = normalizeZero(triangleStart - adjustedLength);
                verticies[5] = triangleEnd;
                break;
            }
            case 4: {
                final double l2 = (adjustedLength * ROOTTWO) / 2;
                verticies[2] = normalizeZero(triangleStart + l2);
                verticies[3] = normalizeZero(triangleEnd + l2);
                verticies[4] = normalizeZero(triangleStart - l2);
                verticies[5] = normalizeZero(triangleEnd + l2);
                break;
            }
            case 5: {
                verticies[2] = normalizeZero(triangleStart + adjustedLength);
                verticies[3] = triangleEnd;
                verticies[4] = triangleStart;
                verticies[5] = normalizeZero(triangleEnd + adjustedLength);
                break;
            }
            case 6: {
                final double l2 = (adjustedLength * ROOTTWO) / 2;
                verticies[2] = normalizeZero(triangleStart + l2);
                verticies[3] = normalizeZero(triangleEnd - l2);
                verticies[4] = normalizeZero(triangleStart + l2);
                verticies[5] = normalizeZero(triangleEnd + l2);
                break;
            }
            case 7: {
                verticies[2] = triangleStart;
                verticies[3] = normalizeZero(triangleEnd - adjustedLength);
                verticies[4] = normalizeZero(triangleStart + adjustedLength);
                verticies[5] = triangleEnd;
                break;
            }
            default: {
                throw new IllegalStateException(ILLEGAL_DIRECTION);
            }
        }
        return verticies;
    }

    /**
     * Rounds values to their 6th decimal place, and gets their abs
     * Used to deal with near-zero values caused by rounding errors
     *
     * @return - normalized Zero Value
     */
//    FIXME(nrobison): This needs to get a calculated precision
    private static double normalizeZero(double value) {
        return normalizedZeroes.computeIfAbsent(value, TDTreeHelpers::computeNormalizedZero);
    }

    private static double computeNormalizedZero(double value) {
        final double abs = FastMath.abs(Precision.round(value, 6));
//        If the value falls outside of the maxValue range, return the maxValue. This adjusts for double rounding issues
        if (abs > TDTree.maxValue) {
            return TDTree.maxValue;
        }
        return abs;
    }

    /**
     * Determine the triangle direction of the given leaf
     * Usually starts at depth 0 and direction 7, which is the base triangle
     *
     * @param leafID          - Leaf to get direction
     * @param depth           - Starting depth
     * @param parentDirection - Starting direction
     * @return - Integer 0-7 of triangle direction
     */
    static int calculateTriangleDirection(int leafID, int depth, int parentDirection) {
        if (getIDLength(leafID) - 1 == depth) {
            return parentDirection;
        }
        final ChildDirection childDirection = calculateChildDirection(parentDirection);
        final int prefix = leafID >> ((getIDLength(leafID) - depth - 1) - 1);
        if ((prefix & 1) == 0) {
            return calculateTriangleDirection(leafID, depth + 1, childDirection.lowerChild);
        }
        return calculateTriangleDirection(leafID, depth + 1, childDirection.higherChild);
    }

    /**
     * Determine the apex of the of the given leaf
     * Usually starts at depth 0 and direction seven, with start/end of 0.0/maxValue which is the base triangle
     *
     * @param leafID          - Leaf to get apex
     * @param depth           - Starting depth
     * @param parentDirection - Starting direction
     * @param parentStart     - Starting X coordinate
     * @param parentEnd       - Starting Y coordinate
     * @return - {@link TriangleApex} of given leaf
     */
    static TriangleApex calculateTriangleApex(int leafID, int depth, int parentDirection, double parentStart, double parentEnd) {
        if (getIDLength(leafID) - 1 == depth) {
            return new TriangleApex(parentStart, parentEnd);
        }
//        Get the current depth
        final int thisPrefix = leafID >> ((getIDLength(leafID) - depth - 1) - 1);
//        Get the apex and direction for the triangle at this level
        final int thisDirection = calculateTriangleDirection(thisPrefix, depth, parentDirection);
        final TriangleApex thisApex = calculateChildApex(getIDLength(thisPrefix), parentDirection, parentStart, parentEnd);
        return calculateTriangleApex(leafID, depth + 1, thisDirection, thisApex.start, thisApex.end);

    }

    static ChildDirection calculateChildDirection(int parentDirection) {
        if (parentDirection >= 1 && parentDirection <= 4) {
            return new ChildDirection((parentDirection + 5) % 8, parentDirection + 3);
        } else {
            return new ChildDirection((parentDirection + 3) % 8, (parentDirection + 5) % 8);
        }
    }

    static TriangleApex calculateChildApex(int leafLength, int parentDirection, double parentStart, double parentEnd) {
        final double length = adjustedLength[leafLength];
        switch (parentDirection) {
            case 0: {
                return new TriangleApex(
                        parentStart,
                        normalizeZero(parentEnd - length));
            }
            case 1: {
                final double l2 = length / ROOTTWO;
                return new TriangleApex(
                        normalizeZero(parentStart - l2),
                        normalizeZero(parentEnd - l2));
            }
            case 2: {
                return new TriangleApex(
                        normalizeZero(parentStart - length),
                        parentEnd);
            }
            case 3: {
                final double l2 = length / ROOTTWO;
                return new TriangleApex(
                        normalizeZero(parentStart - l2),
                        normalizeZero(parentEnd + l2));
            }
            case 4: {
                return new TriangleApex(
                        parentStart,
                        normalizeZero(parentEnd + length));
            }
            case 5: {
                final double l2 = length / ROOTTWO;
                return new TriangleApex(
                        normalizeZero(parentStart + l2),
                        normalizeZero(parentEnd + l2));
            }
            case 6: {
                return new TriangleApex(
                        normalizeZero(parentStart + length),
                        parentEnd);
            }
            case 7: {
                final double l2 = length / ROOTTWO;
                return new TriangleApex(
                        normalizeZero(parentStart + l2),
                        normalizeZero(parentEnd - l2));
            }
            default: {
                throw new IllegalStateException(ILLEGAL_DIRECTION);
            }
        }
    }

    /**
     * Gets the number of bits used for the integer value
     * If the value has been previously computed, it will be used
     *
     * @param leafID - Integer of leaf ID
     * @return - number of used bits
     */
    static int getIDLength(int leafID) {
        return computedIDLengths.computeIfAbsent(leafID, TDTreeHelpers::computeIDLength);
    }

    /**
     * Computes the number of bits used for the integer value
     *
     * @param leafID - Integer of leaf ID
     * @return - number of used bits
     */
    static int computeIDLength(int leafID) {
        return (int) FastMath.floor(FastMath.log10(leafID) / LOG_10_2) + 1;
    }

    /**
     * Gets the maximum value representable by a binary string of the same length as the leafID
     *
     * @param leafID - leafID to get binary length from
     * @return - maximum integer value
     */
    static int getMaximumValue(int leafID) {
        int mask = 1;
        for (int i = 1; i < getIDLength(leafID); i++) {
            mask |= mask << 1;
        }
        return mask;
    }

    /**
     * Computee the adjusted length for the given leaf ID length
     *
     * @param leafLength - length of LeafID
     * @return - double of adjusted length
     */
    private static double computeAdjustedLength(int leafLength) {
        return TDTree.maxValue * (FastMath.pow(ROOTTWO / 2, leafLength - 1));
    }

    static long longHashCode(String string) {
        long h = 1125899906842597L; // prime
        int len = string.length();

        for (int i = 0; i < len; i++) {
            h = 31 * h + string.charAt(i);
        }
        return h;
    }

    static class ChildDirection {
        final int lowerChild;
        final int higherChild;

        ChildDirection(int lowerChild, int higherChild) {
            this.lowerChild = lowerChild;
            this.higherChild = higherChild;
        }
    }

    static class TriangleApex {
        final double start;
        final double end;

        TriangleApex(double start, double end) {
            this.end = end;
            this.start = start;
        }
    }
}
