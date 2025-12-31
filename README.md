# Linear Algebra Engine (LAE)

A multi-threaded Linear Algebra Engine that performs matrix computations using a custom thread pool with fatigue-based scheduling. This project demonstrates advanced concurrency control, thread synchronization, and efficient parallel task execution.

## Overview

The LAE parses JSON-formatted mathematical expressions involving matrices and evaluates them using parallel computation. It breaks down operations into vector-level tasks distributed across worker threads, with intelligent workload balancing based on thread fatigue.

## Features

- **Supported Operations:**
  - Matrix Addition (`+`)
  - Matrix Multiplication (`*`)
  - Matrix Negation (`-`)
  - Matrix Transpose (`T`)

- **Concurrency Features:**
  - Custom thread pool with fatigue-based scheduling
  - Fine-grained locking using `ReadWriteLock` on vectors
  - Deadlock prevention through consistent lock ordering
  - Thread-safe shared memory management

- **Performance Optimization:**
  - Vector-level task granularity for maximum parallelism
  - Least-fatigued-first scheduling for fair work distribution
  - Row-major and column-major storage for efficient operations

## Requirements

- **Java:** 21 or compatible version
- **Maven:** 3.x or higher
- **OS:** Linux, macOS, or Windows with bash support

## Project Structure

```
/workspace
├── src/
│   ├── main/java/
│   │   ├── memory/
│   │   │   ├── SharedMatrix.java      # Thread-safe matrix storage
│   │   │   ├── SharedVector.java      # Thread-safe vector with ReadWriteLock
│   │   │   └── VectorOrientation.java # ROW_MAJOR / COLUMN_MAJOR enum
│   │   ├── parser/
│   │   │   ├── ComputationNode.java   # Computation tree node
│   │   │   ├── InputParser.java       # JSON parser
│   │   │   └── OutputWriter.java      # JSON writer
│   │   ├── scheduling/
│   │   │   ├── TiredExecutor.java     # Thread pool with fatigue scheduling
│   │   │   └── TiredThread.java       # Worker thread with fatigue tracking
│   │   └── spl/lae/
│   │       ├── Main.java              # Application entry point
│   │       └── LinearAlgebraEngine.java # Computation orchestrator
│   └── test/java/                     # Unit tests
├── Examples/                          # Example input/output files
├── pom.xml                           # Maven configuration
└── README.md
```

## Building the Project

```bash
# Compile the project
mvn compile

# Run tests
mvn test

# Package as JAR
mvn package
```

The compiled JAR will be located at: `target/lga-1.0.jar`

## Running the Application

### Basic Usage

```bash
java -jar target/lga-1.0.jar <numThreads> <inputFile> <outputFile>
```

### Parameters

1. **numThreads**: Number of worker threads (must be positive integer)
2. **inputFile**: Path to input JSON file
3. **outputFile**: Path to output JSON file

### Examples

```bash
# Run with 4 threads
java -jar target/lga-1.0.jar 4 Examples/example1.json output.json

# Run with 10 threads
java -jar target/lga-1.0.jar 10 Examples/example2.json result.json
```

### Using Maven exec

```bash
mvn exec:java -Dexec.mainClass="spl.lae.Main" -Dexec.args="4 Examples/example1.json output.json"
```

## Input Format

Input files are JSON documents with nested operations:

```json
{
  "operator": "+",
  "operands": [
    [
      [1, 2],
      [3, 4]
    ],
    [
      [5, 6],
      [7, 8]
    ]
  ]
}
```

### Nested Operations

```json
{
  "operator": "*",
  "operands": [
    {
      "operator": "+",
      "operands": [
        [[1, 2], [3, 4]],
        [[5, 6], [7, 8]]
      ]
    },
    [[2, 0], [0, 2]]
  ]
}
```

## Output Format

### Success

```json
{
  "result": [
    [6, 8],
    [10, 12]
  ]
}
```

### Error

```json
{
  "error": "Matrix dimension mismatch for MULTIPLY"
}
```

## Implementation Details

### Thread Safety

- **SharedVector:** Uses `ReentrantReadWriteLock` for all operations
  - Multiple concurrent readers allowed
  - Exclusive write access
  - Consistent lock ordering to prevent deadlocks

- **SharedMatrix:** No internal locking (per assignment requirements)
  - Relies on SharedVector locks for thread safety
  - Uses `volatile` for visibility of vector array reference
  - Atomic reference assignment for safe publication

