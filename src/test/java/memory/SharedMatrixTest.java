package memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SharedMatrixTest {

    @Test
    void loadRowMajor_clonesInputRows() {
        double[][] input = {
                {1, 2},
                {3, 4}
        };

        SharedMatrix m = new SharedMatrix();
        m.loadRowMajor(input);

        input[0][0] = 999; // mutate original

        double[][] out = m.readRowMajor();
        assertEquals(1, out[0][0], 1e-9);
        assertEquals(2, out[0][1], 1e-9);
        assertEquals(3, out[1][0], 1e-9);
        assertEquals(4, out[1][1], 1e-9);
    }

    @Test
    void loadRowMajor_setsRowMajorVectors() {
        double[][] input = {
                {1, 2, 3},
                {4, 5, 6}
        };

        SharedMatrix m = new SharedMatrix();
        m.loadRowMajor(input);

        assertEquals(VectorOrientation.ROW_MAJOR, m.getOrientation());
        assertEquals(2, m.length());
        assertEquals(VectorOrientation.ROW_MAJOR, m.get(0).getOrientation());
        assertEquals(3, m.get(0).length());
    }

    @Test
    void loadColumnMajor_storesColumnsCorrectly() {
        double[][] input = {
                {1, 2, 3},
                {4, 5, 6}
        };

        SharedMatrix m = new SharedMatrix();
        m.loadColumnMajor(input);

        assertEquals(VectorOrientation.COLUMN_MAJOR, m.getOrientation());
        assertEquals(3, m.length());

        SharedVector col0 = m.get(0);
        assertEquals(VectorOrientation.COLUMN_MAJOR, col0.getOrientation());
        assertEquals(2, col0.length());
        assertEquals(1, col0.get(0), 1e-9);
        assertEquals(4, col0.get(1), 1e-9);

        SharedVector col2 = m.get(2);
        assertEquals(3, col2.get(0), 1e-9);
        assertEquals(6, col2.get(1), 1e-9);
    }

    @Test
    void readRowMajor_returnsSameWhenStoredRowMajor() {
        double[][] input = {
                {7, 8},
                {9, 10}
        };

        SharedMatrix m = new SharedMatrix();
        m.loadRowMajor(input);

        double[][] out = m.readRowMajor();

        assertArrayEquals(new double[]{7, 8}, out[0], 1e-9);
        assertArrayEquals(new double[]{9, 10}, out[1], 1e-9);
    }

    @Test
    void readRowMajor_transposesWhenStoredColumnMajor() {
        double[][] input = {
                {1, 2, 3},
                {4, 5, 6}
        };

        SharedMatrix m = new SharedMatrix();
        m.loadColumnMajor(input);

        double[][] out = m.readRowMajor();

        assertEquals(2, out.length);
        assertEquals(3, out[0].length);
        assertArrayEquals(new double[]{1, 2, 3}, out[0], 1e-9);
        assertArrayEquals(new double[]{4, 5, 6}, out[1], 1e-9);
    }

    @Test
    void loadColumnMajor_emptyMatrix_safe() {
        SharedMatrix m = new SharedMatrix();
        m.loadColumnMajor(new double[0][0]);

        // For empty matrix, orientation doesn't matter functionally
        // The implementation returns ROW_MAJOR as default when no vectors exist
        assertEquals(0, m.length());
        assertNotNull(m.readRowMajor());
    }
}
