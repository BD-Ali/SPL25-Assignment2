package memory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;



public class SharedVectorTest {

    @Test
    public void constructorRejectsNulls() {
        assertThrows(IllegalArgumentException.class, () -> new SharedVector(null, VectorOrientation.ROW_MAJOR));
        assertThrows(IllegalArgumentException.class, () -> new SharedVector(new double[]{1}, null));
    }

    @Test
    public void getLengthOrientationWork() {
        SharedVector v = new SharedVector(new double[]{1,2,3}, VectorOrientation.ROW_MAJOR);
        assertEquals(3, v.length());
        assertEquals(1.0, v.get(0));
        assertEquals(VectorOrientation.ROW_MAJOR, v.getOrientation());
    }

    @Test
    public void transposeFlipsMetadataOnly() {
        SharedVector v = new SharedVector(new double[]{1,2}, VectorOrientation.ROW_MAJOR);
        v.transpose();
        assertEquals(VectorOrientation.COLUMN_MAJOR, v.getOrientation());
        assertEquals(1.0, v.get(0));
        assertEquals(2.0, v.get(1));
    }

    @Test
    public void negateWorksInPlace() {
        SharedVector v = new SharedVector(new double[]{1,-2,3}, VectorOrientation.ROW_MAJOR);
        v.negate();
        assertEquals(-1.0, v.get(0));
        assertEquals(2.0, v.get(1));
        assertEquals(-3.0, v.get(2));
    }

    @Test
    public void addRejectsNullOrLengthMismatch() {
        SharedVector a = new SharedVector(new double[]{1,2}, VectorOrientation.ROW_MAJOR);
        assertThrows(IllegalArgumentException.class, () -> a.add(null));

        SharedVector b = new SharedVector(new double[]{1,2,3}, VectorOrientation.ROW_MAJOR);
        assertThrows(IllegalArgumentException.class, () -> a.add(b));
    }

    @Test
    public void addAddsElementwiseToReceiver() {
        SharedVector a = new SharedVector(new double[]{1,2}, VectorOrientation.ROW_MAJOR);
        SharedVector b = new SharedVector(new double[]{10,20}, VectorOrientation.ROW_MAJOR);
        a.add(b);
        assertEquals(11.0, a.get(0));
        assertEquals(22.0, a.get(1));
        // b unchanged
        assertEquals(10.0, b.get(0));
        assertEquals(20.0, b.get(1));
    }

    @Test
    public void dotRejectsNullOrLengthMismatch() {
        SharedVector a = new SharedVector(new double[]{1,2}, VectorOrientation.ROW_MAJOR);
        assertThrows(IllegalArgumentException.class, () -> a.dot(null));

        SharedVector b = new SharedVector(new double[]{1,2,3}, VectorOrientation.COLUMN_MAJOR);
        assertThrows(IllegalArgumentException.class, () -> a.dot(b));
    }

    @Test
    public void dotComputesCorrectly() {
        SharedVector a = new SharedVector(new double[]{1,2,3}, VectorOrientation.ROW_MAJOR);
        SharedVector b = new SharedVector(new double[]{4,5,6}, VectorOrientation.COLUMN_MAJOR);
        assertEquals(32.0, a.dot(b)); // 1*4 + 2*5 + 3*6
    }

    @Test
    void getIndexOutOfBoundsThrows() {
        SharedVector v = new SharedVector(new double[]{1,2,3}, VectorOrientation.ROW_MAJOR);
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> v.get(-1));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> v.get(3));
    }

    @Test
    void transposeTwiceRestoresOrientation() {
        SharedVector v = new SharedVector(new double[]{1,2}, VectorOrientation.ROW_MAJOR);
        v.transpose();
        assertEquals(VectorOrientation.COLUMN_MAJOR, v.getOrientation());
        v.transpose();
        assertEquals(VectorOrientation.ROW_MAJOR, v.getOrientation());
        // values unchanged
        assertEquals(1.0, v.get(0));
        assertEquals(2.0, v.get(1));
    }

    @Test
    void addWithSelfDoublesElements() {
        SharedVector v = new SharedVector(new double[]{1, -2, 3}, VectorOrientation.ROW_MAJOR);
        v.add(v);
        assertEquals(2.0, v.get(0));
        assertEquals(-4.0, v.get(1));
        assertEquals(6.0, v.get(2));
    }

    @Test
    void dotWithSelfComputesSumSquares() {
        SharedVector v = new SharedVector(new double[]{3,4}, VectorOrientation.ROW_MAJOR);
        assertEquals(25.0, v.dot(v)); // 3^2 + 4^2
    }

    @Test
    void operationsOnEmptyVectorWork() {
        SharedVector v = new SharedVector(new double[]{}, VectorOrientation.ROW_MAJOR);

        assertEquals(0, v.length());
        assertDoesNotThrow(v::negate);
        assertDoesNotThrow(v::transpose);
        assertEquals(0.0, v.dot(new SharedVector(new double[]{}, VectorOrientation.COLUMN_MAJOR)));
    }

    @Test
    void dotPropagatesNaNAndHandlesInfinity() {
        SharedVector a = new SharedVector(new double[]{Double.NaN, 1.0}, VectorOrientation.ROW_MAJOR);
        SharedVector b = new SharedVector(new double[]{2.0, 3.0}, VectorOrientation.COLUMN_MAJOR);
        assertTrue(Double.isNaN(a.dot(b)));

        SharedVector c = new SharedVector(new double[]{Double.POSITIVE_INFINITY}, VectorOrientation.ROW_MAJOR);
        SharedVector d = new SharedVector(new double[]{2.0}, VectorOrientation.COLUMN_MAJOR);
        assertTrue(Double.isInfinite(c.dot(d)));
    }

    @Test
    void addDoesNotModifyOtherVector() {
        SharedVector a = new SharedVector(new double[]{1,2}, VectorOrientation.ROW_MAJOR);
        SharedVector b = new SharedVector(new double[]{10,20}, VectorOrientation.ROW_MAJOR);

        a.add(b);

        assertArrayEquals(new double[]{11,22}, new double[]{a.get(0), a.get(1)});
        assertArrayEquals(new double[]{10,20}, new double[]{b.get(0), b.get(1)});
    }

    @Test
    void concurrentReadersAndWriterDoNotDeadlock() throws Exception {
        SharedVector v = new SharedVector(new double[]{1,2,3,4,5}, VectorOrientation.ROW_MAJOR);

        int readers = 4;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(readers + 1);

        // one writer: repeatedly negates
        Thread writer = new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < 2000; i++) v.negate();
            } catch (InterruptedException ignored) {
            } finally {
                done.countDown();
            }
        });

        // multiple readers: repeatedly read all indices
        Thread[] rs = new Thread[readers];
        for (int r = 0; r < readers; r++) {
            rs[r] = new Thread(() -> {
                try {
                    start.await();
                    for (int k = 0; k < 2000; k++) {
                        for (int i = 0; i < v.length(); i++) v.get(i);
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        writer.start();
        for (Thread t : rs) t.start();

        start.countDown();

        assertTrue(done.await(2, TimeUnit.SECONDS),
                "Likely deadlock or missing unlock in SharedVector locking logic.");
    }
    

}
