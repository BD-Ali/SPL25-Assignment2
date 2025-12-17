package memory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SharedMatrixTest {

    @Test
    void newMatrixIsEmpty() {
        SharedMatrix m = new SharedMatrix();
        assertEquals(0, m.length());
    }

    @Test
    void loadRowMajorAndReadRowMajorRoundTrip() {
        SharedMatrix m = new SharedMatrix();
        double[][] a = { {1,2}, {3,4} };
        m.loadRowMajor(a);

        double[][] out = m.readRowMajor();
        assertArrayEquals(new double[]{1,2}, out[0]);
        assertArrayEquals(new double[]{3,4}, out[1]);
        assertEquals(2, m.length());
        assertEquals(VectorOrientation.ROW_MAJOR, m.getOrientation());
    }

    @Test
    void loadColumnMajorAndReadRowMajorMatchesLogicalMatrix() {
        SharedMatrix m = new SharedMatrix();
        double[][] a = { {1,2}, {3,4} };
        m.loadColumnMajor(a);

        // still must read as a normal row-major matrix
        double[][] out = m.readRowMajor();
        assertArrayEquals(new double[]{1,2}, out[0]);
        assertArrayEquals(new double[]{3,4}, out[1]);
        assertEquals(VectorOrientation.COLUMN_MAJOR, m.getOrientation());
    }

    @Test
    void getReturnsVectorAtIndex() {
        SharedMatrix m = new SharedMatrix();
        m.loadRowMajor(new double[][]{{1,2},{3,4}});
        SharedVector v0 = m.get(0);
        assertNotNull(v0);
        assertEquals(2, v0.length());
        assertEquals(1.0, v0.get(0));
    }

    @Test
    void emptyConstructorCreatesEmptyMatrix() {
        SharedMatrix m = new SharedMatrix();
        assertEquals(0, m.length());
    }

    @Test
    void loadRowMajorRejectsNull() {
        SharedMatrix m = new SharedMatrix();
        assertThrows(IllegalArgumentException.class, () -> m.loadRowMajor(null));
    }

    @Test
    void loadColumnMajorRejectsNull() {
        SharedMatrix m = new SharedMatrix();
        assertThrows(IllegalArgumentException.class, () -> m.loadColumnMajor(null));
    }

    @Test
    void loadRejectsRaggedMatrix() {
        SharedMatrix m = new SharedMatrix();
        double[][] ragged = new double[][]{
                {1,2,3},
                {4,5}     // shorter row => illegal
        };
        assertThrows(IllegalArgumentException.class, () -> m.loadRowMajor(ragged));
        assertThrows(IllegalArgumentException.class, () -> m.loadColumnMajor(ragged));
    }

    @Test
    void loadAcceptsEmptyMatrixAndReadReturnsEmpty() {
        SharedMatrix m = new SharedMatrix();
        m.loadRowMajor(new double[][]{});
        assertEquals(0, m.length());
        assertArrayEquals(new double[][]{}, m.readRowMajor());
    }

    @Test
    void loadRowMajorRoundTripPreservesValues() {
        SharedMatrix m = new SharedMatrix();
        double[][] a = new double[][]{
                {1,2},
                {3,4}
        };
        m.loadRowMajor(a);
        assertEquals(VectorOrientation.ROW_MAJOR, m.getOrientation());

        double[][] out = m.readRowMajor();
        assertArrayEquals(new double[]{1,2}, out[0]);
        assertArrayEquals(new double[]{3,4}, out[1]);
    }

    @Test
    void loadColumnMajorStillReadsAsSameLogicalRowMajorMatrix() {
        SharedMatrix m = new SharedMatrix();
        double[][] a = new double[][]{
                {1,2},
                {3,4}
        };
        m.loadColumnMajor(a);
        assertEquals(VectorOrientation.COLUMN_MAJOR, m.getOrientation());

        // readRowMajor should return the logical matrix, not the internal layout
        double[][] out = m.readRowMajor();
        assertArrayEquals(new double[]{1,2}, out[0]);
        assertArrayEquals(new double[]{3,4}, out[1]);
    }


    @Test
    void getReturnsVectorsConsistentWithOrientation() {
        SharedMatrix m = new SharedMatrix();
        double[][] a = new double[][]{
                {1,2,3},
                {4,5,6}
        };

        // Row-major: vectors are rows
        m.loadRowMajor(a);
        assertEquals(2, m.length());
        assertEquals(VectorOrientation.ROW_MAJOR, m.getOrientation());
        assertArrayEquals(new double[]{1,2,3},
                new double[]{m.get(0).get(0), m.get(0).get(1), m.get(0).get(2)});

        // Column-major: vectors are columns
        m.loadColumnMajor(a);
        assertEquals(3, m.length());
        assertEquals(VectorOrientation.COLUMN_MAJOR, m.getOrientation());
        assertArrayEquals(new double[]{1,4},
                new double[]{m.get(0).get(0), m.get(0).get(1)});
        assertArrayEquals(new double[]{2,5},
                new double[]{m.get(1).get(0), m.get(1).get(1)});
        assertArrayEquals(new double[]{3,6},
                new double[]{m.get(2).get(0), m.get(2).get(1)});
    }

    @Test
    void readRowMajorReturnsDefensiveCopyNotAlias() {
        SharedMatrix m = new SharedMatrix();
        double[][] a = new double[][]{{1,2},{3,4}};
        m.loadRowMajor(a);

        double[][] out1 = m.readRowMajor();
        out1[0][0] = 999;

        double[][] out2 = m.readRowMajor();
        assertEquals(1.0, out2[0][0], "readRowMajor should not expose internal storage directly.");
    }

    @Test
    void loadShouldNotAliasInputArrayDirectly() {
        SharedMatrix m = new SharedMatrix();
        double[][] a = new double[][]{{1,2},{3,4}};
        m.loadRowMajor(a);

        // mutate input after load
        a[0][0] = 777;

        double[][] out = m.readRowMajor();
        assertEquals(1.0, out[0][0], "loadRowMajor should copy input, not keep alias to caller array.");
    }
}
