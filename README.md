# SPL Assignment 2 - Linear Algebra Engine (LAE)

**Submission Date:** December 31, 2025

## Student Information

- **Student 1:** Ali Badarne, ID: 326107687
- **Student 2:** Najwan Drawshe, ID: 215191297

---

## Overview

The **Linear Algebra Engine (LAE)** is a multi-threaded Java application that evaluates linear algebra computations expressed as JSON-based expression trees. It supports matrix operations including addition, multiplication, negation, and transposition, with parallel execution using a custom thread pool with fatigue-based scheduling.

## Features

- **Matrix Operations**: Addition (`+`), Multiplication (`*`), Negation (`-`), Transpose (`T`)
- **Parallel Execution**: Custom thread pool with configurable worker threads
- **Fatigue-Based Scheduling**: Workers are assigned tasks based on their current fatigue level (least tired worker gets the next task)
- **Thread-Safe Memory**: Shared matrix and vector structures with proper locking mechanisms
- **JSON I/O**: Input computations and output results in JSON format
- **Robust Error Handling**: All errors are captured and written to the output file

---

## Project Structure

```
├── pom.xml                          # Maven build configuration
├── README.md                        # This file
├── Examples/                        # Sample input/output JSON files
│   ├── example.json ... example6.json   # Input computation trees
│   └── out.json ... out6.json           # Expected output results
├── scripts/
│   └── run_examples_and_compare.sh  # Test script to run examples
└── src/
    ├── main/java/
    │   ├── spl/lae/
    │   │   ├── Main.java                # Application entry point
    │   │   └── LinearAlgebraEngine.java # Core computation orchestrator
    │   ├── parser/
    │   │   ├── InputParser.java         # JSON input parser
    │   │   ├── OutputWriter.java        # JSON output writer
    │   │   ├── ComputationNode.java     # Expression tree node
    │   │   └── ComputationNodeType.java # Node type enum
    │   ├── memory/
    │   │   ├── SharedMatrix.java        # Thread-safe matrix storage
    │   │   ├── SharedVector.java        # Thread-safe vector with R/W locks
    │   │   └── VectorOrientation.java   # Row/column major enum
    │   └── scheduling/
    │       ├── TiredExecutor.java       # Thread pool with fatigue scheduling
    │       └── TiredThread.java         # Worker thread with fatigue tracking
    └── test/java/                       # JUnit 5 test classes
```

---

## Requirements

- **Java 21** or later
- **Maven 3.6+** (for building)

---

## Building the Project

```bash
# Compile the project
mvn compile

# Run tests
mvn test

# Package as JAR (creates shaded JAR with all dependencies)
mvn package
```

---

## Usage

### Running the Application

```bash
java -jar target/lga-1.0.jar <threads> <input.json> <output.json>
```

**Arguments:**
| Argument | Description |
|----------|-------------|
| `threads` | Number of worker threads (positive integer) |
| `input.json` | Path to input JSON file with computation tree |
| `output.json` | Path to output JSON file for results |

### Example

```bash
java -jar target/lga-1.0.jar 4 Examples/example1.json result.json
```

---

## Input Format

The input is a JSON file representing a computation tree. Nodes can be:

### Matrix (Leaf Node)
A 2D array of numbers:
```json
[
  [1, 2, 3],
  [4, 5, 6]
]
```

### Operation Node
An object with `operator` and `operands`:
```json
{
  "operator": "+",
  "operands": [
    [[1, 2], [3, 4]],
    [[5, 6], [7, 8]]
  ]
}
```

### Supported Operators

| Operator | Description | Operands |
|----------|-------------|----------|
| `+` | Matrix Addition | 2+ matrices (same dimensions) |
| `*` | Matrix Multiplication | 2+ matrices (compatible dimensions) |
| `-` | Matrix Negation | 1 matrix |
| `T` | Matrix Transpose | 1 matrix |

### Example Input (Nested Operations)
```json
{
  "operator": "+",
  "operands": [
    {
      "operator": "*",
      "operands": [
        [[1, 2, 3], [4, 5, 6]],
        {
          "operator": "-",
          "operands": [
            [[7, 8], [9, 10], [11, 12]]
          ]
        }
      ]
    },
    [[13, 14], [15, 16]],
    {
      "operator": "T",
      "operands": [
        [[17, 18], [19, 20]]
      ]
    }
  ]
}
```

---

## Output Format

### Successful Result
```json
{
  "result": [
    [1.0, 2.0, 3.0],
    [4.0, 5.0, 6.0]
  ]
}
```

### Error Result
```json
{
  "error": "Matrix dimension mismatch for ADD"
}
```

---

## Architecture

### Core Components

#### 1. LinearAlgebraEngine
The main orchestrator that:
- Parses the computation tree from input
- Converts n-ary operations to binary using left-associative nesting (e.g., `A+B+C` → `(A+B)+C`)
- Iteratively finds and resolves the deepest resolvable node
- Coordinates parallel task execution through the thread pool

#### 2. TiredExecutor (Thread Pool)
A custom thread pool with fatigue-based scheduling:
- Maintains a priority queue of workers ordered by fatigue (min-heap)
- Always assigns tasks to the least fatigued worker
- Workers accumulate fatigue based on execution time
- Supports blocking until all submitted tasks complete

#### 3. TiredThread (Worker)
Individual worker threads that:
- Track their own fatigue level: `fatigue = fatigueFactor × timeUsed`
- Have random fatigue factors (0.5 to 1.5) for varied work distribution
- Use a poison pill pattern for graceful shutdown

#### 4. SharedMatrix & SharedVector
Thread-safe data structures:
- `SharedMatrix`: Stores vectors in row-major or column-major format
- `SharedVector`: Uses `ReentrantReadWriteLock` for concurrent access
- Lock ordering prevents deadlocks in multi-vector operations

### Parallel Execution Strategy

Each matrix operation is decomposed into row-level tasks:

| Operation | Parallelization |
|-----------|-----------------|
| **Add** | Each task adds one row from matrix B to matrix A |
| **Multiply** | Each task computes one row of the result via row × matrix |
| **Negate** | Each task negates one row |
| **Transpose** | Each task reads one column, writes one row of result |

---

## Running Examples

A test script is provided to run all examples and compare outputs:

```bash
./scripts/run_examples_and_compare.sh [threads]
```

This script:
1. Compiles the project (if Maven is available)
2. Runs each `Examples/example*.json` file
3. Compares outputs with expected `Examples/out*.json` files
4. Reports matches and differences

---

## Testing

The project includes comprehensive JUnit 5 tests:

```bash
# Run all tests
mvn test

# Run a specific test class
mvn test -Dtest=LinearAlgebraEngineTest
```

### Test Coverage
- `SharedMatrixTest` - Matrix loading and reading
- `SharedVectorTest` - Vector operations and thread safety
- `TiredExecutorTest` - Thread pool behavior
- `TiredThreadTest` - Worker thread functionality
- `LinearAlgebraEngineTest` - Core engine operations
- `MainTest` - End-to-end application tests

---

## Error Handling

The application handles errors gracefully:

- **Invalid Arguments**: Writes usage message to `error.json`
- **Invalid Thread Count**: Writes error to output file
- **Parse Errors**: Invalid JSON or structure errors
- **Dimension Mismatch**: Matrix operations with incompatible dimensions
- **All Other Exceptions**: Captured and written to output file

The application never crashes without producing output.

---

## Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Jackson Databind | 2.17.1 | JSON parsing and serialization |
| JUnit Jupiter | 5.10.2 | Unit testing (test scope) |

---

## License

This project was developed as part of the Systems Programming Lab course assignment.
