package memory;

public class SharedMatrix {

    private volatile SharedVector[] vectors = {}; // underlying vectors
    private volatile VectorOrientation orientation = VectorOrientation.ROW_MAJOR;

    public SharedMatrix() {
        this.vectors = new SharedVector[0];
        this.orientation = VectorOrientation.ROW_MAJOR;
    }

    public SharedMatrix(double[][] matrix) {
        if (matrix == null) throw new IllegalArgumentException("matrix is null");
        
        int numRows = matrix.length;
        int numCols = 0;
        if (numRows > 0) {
            if (matrix[0] == null) throw new IllegalArgumentException("matrix row 0 is null");
            numCols = matrix[0].length;
        }
        
        this.vectors = new SharedVector[numRows];
        for (int i = 0; i < numRows; i++) {
            if (matrix[i] == null) throw new IllegalArgumentException("matrix row " + i + " is null");
            if (matrix[i].length != numCols)
                throw new IllegalArgumentException("matrix row " + i + " length mismatch");
            this.vectors[i] = new SharedVector(matrix[i], VectorOrientation.ROW_MAJOR);
        }
        this.orientation = VectorOrientation.ROW_MAJOR;
    }

    public void loadRowMajor(double[][] matrix) {
        if (matrix == null) throw new IllegalArgumentException("matrix is null");
        
        int numRows = matrix.length;
        int numCols = 0;
        if (numRows > 0) {
            if (matrix[0] == null) throw new IllegalArgumentException("matrix row 0 is null");
            numCols = matrix[0].length;
        }
        
        this.vectors = new SharedVector[numRows];
        for (int i = 0; i < numRows; i++) {
            if (matrix[i] == null) throw new IllegalArgumentException("matrix row " + i + " is null");
            if (matrix[i].length != numCols)
                throw new IllegalArgumentException("matrix row " + i + " length mismatch");
            this.vectors[i] = new SharedVector(matrix[i], VectorOrientation.ROW_MAJOR);
        }
        this.orientation = VectorOrientation.ROW_MAJOR;
    }

    public void loadColumnMajor(double[][] matrix) {
        if (matrix == null) throw new IllegalArgumentException("matrix is null");
        int numRows = matrix.length;
        int numCols = 0;
        if (numRows > 0) numCols = matrix[0].length;
        this.vectors = new SharedVector[numCols];
        for (int j = 0; j < numCols; j++) {
            double[] colData = new double[numRows];
            for (int i = 0; i < numRows; i++) {
                if (matrix[i] == null) throw new IllegalArgumentException("matrix row " + i + " is null");
                if (matrix[i].length != numCols)
                    throw new IllegalArgumentException("matrix row " + i + " length mismatch");
                colData[i] = matrix[i][j];
            }
            this.vectors[j] = new SharedVector(colData, VectorOrientation.COLUMN_MAJOR);
        }
        this.orientation = VectorOrientation.COLUMN_MAJOR;
    }

    public double[][] readRowMajor() {
        if (vectors.length == 0) return new double[0][0];
        
        if (orientation == VectorOrientation.ROW_MAJOR) {
            int numRows = vectors.length;
            int numCols = vectors[0].length();
            double[][] result = new double[numRows][numCols];
            for (int i = 0; i < numRows; i++) {
                for (int j = 0; j < numCols; j++) {
                    result[i][j] = vectors[i].get(j);
                }
            }
            return result;
        } else {
            // COLUMN_MAJOR: transpose
            int numCols = vectors.length;
            int numRows = vectors[0].length();
            double[][] result = new double[numRows][numCols];
            for (int j = 0; j < numCols; j++) {
                for (int i = 0; i < numRows; i++) {
                    result[i][j] = vectors[j].get(i);
                }
            }
            return result;
        }
    }

    public SharedVector get(int index) {
        if (index < 0 || index >= vectors.length)
            throw new IndexOutOfBoundsException("index out of bounds");
        return vectors[index];
    }

    public int length() {
        return vectors.length;
    }

    public VectorOrientation getOrientation() {
        return orientation;
    }

    private void acquireAllVectorReadLocks(SharedVector[] vecs) {
        for (SharedVector vec : vecs) {
            vec.readLock();
        }
    }

    private void releaseAllVectorReadLocks(SharedVector[] vecs) {
        for (SharedVector vec : vecs) {
            vec.readUnlock();
        }
    }

    private void acquireAllVectorWriteLocks(SharedVector[] vecs) {
        for (SharedVector vec : vecs) {
            vec.writeLock();
        }
    }

    private void releaseAllVectorWriteLocks(SharedVector[] vecs) {
        for (SharedVector vec : vecs) {
            vec.writeUnlock();
        }
    }
}