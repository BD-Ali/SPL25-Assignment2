package memory;

import java.util.Arrays;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * SharedVector - Thread-safe vector implementation using ReentrantReadWriteLock.
 * 
 * Design:
 * - Stores a double array with an orientation (row or column)
 * - Uses ReadWriteLock for fine-grained concurrency control
 * - Multiple threads can read simultaneously, but writes are exclusive
 * 
 * Thread Safety:
 * - All read operations acquire read locks
 * - All write operations acquire write locks  
 * - Multi-vector operations use consistent lock ordering to prevent deadlock
 * - Special handling for self-operations (v.add(v)) to avoid self-deadlock
 * 
 * Lock Ordering:
 * - When locking multiple vectors, uses System.identityHashCode() for ordering
 * - This prevents deadlock when two threads lock the same pair in opposite order
 */
public class SharedVector {

    private double[] vector;            // The underlying data array
    private VectorOrientation orientation;  // ROW_MAJOR or COLUMN_MAJOR
    private ReadWriteLock lock = new java.util.concurrent.locks.ReentrantReadWriteLock();

    /**
     * Creates a new SharedVector with the given data and orientation.
     * 
     * @param vector The data array (not cloned - caller should clone if needed)
     * @param orientation Whether this is a row or column vector
     * @throws IllegalArgumentException if vector or orientation is null
     */
    public SharedVector(double[] vector, VectorOrientation orientation) {
        if (vector == null) {
            throw new IllegalArgumentException("vector cannot be null");
        }
        if (orientation == null) {
            throw new IllegalArgumentException("orientation cannot be null");
        }
        this.vector = vector;
        this.orientation = orientation;
    }

    /**
     * Gets the value at the specified index.
     * Thread-safe: acquires read lock during access.
     * 
     * @param index The index to read from
     * @return The value at the index
     * @throws ArrayIndexOutOfBoundsException if index is out of bounds
     */
    public double get(int index) {
        if (index < 0 || index >= vector.length) {
            throw new ArrayIndexOutOfBoundsException("Index out of bounds: " + index);
        }
        readLock();
        try {
            return vector[index];
        } finally {
            readUnlock();
        }
    }

    /**
     * Returns the length of this vector.
     * Thread-safe: acquires read lock during access.
     * 
     * @return The number of elements in the vector
     */
    public int length() {
        if (vector == null) {
            throw new IllegalStateException("Vector data is null");
        }
        readLock();
        try {
            return vector.length;
        } finally {
            readUnlock();
        }
    }

    /**
     * Returns the orientation of this vector.
     * Thread-safe: acquires read lock since orientation can be changed by transpose().
     */
    public VectorOrientation getOrientation() {
        readLock();
        try {
            return orientation;
        } finally {
            readUnlock();
        }
    }

    // ========== Lock Management Methods ==========
    // These are public so that SharedMatrix can coordinate locking
    // across multiple vectors for atomic operations.

    /** Acquires the write lock (exclusive access) */
    public void writeLock() {
        lock.writeLock().lock();
    }

    /** Releases the write lock */
    public void writeUnlock() {
        lock.writeLock().unlock();
    }

    /** Acquires the read lock (shared access) */
    public void readLock() {
        lock.readLock().lock();
    }

    /** Releases the read lock */
    public void readUnlock() {
        lock.readLock().unlock();
    }

    // ========== Mutation Operations ==========

    /**
     * Transposes this vector by flipping its orientation.
     * A row vector becomes a column vector and vice versa.
     * Thread-safe: acquires write lock for the modification.
     */
    public void transpose() {
        writeLock();
        try {
            if (orientation == VectorOrientation.ROW_MAJOR) {
                orientation = VectorOrientation.COLUMN_MAJOR;
            } else {
                orientation = VectorOrientation.ROW_MAJOR;
            }
        } finally {
            writeUnlock();
        }
    }

    /**
     * Adds another vector to this vector element-wise: this += other
     * 
     * Thread Safety:
     * - Handles self-addition (v.add(v)) specially to avoid self-deadlock
     * - For different vectors, uses identity hash code ordering to prevent deadlock
     * 
     * @param other The vector to add
     * @throws IllegalArgumentException if vectors have different lengths
     */
    public void add(SharedVector other) {
        if (this.length() != other.length()) {
            throw new IllegalArgumentException("Vectors must have the same length to add");
        }

        // Special case: adding vector to itself (v + v = 2v)
        if (this == other) {
            writeLock();
            try {
                for (int i = 0; i < vector.length; i++) {
                    vector[i] += vector[i];
                }
            } finally {
                writeUnlock();
            }
            return;
        }

        // Lock in hash order to prevent deadlock (lower hash first)
        // Use ReadWriteLock consistently with the rest of the class
        if (System.identityHashCode(this) < System.identityHashCode(other)) {
            this.writeLock();
            try {
                other.readLock();
                try {
                    for (int i = 0; i < vector.length; i++) {
                        vector[i] += other.vector[i];
                    }
                } finally {
                    other.readUnlock();
                }
            } finally {
                this.writeUnlock();
            }
        } else {
            other.readLock();
            try {
                this.writeLock();
                try {
                    for (int i = 0; i < vector.length; i++) {
                        vector[i] += other.vector[i];
                    }
                } finally {
                    this.writeUnlock();
                }
            } finally {
                other.readUnlock();
            }
        }
    }

