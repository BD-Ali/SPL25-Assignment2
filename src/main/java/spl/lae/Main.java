package spl.lae;

import java.io.IOException;

import parser.*;

/**
 * Main entry point for the Linear Algebra Engine (LAE) application.
 * 
 * Responsibilities:
 * - Parse and validate command-line arguments (numThreads, inputFile, outputFile)
 * - Initialize the LinearAlgebraEngine with the specified thread count
 * - Execute the computation tree parsed from the input JSON
 * - Write the result matrix or error message to the output JSON file
 * 
 * Error Handling:
 * - Any error during execution results in an error JSON being written
 * - The application never crashes - all exceptions are caught and reported
 */
public class Main {
    public static void main(String[] args) throws IOException {
        // 1) Validate command-line arguments - must be exactly 3:
        //    args[0] = number of worker threads
        //    args[1] = input JSON file path
        //    args[2] = output JSON file path
        if (args.length != 3) {
            // No output file specified, so write error to default "error.json"
            try {
                OutputWriter.write("usage: <threads> <input.json> <output.json>", "error.json");
            } catch (IOException ignored) {
            }
            return;
        }

        String outputFile = args[2];

        try {
            // 2) Parse numThreads - must be a positive integer
            int numThreads;
            try {
                numThreads = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                OutputWriter.write("Invalid number of threads: " + args[0], outputFile);
                return;
            }

            if (numThreads <= 0) {
                OutputWriter.write("Number of threads must be positive", outputFile);
                return;
            }

            String inputFile = args[1];

            // 3) Parse input JSON into a computation tree
            //    The tree consists of ComputationNode objects representing
            //    operators (ADD, MULTIPLY, NEGATE, TRANSPOSE) and matrices
            InputParser parser = new InputParser();
            ComputationNode root = parser.parse(inputFile);

            // 4) Create the engine and run the computation
            //    The engine will resolve the tree bottom-up, executing
            //    each operation using the thread pool
            LinearAlgebraEngine engine = new LinearAlgebraEngine(numThreads);
            ComputationNode resultRoot = engine.run(root);

            // 5) Write the final result matrix to the output file
            //    At this point, resultRoot is guaranteed to be a MATRIX node
            double[][] result = resultRoot.getMatrix();
            OutputWriter.write(result, outputFile);

        } catch (Exception e) {
            // 6) Catch ANY exception and write error JSON
            //    This ensures the application never crashes without output
            String errorMessage = e.getMessage();
            if (errorMessage == null) {
                // Some exceptions have null messages, use toString as fallback
                errorMessage = e.toString();
            }
            try {
                OutputWriter.write(errorMessage, outputFile);
            } catch (IOException ignored) {
                // If we can't write the error, there's nothing more we can do
            }
        }
    }
}