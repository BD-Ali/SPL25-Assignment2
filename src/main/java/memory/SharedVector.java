package memory;

import java.util.concurrent.locks.ReadWriteLock;

public class SharedVector {

    private double[] vector;
    private VectorOrientation orientation;
    private ReadWriteLock lock = new java.util.concurrent.locks.ReentrantReadWriteLock();

    public SharedVector(double[] vector, VectorOrientation orientation) {
        // TODO: store vector data and its orientation
    }

    public double get(int index) {
        // TODO: return element at index (read-locked)
        return 0;
    }

    public int length() {
        // TODO: return vector length
        return 0;
    }

    public VectorOrientation getOrientation() {
        // TODO: return vector orientation
        return null;
    }

    public void writeLock() {
        // TODO: acquire write lock
    }

    public void writeUnlock() {
        // TODO: release write lock
    }

    public void readLock() {
        // TODO: acquire read lock
    }

    public void readUnlock() {
        // TODO: release read lock
    }

    public void transpose() {
        // TODO: transpose vector
    }

    public void add(SharedVector other) {
        // TODO: add two vectors
    }

    public void negate() {
        // TODO: negate vector
    }

    public double dot(SharedVector other) {
        // TODO: compute dot product (row · column)
        return 0;
    }

    public void vecMatMul(SharedMatrix matrix) {
        // TODO: compute row-vector × matrix
    }
}
