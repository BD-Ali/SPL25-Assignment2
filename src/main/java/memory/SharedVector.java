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
        // Store vector data and its orientation
        if (vector == null) throw new IllegalArgumentException("vector is null");
        if (orientation == null) throw new IllegalArgumentException("orientation is null");
        this.vector = vector.clone();
        this.orientation = orientation;
    }

    public double get(int index) {
        // Return element at index (read-locked)
        readLock();
        try {
            return vector[index];
        } finally {
            readUnlock();
        }
    }

    public int length() {
        // Return vector length (read-locked)
        readLock();
        try {
            return vector.length;
        } finally {
            readUnlock();
        }
    }

    public VectorOrientation getOrientation() {
        // Return vector orientation (read-locked)
        readLock();
        try {
            return orientation;
        } finally {
            readUnlock();
        }
    }

    public void writeLock() {
        // Acquire write lock
        lock.writeLock().lock();
    }

    public void writeUnlock() {
        // Release write lock
        lock.writeLock().unlock();
    }

    public void readLock() {
        // Acquire read lock
        lock.readLock().lock();

    }

    public void readUnlock() {
        // Release read lock
        lock.readLock().unlock();
    }

    public void transpose() {
        // Transpose vector - flip orientation between ROW_MAJOR and COLUMN_MAJOR
        // Numeric array stays the same
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
        // Add two vectors element-wise (this = this + other)
        // Requires same length and orientation
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
        // Negate vector - multiply all elements by -1
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
        // Compute dot product (row · column)
        // Requires this to be ROW_MAJOR and other to be COLUMN_MAJOR
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
        // Compute row-vector × matrix
        // Multiplies this (as row vector) by matrix, stores result back into this
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
