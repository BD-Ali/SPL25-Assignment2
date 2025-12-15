package memory;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.atomic.AtomicInteger;

public class SharedVector {

    private double[] vector;
    private VectorOrientation orientation;
    private ReadWriteLock lock = new java.util.concurrent.locks.ReentrantReadWriteLock();
    // Unique ID generator
    private static final AtomicInteger NEXT_ID = new AtomicInteger(0);
    private final int id = NEXT_ID.getAndIncrement();


    public SharedVector(double[] vector, VectorOrientation orientation) {
        // TODO: store vector data and its orientation
        if (vector == null) throw new IllegalArgumentException("vector is null");
        if (orientation == null) throw new IllegalArgumentException("orientation is null");
        this.vector = vector;
        this.orientation = orientation;
    }

    public double get(int index) {
        // TODO: return element at index (read-locked)
        readLock();
        try {
            return vector[index];
        } finally {
            readUnlock();
        }
    }

    public int length() {
        // TODO: return vector length
        readLock();
        try {
            return vector.length;
        } finally {
            readUnlock();
        }
    }

    public VectorOrientation getOrientation() {
        // TODO: return vector orientation
        readLock();
        try {
            return orientation;
        } finally {
            readUnlock();
        }
    }

    public void writeLock() {
        // TODO: acquire write lock
        lock.writeLock().lock();
    }

    public void writeUnlock() {
        // TODO: release write lock
        lock.writeLock().unlock();
    }

    public void readLock() {
        // TODO: acquire read lock
        lock.readLock().lock();

    }

    public void readUnlock() {
        // TODO: release read lock
        lock.readLock().unlock();
    }

    public void transpose() {
        // TODO: transpose vector
        // “Transpose” at vector level means: row <-> column metadata flip
        // Numeric array stays the same.
        writeLock();
        try {
            if (orientation == VectorOrientation.ROW_MAJOR) {
                orientation = VectorOrientation.COLUMN_MAJOR;
            } else {
                orientation = VectorOrientation.ROW_MAJOR;}
        } 
        finally {
            writeUnlock();
        }
    }

    public void add(SharedVector other) {
        // TODO: add two vectors
        if (other == null) throw new IllegalArgumentException("other is null");
        if (this.length() != other.length())
            throw new IllegalArgumentException("Vector length mismatch");

        lockOrdered(this, true, other, false); // this write, other read
        try {
            for (int i = 0; i < vector.length; i++) vector[i] += other.vector[i];
        } finally {
            unlockOrdered(this, true, other, false);
}

        
    }

    public void negate() {
        // TODO: negate vector
        writeLock();
        try {
            for (int i = 0; i < vector.length; i++) {
                vector[i] = -vector[i];
            }
        } finally {
            writeUnlock();
        }
    }

    public double dot(SharedVector other) {
        // TODO: compute dot product (row · column)
        if (other == null) throw new IllegalArgumentException("other is null");
        if (this.length() != other.length())
            throw new IllegalArgumentException("Vector length mismatch");

        lockOrdered(this, false, other, false); // both read
        try {
            double sum = 0.0;
            for (int i = 0; i < vector.length; i++) sum += this.vector[i] * other.vector[i];
            return sum;
        } finally {
            unlockOrdered(this, false, other, false);
        }
    }

    public void vecMatMul(SharedMatrix matrix) {
        // TODO: compute row-vector × matrix
        // Computes: (this row-vector) × matrix  -> stores result back into THIS vector
        if (matrix == null) throw new IllegalArgumentException("matrix is null");
        if (this.orientation != VectorOrientation.ROW_MAJOR)
            throw new IllegalStateException("vecMatMul expects this to be ROW_MAJOR");

        // Efficient if matrix is column-major: each column is a vector
        // Each output entry j is dot(thisRow, matrixColumn[j]).
        int numCols = matrix.length();
        double[] result = new double[numCols];

        // Read-lock this once, then read-lock each column as we go (avoid locking all at once).
        this.readLock();
        try {
            for (int j = 0; j < numCols; j++) {
                SharedVector col = matrix.get(j);
                col.readLock();
                try {
                    if (col.length() != this.vector.length)
                        throw new IllegalArgumentException("Dimension mismatch in vecMatMul");
                    double sum = 0.0;
                    for (int i = 0; i < this.vector.length; i++) {
                        sum += this.vector[i] * col.vector[i];
                    }
                    result[j] = sum;
                } finally {
                    col.readUnlock();
                }
            }
        } finally {
            this.readUnlock();
        }
        // Write the result into THIS (must be a write)
        this.writeLock();
        try {
            this.vector = result;
            this.orientation = VectorOrientation.ROW_MAJOR;
        } finally {
            this.writeUnlock();
        }
    }

    private static void lockOrdered(SharedVector a, boolean aWrite, SharedVector b, boolean bWrite) {
    if (a == b) {
        if (aWrite || bWrite) a.writeLock(); else a.readLock();
        return;
    }

    SharedVector first;
    SharedVector second;
    if (a.id < b.id) {
        first = a;
        second = b;
    } else {
        first = b;
        second = a;
    }

    // lock first
    if ((first == a && aWrite) || (first == b && bWrite)) first.writeLock();
    else first.readLock();

    // lock second
    if ((second == a && aWrite) || (second == b && bWrite)) second.writeLock();
    else second.readLock();
}

private static void unlockOrdered(SharedVector a, boolean aWrite, SharedVector b, boolean bWrite) {
    if (a == b) {
        if (aWrite || bWrite) a.writeUnlock(); 
        else a.readUnlock();
        return;
    }

    SharedVector first;
    SharedVector second;
    if (a.id < b.id) {
        first = a;
        second = b;
    } else {
        first = b;
        second = a;
    }

    // unlock reverse order
    if ((second == a && aWrite) || (second == b && bWrite)) second.writeUnlock();
    else second.readUnlock();

    if ((first == a && aWrite) || (first == b && bWrite)) first.writeUnlock();
    else first.readUnlock();
}

}
