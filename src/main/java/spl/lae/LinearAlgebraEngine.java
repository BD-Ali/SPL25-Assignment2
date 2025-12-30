package spl.lae;

import parser.*;
import memory.*;
import scheduling.*;

import java.util.List;
import java.util.ArrayList;

/**
 * LinearAlgebraEngine (LAE) - Orchestrates parallel matrix computations.
 * 
 * This class is responsible for:
 * 1. Iteratively finding resolvable nodes in the computation tree
 *    (nodes whose operands are all concrete matrices)
 * 2. Loading operand matrices into shared memory (M1=leftMatrix, M2=rightMatrix)
 * 3. Decomposing operations into parallel tasks that work on SharedVector objects
 * 4. Submitting tasks to the TiredExecutor thread pool and waiting for completion
 * 5. Reading results from M1 and attaching them back to the computation tree
 * 
 * Thread Safety:
 * - All results are written to leftMatrix (M1)
 * - The executor handles task distribution based on worker fatigue
 * - Shutdown is guaranteed even if an exception occurs (try-finally)
 */
public class LinearAlgebraEngine {

    private SharedMatrix leftMatrix = new SharedMatrix();   // M1: Left operand & result storage
    private SharedMatrix rightMatrix = new SharedMatrix();  // M2: Right operand storage
    private TiredExecutor executor;                         // Thread pool for parallel execution

    /**
     * Creates a new LinearAlgebraEngine with the specified number of worker threads.
     * @param numThreads Number of worker threads in the pool (must be > 0)
     */
    public LinearAlgebraEngine(int numThreads) {
        this.executor = new TiredExecutor(numThreads);
    }

