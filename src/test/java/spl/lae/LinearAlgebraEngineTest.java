package spl.lae;

import org.junit.jupiter.api.Test;
import parser.ComputationNode;
import parser.ComputationNodeType;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LinearAlgebraEngineTest {

    private static void assertMatrixEquals(double[][] expected, double[][] actual) {
        assertEquals(expected.length, actual.length, "row count mismatch");
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i].length, actual[i].length, "col count mismatch at row " + i);
            for (int j = 0; j < expected[i].length; j++) {
                assertEquals(expected[i][j], actual[i][j], 1e-9, "mismatch at (" + i + "," + j + ")");
            }
        }
    }

    @Test
    void run_matrixLiteral_returnsAsIs() {
        double[][] m = {{1, 2}, {3, 4}};
        ComputationNode root = new ComputationNode(m);

        LinearAlgebraEngine lae = new LinearAlgebraEngine(2);
        ComputationNode out = lae.run(root);

        assertEquals(ComputationNodeType.MATRIX, out.getNodeType());
        assertMatrixEquals(m, out.getMatrix());
    }

    @Test
    void run_add_correct() {
        ComputationNode a = new ComputationNode(new double[][]{{1, 2}, {3, 4}});
        ComputationNode b = new ComputationNode(new double[][]{{10, 20}, {30, 40}});

        ComputationNode root = new ComputationNode(ComputationNodeType.ADD, List.of(a, b));

        LinearAlgebraEngine lae = new LinearAlgebraEngine(3);
        ComputationNode out = lae.run(root);

        assertMatrixEquals(new double[][]{{11, 22}, {33, 44}}, out.getMatrix());
    }

    @Test
    void run_multiply_correct() {
        // [[1,2,3],[4,5,6]] * [[7,8],[9,10],[11,12]]
        // = [[58,64],[139,154]]
        ComputationNode left = new ComputationNode(new double[][]{{1, 2, 3}, {4, 5, 6}});
        ComputationNode right = new ComputationNode(new double[][]{{7, 8}, {9, 10}, {11, 12}});
        ComputationNode root = new ComputationNode(ComputationNodeType.MULTIPLY, List.of(left, right));

        LinearAlgebraEngine lae = new LinearAlgebraEngine(4);
        ComputationNode out = lae.run(root);

        assertMatrixEquals(new double[][]{{58, 64}, {139, 154}}, out.getMatrix());
    }

    @Test
    void run_negate_correct() {
        ComputationNode a = new ComputationNode(new double[][]{{1, -2}, {3, 0}});
        ComputationNode root = new ComputationNode(ComputationNodeType.NEGATE, List.of(a));

        LinearAlgebraEngine lae = new LinearAlgebraEngine(2);
        ComputationNode out = lae.run(root);

        assertMatrixEquals(new double[][]{{-1, 2}, {-3, -0}}, out.getMatrix());
    }

    @Test
    void run_transpose_correct() {
        ComputationNode a = new ComputationNode(new double[][]{{1, 2, 3}, {4, 5, 6}});
        ComputationNode root = new ComputationNode(ComputationNodeType.TRANSPOSE, List.of(a));

        LinearAlgebraEngine lae = new LinearAlgebraEngine(2);
        ComputationNode out = lae.run(root);

        assertMatrixEquals(new double[][]{{1, 4}, {2, 5}, {3, 6}}, out.getMatrix());
    }

    @Test
    void run_nAryAdd_isLeftAssociative() {
        // ((A + B) + C)
        ComputationNode a = new ComputationNode(new double[][]{{1, 1}, {1, 1}});
        ComputationNode b = new ComputationNode(new double[][]{{2, 2}, {2, 2}});
        ComputationNode c = new ComputationNode(new double[][]{{3, 3}, {3, 3}});

        ComputationNode root = new ComputationNode(ComputationNodeType.ADD, new ArrayList<>(List.of(a, b, c)));

        LinearAlgebraEngine lae = new LinearAlgebraEngine(3);
        ComputationNode out = lae.run(root);

        assertMatrixEquals(new double[][]{{6, 6}, {6, 6}}, out.getMatrix());
    }

    @Test
    void run_add_dimensionMismatch_throws() {
        ComputationNode a = new ComputationNode(new double[][]{{1, 2}});
        ComputationNode b = new ComputationNode(new double[][]{{1, 2}, {3, 4}});

        ComputationNode root = new ComputationNode(ComputationNodeType.ADD, List.of(a, b));

        LinearAlgebraEngine lae = new LinearAlgebraEngine(2);
        assertThrows(IllegalArgumentException.class, () -> lae.run(root));
    }

    @Test
    void run_multiply_dimensionMismatch_throws() {
        ComputationNode left = new ComputationNode(new double[][]{{1, 2, 3}});  // 1x3
        ComputationNode right = new ComputationNode(new double[][]{{1, 2}, {3, 4}}); // 2x2, mismatch (3 != 2)

        ComputationNode root = new ComputationNode(ComputationNodeType.MULTIPLY, List.of(left, right));

        LinearAlgebraEngine lae = new LinearAlgebraEngine(2);
        assertThrows(IllegalArgumentException.class, () -> lae.run(root));
    }

    @Test
    void run_negate_wrongArity_throws() {
        ComputationNode a = new ComputationNode(new double[][]{{1}});
        ComputationNode b = new ComputationNode(new double[][]{{2}});
        ComputationNode root = new ComputationNode(ComputationNodeType.NEGATE, List.of(a, b));

        LinearAlgebraEngine lae = new LinearAlgebraEngine(2);
        assertThrows(IllegalArgumentException.class, () -> lae.run(root));
    }
}