    /**
     * Negates all elements in this vector: this = -this
     * Thread-safe: acquires write lock for the modification.
     */
    public void negate() {
        writeLock();
        try {
            for (int i = 0; i < vector.length; i++) {
                vector[i] = -vector[i];
            }
        } finally {
            writeUnlock();
        }
    }

    /**
     * Computes the dot product of this vector with another.
     * 
     * Thread Safety:
     * - Uses consistent lock ordering based on identity hash code
     * 
     * @param other The vector to dot with
     * @return The scalar dot product
     * @throws IllegalArgumentException if vectors have different lengths
     */
    public double dot(SharedVector other) {
        if (this.vector.length != other.vector.length) {
            throw new IllegalArgumentException("Vectors must be of the same length for dot product");
        }

        // Special case: dot product with self
        if (this == other) {
            readLock();
            try {
                double sum = 0.0;
                for (int i = 0; i < vector.length; i++) {
                    sum += vector[i] * vector[i];
                }
                return sum;
            } finally {
                readUnlock();
            }
        }

        // Lock in hash order to prevent deadlock (lower hash first)
        // Use ReadWriteLock consistently - both are read operations
        if (System.identityHashCode(this) < System.identityHashCode(other)) {
            this.readLock();
            try {
                other.readLock();
                try {
                    double sum = 0.0;
                    for (int i = 0; i < vector.length; i++) {
                        sum += vector[i] * other.vector[i];
                    }
                    return sum;
                } finally {
                    other.readUnlock();
                }
            } finally {
                this.readUnlock();
            }
        } else {
            other.readLock();
            try {
                this.readLock();
                try {
                    double sum = 0.0;
                    for (int i = 0; i < vector.length; i++) {
                        sum += vector[i] * other.vector[i];
                    }
                    return sum;
                } finally {
                    this.readUnlock();
                }
            } finally {
                other.readUnlock();
            }
        }
    }

    /**
     * Multiplies this row vector by a column-major matrix.
     * Result: this = this * matrix (row vector times matrix gives row vector)
     * 
     * Thread Safety:
     * - Collects all involved vectors (this + all matrix columns)
     * - Sorts by identityHashCode for consistent global lock ordering
     * - This prevents deadlock with concurrent operations on same vectors
     * 
     * @param matrix The column-major matrix to multiply by
     * @throws IllegalArgumentException if dimensions don't match
     */
    public void vecMatMul(SharedMatrix matrix) {
        int numCols = matrix.length();
        if (numCols == 0) {
            return;
        }
        
        if (this.length() != matrix.get(0).length()) {
            throw new IllegalArgumentException("Vector length must match number of rows in matrix for vecMatMul");
        }

        // Collect all vectors: this + all matrix columns
        SharedVector[] allVectors = new SharedVector[numCols + 1];
        allVectors[0] = this;
        for (int i = 0; i < numCols; i++) {
            allVectors[i + 1] = matrix.get(i);
        }
        
        // Sort by identityHashCode for consistent lock ordering
        Arrays.sort(allVectors, (a, b) -> 
            Integer.compare(System.identityHashCode(a), System.identityHashCode(b)));
        
        // Acquire all locks in sorted order
        for (SharedVector v : allVectors) {
            if (v == this) {
                v.writeLock();
            } else {
                v.readLock();
            }
        }
        
        try {
            double[] result = new double[numCols];
            for (int i = 0; i < numCols; i++) {
                SharedVector col = matrix.get(i);
                double sum = 0.0;
                for (int j = 0; j < vector.length; j++) {
                    sum += vector[j] * col.vector[j];
                }
                result[i] = sum;
            }
            this.vector = result;
        } finally {
            // Release in reverse order
            for (int i = allVectors.length - 1; i >= 0; i--) {
                SharedVector v = allVectors[i];
                if (v == this) {
                    v.writeUnlock();
                } else {
                    v.readUnlock();
                }
            }
        }
    }
}