    /**
     * Executes the computation tree and returns the resolved root node.
     * 
     * Algorithm:
     * 1. If root is already a matrix, return immediately (no computation needed)
     * 2. Convert n-ary operations to binary using left-associative nesting
     * 3. Repeatedly find and resolve the deepest resolvable node until root is a matrix
     * 
     * @param computationRoot The root of the computation tree
     * @return The resolved root node containing the final result matrix
     * @throws IllegalArgumentException if computationRoot is null
     */
    public ComputationNode run(ComputationNode computationRoot) {
        if (computationRoot == null) throw new IllegalArgumentException("computationRoot is null");

        try {
            // If root is already a matrix, no computation needed
            // This handles the case where input is just a matrix literal
            if (computationRoot.getNodeType() == ComputationNodeType.MATRIX) {
                return computationRoot;
            }

            // Convert n-ary operations (e.g., A+B+C) into binary trees
            // Result: ((A+B)+C) - left-associative nesting
            computationRoot.associativeNesting();

            // Resolve the tree bottom-up
            // Each iteration finds the deepest node with all-matrix children,
            // computes it, and replaces it with the result matrix
            while (computationRoot.getNodeType() != ComputationNodeType.MATRIX) {
                ComputationNode node = computationRoot.findResolvable();
                if (node == null) {
                    throw new IllegalStateException("No resolvable node found but root is not a MATRIX");
                }

                // Load operands into shared matrices, create tasks, execute them
                loadAndCompute(node);

                // Read the result from M1 (leftMatrix) and resolve the node
                double[][] result = leftMatrix.readRowMajor();
                node.resolve(result);
            }

            return computationRoot;
        } finally {
            // CRITICAL: Always shutdown executor, even if an exception occurs
            // This prevents worker thread leaks
            try {
                executor.shutdown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Loads operands into shared matrices and executes the operation.
     * 
     * This method:
     * 1. Validates the node and its children
     * 2. Loads operand matrices into M1 (leftMatrix) and M2 (rightMatrix)
     * 3. Creates parallel tasks for the operation
     * 4. Submits tasks to executor and waits for completion
     * 
     * After this method returns, the result is stored in leftMatrix (M1).
     * 
     * @param node The computation node to execute (must have all MATRIX children)
     */
    public void loadAndCompute(ComputationNode node) {
        if (node == null) {
            throw new IllegalArgumentException("node is null");
        }
        
        ComputationNodeType nodeType = node.getNodeType();
        List<ComputationNode> children = node.getChildren();
        
        if (children == null || children.isEmpty()) {
            throw new IllegalStateException("Node has no children");
        }
        
        // All children must be resolved to matrices before we can compute
        for (ComputationNode child : children) {
            if (child.getNodeType() != ComputationNodeType.MATRIX) {
                throw new IllegalStateException("Child node is not a MATRIX");
            }
        }
        
        List<Runnable> tasks;
        
        if (nodeType == ComputationNodeType.ADD) {
            // Binary operation: requires exactly 2 operands after associativeNesting
            if (children.size() != 2) {
                throw new IllegalArgumentException("ADD requires exactly 2 operands");
            }
            double[][] leftAdd = children.get(0).getMatrix();
            double[][] rightAdd = children.get(1).getMatrix();
            
            // Dimension validation: matrices must have same dimensions for addition
            if ((leftAdd.length != rightAdd.length) || (leftAdd.length > 0 && leftAdd[0].length != rightAdd[0].length)) {
                throw new IllegalArgumentException("Matrix dimension mismatch for ADD");
            }
            
            // Load both matrices as row-major (each vector = one row)
            leftMatrix.loadRowMajor(leftAdd);
            rightMatrix.loadRowMajor(rightAdd);
            tasks = createAddTasks();
            
        } else if (nodeType == ComputationNodeType.MULTIPLY) {
            // Binary operation: requires exactly 2 operands after associativeNesting
            if (children.size() != 2) {
                throw new IllegalArgumentException("MULTIPLY requires exactly 2 operands");
            }
            double[][] leftMul = children.get(0).getMatrix();
            double[][] rightMul = children.get(1).getMatrix();
            
            // Dimension validation: left columns must equal right rows
            int leftCols = 0;
            if (leftMul.length > 0) {
                leftCols = leftMul[0].length;
            }
            int rightRows = rightMul.length;
            
            if (leftCols != rightRows) {
                throw new IllegalArgumentException("Matrix dimension mismatch for MULTIPLY");
            }
            
            // Left matrix: row-major (rows for dot products)
            // Right matrix: column-major (columns for efficient dot products)
            leftMatrix.loadRowMajor(leftMul);
            rightMatrix.loadColumnMajor(rightMul);
            tasks = createMultiplyTasks();
            
        } else if (nodeType == ComputationNodeType.NEGATE) {
            // Unary operation: exactly 1 operand
            if (children.size() != 1) {
                throw new IllegalArgumentException("NEGATE requires exactly 1 operand");
            }
            double[][] matrixNeg = children.get(0).getMatrix();
            leftMatrix.loadRowMajor(matrixNeg);
            tasks = createNegateTasks();
            
        } else if (nodeType == ComputationNodeType.TRANSPOSE) {
            // Unary operation: exactly 1 operand
            if (children.size() != 1) {
                throw new IllegalArgumentException("TRANSPOSE requires exactly 1 operand");
            }
            double[][] matrixTrans = children.get(0).getMatrix();
            leftMatrix.loadRowMajor(matrixTrans);
            tasks = createTransposeTasks();
            
        } else {
            throw new IllegalStateException("Unexpected node type: " + nodeType);
        }
        
        // Submit all tasks and block until all complete
        executor.submitAll(tasks);
    }

    /**
     * Creates tasks for matrix addition.
     * Each task adds one row from rightMatrix to the corresponding row in leftMatrix.
     * Tasks can run in parallel since each operates on a different row.
     * Result: leftMatrix[i] = leftMatrix[i] + rightMatrix[i]
     */
    public List<Runnable> createAddTasks() {
        int numRows = leftMatrix.length();
        List<Runnable> tasks = new ArrayList<>();
        
        for (int i = 0; i < numRows; i++) {
            final int rowIndex = i;
            tasks.add(() -> {
                SharedVector leftRow = leftMatrix.get(rowIndex);
                SharedVector rightRow = rightMatrix.get(rowIndex);
                // SharedVector.add() handles its own locking
                leftRow.add(rightRow);
            });
        }
        
        return tasks;
    }

    /**
     * Creates tasks for matrix multiplication.
     * Each task computes one row of the result using vector-matrix multiplication.
     * rightMatrix must be column-major for efficient dot product computation.
     * Result: leftMatrix[i] = leftMatrix[i] * rightMatrix (row × matrix)
     */
    public List<Runnable> createMultiplyTasks() {
        int numRows = leftMatrix.length();
        List<Runnable> tasks = new ArrayList<>();
        
        for (int i = 0; i < numRows; i++) {
            final int rowIndex = i;
            tasks.add(() -> {
                SharedVector row = leftMatrix.get(rowIndex);
                // vecMatMul computes row × matrix and stores result back in row
                row.vecMatMul(rightMatrix);
            });
        }
        
        return tasks;
    }

    /**
     * Creates tasks for matrix negation.
     * Each task negates one row of the matrix.
     * Result: leftMatrix[i] = -leftMatrix[i]
     */
    public List<Runnable> createNegateTasks() {
        int numRows = leftMatrix.length();
        List<Runnable> tasks = new ArrayList<>();
        
        for (int i = 0; i < numRows; i++) {
            final int rowIndex = i;
            tasks.add(() -> {
                SharedVector row = leftMatrix.get(rowIndex);
                row.negate();
            });
        }
        
        return tasks;
    }

    /**
     * Creates tasks for matrix transpose.
     * 
     * Algorithm:
     * 1. Each task reads one column from the input (= one row of output)
     * 2. Tasks write to separate rows of the output buffer (no conflicts)
     * 3. The LAST task to finish loads the result into leftMatrix
     * 
     * Deadlock Prevention:
     * - No task waits for other tasks (would deadlock with numThreads=1)
     * - Instead, tasks use a counter to determine who loads the final result
     * - synchronized block protects the counter and ensures exactly one task loads
     */
    public List<Runnable> createTransposeTasks() {
        int inputRows = leftMatrix.length();
        if (inputRows == 0) {
            return new ArrayList<>();
        }
        
        int inputCols = leftMatrix.get(0).length();
        
        // Capture references to input vectors before any modifications
        // This is important because leftMatrix will be overwritten with the result
        final SharedVector[] inputVectors = new SharedVector[inputRows];
        for (int i = 0; i < inputRows; i++) {
            inputVectors[i] = leftMatrix.get(i);
        }
        
        // Output buffer: transposed[col][row] = input[row][col]
        // Each task writes to its own row - no synchronization needed for writes
        final double[][] transposed = new double[inputCols][inputRows];
        
        // Counter to track remaining tasks
        // The last task to finish (remaining[0] == 0) loads the result
        final int[] remaining = {inputCols};
        final Object lock = new Object();
        
        List<Runnable> tasks = new ArrayList<>();
        
        // Create one task per output row (= input column)
        for (int outputRow = 0; outputRow < inputCols; outputRow++) {
            final int col = outputRow;
            tasks.add(() -> {
                // Read column 'col' from input, write to row 'col' of output
                // No conflicts: each task writes to different row
                for (int i = 0; i < inputRows; i++) {
                    transposed[col][i] = inputVectors[i].get(col);
                }
                
                // Atomically decrement counter and check if we're the last task
                synchronized (lock) {
                    remaining[0]--;
                    if (remaining[0] == 0) {
                        // We're the last task - load the transposed result into M1
                        // This is safe because all other tasks have completed their writes
                        leftMatrix.loadRowMajor(transposed);
                    }
                }
            });
        }
        
        return tasks;
    }

    /**
     * Returns a report of worker thread statistics.
     * Useful for debugging and analyzing work distribution.
     */
    public String getWorkerReport() {
        return executor.getWorkerReport();
    }
}
