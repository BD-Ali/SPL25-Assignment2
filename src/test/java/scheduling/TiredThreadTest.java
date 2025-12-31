package scheduling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TiredThreadTest {

    @Test
    void newTask_null_throws() {
        TiredThread t = new TiredThread(0, 1.0);
        assertThrows(IllegalArgumentException.class, () -> t.newTask(null));
    }

    @Test
    void newTask_queueSingleSlot_secondOfferFailsIfNotConsumed() {
        // Do not start thread => it will not consume from the queue.
        TiredThread t = new TiredThread(0, 1.0);

        t.newTask(() -> {});
        assertThrows(IllegalStateException.class, () -> t.newTask(() -> {}),
                "Queue is capacity=1; second offer should fail when not consumed");
    }

    @Test
    void compareTo_tieBreaksByIdWhenSameFatigue() {
        TiredThread a = new TiredThread(1, 1.0);
        TiredThread b = new TiredThread(2, 1.0);

        // Both timeUsed=0 => equal fatigue => compare by id
        assertTrue(a.compareTo(b) < 0);
        assertTrue(b.compareTo(a) > 0);
    }
}