### Scheduling Algorithm

1. **TiredExecutor** maintains a `PriorityBlockingQueue` of idle workers
2. Workers ordered by fatigue (least-fatigued first)
3. Each task assigned to the least-fatigued available worker
4. Worker fatigue = `fatigueFactor × timeUsed`
5. Automatic rebalancing as workers complete tasks

### Concurrency Primitives

- **Monitor-based synchronization:** `synchronized`, `wait()`, `notifyAll()`
- **ReadWriteLock:** Fine-grained vector-level locking
- **Atomic operations:** `AtomicInteger`, `AtomicLong`, `AtomicBoolean`
- **Volatile:** Memory visibility for shared state

## Testing

```bash
# Run all unit tests
mvn test

# Run specific test class
mvn test -Dtest=SharedVectorTest

# Run with verbose output
mvn test -X
```

### Test Coverage

- ✅ SharedVector operations (add, negate, dot product, transpose)
- ✅ SharedMatrix storage and retrieval
- ✅ TiredThread fatigue tracking
- ✅ TiredExecutor task distribution
- ✅ LinearAlgebraEngine computation flow
- ✅ End-to-end integration tests

### Example Validation Script

```bash
./scripts/run_examples_and_compare.sh
```

This script runs all examples and compares outputs against expected results.

## Performance Considerations

### Optimization Strategies

1. **Task Granularity:** Operations decomposed at vector level (rows/columns)
2. **Lock Ordering:** Consistent ordering by `System.identityHashCode()` prevents deadlocks
3. **Read-Write Separation:** Multiple readers can access vectors simultaneously
4. **Column-Major Storage:** Matrix multiplication uses column-major format for efficient dot products
5. **Fatigue Balancing:** Naturally distributes work across threads over time

### Scalability

- Linear speedup expected for large matrices with sufficient parallelism
- Overhead minimal for small matrices (< 10x10)
- Optimal thread count: typically `numCPUCores` to `2 × numCPUCores`

## Error Handling

The application handles all errors gracefully:

- **Invalid arguments:** Usage message written to error.json
- **Dimension mismatches:** Error written to output file
- **Parse errors:** Exception message written to output file
- **Runtime exceptions:** Caught and reported in JSON format

No crashes or unhandled exceptions occur.

## Assignment Compliance

This implementation satisfies all requirements:

- ✅ SharedMatrix uses no synchronization primitives
- ✅ SharedVector uses `ReentrantReadWriteLock` exclusively
- ✅ Thread pool uses only monitor primitives (synchronized/wait/notify)
- ✅ Fatigue-based scheduling with PriorityBlockingQueue
- ✅ All operations write results to leftMatrix (M1)
- ✅ Proper error handling with JSON output format
- ✅ Left-associative nesting for n-ary operations
- ✅ Dimension validation before all operations
- ✅ Comprehensive unit test coverage

## Known Limitations

- Empty matrices default to ROW_MAJOR orientation
- No support for scalar operations (scalar × matrix)
- Error messages may vary in format depending on exception type

## Troubleshooting

### Common Issues

1. **"Number of threads must be positive"**
   - Ensure first argument is a positive integer

2. **"Matrix dimension mismatch"**
   - Check that matrix dimensions are compatible for the operation
   - Addition: both matrices must have same dimensions
   - Multiplication: left columns must equal right rows

3. **Tests failing**
   - Ensure Java 21 is installed: `java -version`
   - Clean and rebuild: `mvn clean compile test`

4. **OutOfMemoryError**
   - Reduce number of threads or matrix size
   - Increase JVM heap: `java -Xmx2g -jar target/lga-1.0.jar ...`

## Development

### Adding New Operations

1. Add operator to `ComputationNodeType` enum
2. Update `ComputationNode.mapOperator()` parser
3. Implement task creation in `LinearAlgebraEngine.createXXXTasks()`
4. Add validation in `loadAndCompute()`
5. Write unit tests

### Running in Debug Mode

```bash
mvn exec:java -Dexec.mainClass="spl.lae.Main" -Dexec.args="4 input.json output.json" -Dexec.classpathScope=compile
```

## License

This project is an academic assignment for the Systems Programming Laboratory course at Ben-Gurion University, 2026-1.

## Author

Ali Badarne

---

**Last Updated:** December 31, 2025
