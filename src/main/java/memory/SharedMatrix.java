package memory;

/**
 * SharedMatrix - Thread-safe matrix storage using SharedVector objects.
 * 
 * Design:
 * - Stores matrix data as an array of SharedVector objects
 * - Can store data in row-major or column-major format
 * - Does NOT implement any locking itself (per assignment requirements)
 * - Relies on SharedVector's locks for thread-safe access to individual vectors
 * 
 * Thread Safety:
 * - Uses volatile fields for visibility across threads
 * - The readRowMajor() method acquires locks on all vectors before reading
 * - Orientation is determined from the vectors themselves to ensure consistency
 * 
 * Usage:
 * - loadRowMajor(): Each SharedVector represents one row
 * - loadColumnMajor(): Each SharedVector represents one column
 * - readRowMajor(): Always returns data in row-major format (transposes if needed)
 */
public class SharedMatrix {

    private volatile SharedVector[] vectors = {}; // underlying vectors

    /**
     * Creates an empty matrix with row-major orientation.
     */
    public SharedMatrix() {
        this.vectors = new SharedVector[0];
    }

    /**
     * Creates a matrix from a 2D array, stored in row-major format.
     */
    public SharedMatrix(double[][] matrix) {
        loadRowMajor(matrix);
    }

    /**
     * Loads a matrix in row-major format.
     * Each SharedVector will represent one row of the matrix.
     * 
     * @param matrix The input matrix (row-major 2D array)
     */
    public void loadRowMajor(double[][] matrix) {
        int numRows = matrix.length;
        SharedVector[] newVectors = new SharedVector[numRows];
        for (int i = 0; i < numRows; i++) {
            // Clone the row to prevent external modification
            newVectors[i] = new SharedVector(matrix[i].clone(), VectorOrientation.ROW_MAJOR);
        }
        
        // Update vectors atomically
        this.vectors = newVectors;
    }

    /**
     * Loads a matrix in column-major format.
     * Each SharedVector will represent one column of the matrix.
     * Input is still row-major, but storage is column-major.
     * 
     * This format is useful for matrix multiplication where we need
     * efficient access to columns for dot product computation.
     * 
     * @param matrix The input matrix (row-major 2D array)
     */
    public void loadColumnMajor(double[][] matrix) {
        // matrix is row-major input, but we store as column vectors
        int numRows = matrix.length;
        if (numRows == 0) {
            this.vectors = new SharedVector[0];
            return;
        }
        int numCols = matrix[0].length;
        SharedVector[] newVectors = new SharedVector[numCols];
        
        // Extract each column into a separate SharedVector
        for (int col = 0; col < numCols; col++) {
            double[] columnData = new double[numRows];
            for (int row = 0; row < numRows; row++) {
                columnData[row] = matrix[row][col];
            }
            newVectors[col] = new SharedVector(columnData, VectorOrientation.COLUMN_MAJOR);
        }
        this.vectors = newVectors;
    }

    /**
     * Reads the matrix in row-major format.
     * If stored as column-major, transposes the data during read.
     * 
     * Thread Safety:
     * - Captures a snapshot of the vectors array (volatile read)
     * - Acquires read locks on ALL vectors before reading any data
     * - Determines orientation FROM the vectors themselves, not the matrix field
     *   This ensures consistency even if another thread is loading new data
     * 
     * @return 2D array in row-major format
     */
    public double[][] readRowMajor() {
        // Capture snapshot of vectors array (single volatile read)
        SharedVector[] vecs = this.vectors;
        
        // Acquire read locks on all vectors BEFORE reading any data
        // This ensures a consistent view of the entire matrix
        acquireAllVectorReadLocks(vecs);
        try {
            // Determine orientation from the vectors themselves (not the matrix field)
            // This guarantees consistency: the orientation is stored WITH the data
            VectorOrientation orient;
            if (vecs.length > 0) {
                orient = vecs[0].getOrientation();
            } else {
                orient = VectorOrientation.ROW_MAJOR;
            }
            
            if (orient == VectorOrientation.ROW_MAJOR) {
                // Each vector is a row - read directly
                double[][] result = new double[vecs.length][];
                for (int i = 0; i < vecs.length; i++) {
                    int len = vecs[i].length();
                    result[i] = new double[len];
                    for (int j = 0; j < len; j++) {
                        result[i][j] = vecs[i].get(j);
                    }
                }
                return result;
            } else {
                // Each vector is a column - transpose during read
                if (vecs.length == 0) {
                    return new double[0][0];
                }
                int numCols = vecs.length;
                int numRows = vecs[0].length();
                double[][] result = new double[numRows][numCols];
                for (int col = 0; col < numCols; col++) {
                    for (int row = 0; row < numRows; row++) {
                        result[row][col] = vecs[col].get(row);
                    }
                }
                return result;
            }
        } finally {
            releaseAllVectorReadLocks(vecs);
        }
    }

    /**
     * Returns the SharedVector at the specified index.
     * For row-major: returns the i-th row
     * For column-major: returns the i-th column
     */
    public SharedVector get(int index) {
        SharedVector[] vecs = this.vectors;
        return vecs[index];
    }

    /**
     * Returns the number of vectors in the matrix.
     * For row-major: number of rows
     * For column-major: number of columns
     */
    public int length() {
        SharedVector[] vecs = this.vectors;
        return vecs.length;
    }

    /**
     * Returns the current orientation of the matrix storage.
     * Derived from the vectors themselves for consistency.
     */
    public VectorOrientation getOrientation() {
        SharedVector[] vecs = this.vectors;
        if (vecs.length > 0) {
            return vecs[0].getOrientation();
        } else {
            return VectorOrientation.ROW_MAJOR;
        }
    }

    /**
     * Acquires read locks on all vectors in the array.
     * Must be called before reading from multiple vectors to ensure consistency.
     */
    private void acquireAllVectorReadLocks(SharedVector[] vecs) {
        for (SharedVector vec : vecs) {
            vec.readLock();
        }
    }

    /**
     * Releases read locks on all vectors in the array.
     * Must be called after reading is complete.
     */
    private void releaseAllVectorReadLocks(SharedVector[] vecs) {
        for (SharedVector vec : vecs) {
            vec.readUnlock();
        }
    }

    /**
     * Acquires write locks on all vectors in the array.
     */
    private void acquireAllVectorWriteLocks(SharedVector[] vecs) {
        for (SharedVector vec : vecs) {
            vec.writeLock();
        }
    }

    /**
     * Releases write locks on all vectors in the array.
     */
    private void releaseAllVectorWriteLocks(SharedVector[] vecs) {
        for (SharedVector vec : vecs) {
            vec.writeUnlock();
        }
    }
}
