package memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SharedVectorTest {

    @Test
    void ctor_nullVector_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new SharedVector(null, VectorOrientation.ROW_MAJOR));
    }

    @Test
    void ctor_nullOrientation_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new SharedVector(new double[]{1, 2}, null));
    }

    @Test
    void get_outOfBounds_throws() {
        SharedVector v = new SharedVector(new double[]{1, 2, 3}, VectorOrientation.ROW_MAJOR);
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> v.get(-1));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> v.get(3));
    }

    @Test
    void transpose_flipsOrientation() {
        SharedVector v = new SharedVector(new double[]{1, 2}, VectorOrientation.ROW_MAJOR);
        assertEquals(VectorOrientation.ROW_MAJOR, v.getOrientation());

        v.transpose();
        assertEquals(VectorOrientation.COLUMN_MAJOR, v.getOrientation());

        v.transpose();
        assertEquals(VectorOrientation.ROW_MAJOR, v.getOrientation());
    }

    @Test
    void add_sameLength_addsElementwise() {
        SharedVector a = new SharedVector(new double[]{1, 2, 3}, VectorOrientation.ROW_MAJOR);
        SharedVector b = new SharedVector(new double[]{10, 20, 30}, VectorOrientation.ROW_MAJOR);

        a.add(b);

        assertEquals(11, a.get(0), 1e-9);
        assertEquals(22, a.get(1), 1e-9);
        assertEquals(33, a.get(2), 1e-9);
    }

    @Test
    void add_lengthMismatch_throws() {
        SharedVector a = new SharedVector(new double[]{1, 2, 3}, VectorOrientation.ROW_MAJOR);
        SharedVector b = new SharedVector(new double[]{1, 2}, VectorOrientation.ROW_MAJOR);

        assertThrows(IllegalArgumentException.class, () -> a.add(b));
    }

    @Test
    void add_self_doublesElements() {
        SharedVector a = new SharedVector(new double[]{2, -3, 5}, VectorOrientation.ROW_MAJOR);

        a.add(a);

        assertEquals(4, a.get(0), 1e-9);
        assertEquals(-6, a.get(1), 1e-9);
        assertEquals(10, a.get(2), 1e-9);
    }

    @Test
    void negate_inPlace() {
        SharedVector a = new SharedVector(new double[]{2, -3, 5}, VectorOrientation.ROW_MAJOR);

        a.negate();

        assertEquals(-2, a.get(0), 1e-9);
        assertEquals(3, a.get(1), 1e-9);
        assertEquals(-5, a.get(2), 1e-9);
    }

    @Test
    void dot_sameLength_correct() {
        SharedVector a = new SharedVector(new double[]{1, 2, 3}, VectorOrientation.ROW_MAJOR);
        SharedVector b = new SharedVector(new double[]{4, 5, 6}, VectorOrientation.ROW_MAJOR);

        assertEquals(32.0, a.dot(b), 1e-9); // 1*4 + 2*5 + 3*6
    }

    @Test
    void dot_lengthMismatch_throws() {
        SharedVector a = new SharedVector(new double[]{1, 2, 3}, VectorOrientation.ROW_MAJOR);
        SharedVector b = new SharedVector(new double[]{1, 2}, VectorOrientation.ROW_MAJOR);

        assertThrows(IllegalArgumentException.class, () -> a.dot(b));
    }

    @Test
    void vecMatMul_smallExample_correct() {
        // row [1 2] * [[3 4 5],
        //              [6 7 8]]  => [15,18,21]
        SharedVector row = new SharedVector(new double[]{1, 2}, VectorOrientation.ROW_MAJOR);

        double[][] m = {
                {3, 4, 5},
                {6, 7, 8}
        };

        SharedMatrix matrix = new SharedMatrix();
        matrix.loadColumnMajor(m);

        row.vecMatMul(matrix);

        assertEquals(15, row.get(0), 1e-9);
        assertEquals(18, row.get(1), 1e-9);
        assertEquals(21, row.get(2), 1e-9);
    }

    @Test
    void vecMatMul_dimensionMismatch_throws() {
        SharedVector row = new SharedVector(new double[]{1, 2, 3}, VectorOrientation.ROW_MAJOR);

        double[][] m = {
                {1, 2},
                {3, 4}
        };

        SharedMatrix matrix = new SharedMatrix();
        matrix.loadColumnMajor(m);

        assertThrows(IllegalArgumentException.class, () -> row.vecMatMul(matrix));
    }
}
