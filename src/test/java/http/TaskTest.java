package http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskTest {

    @Test
    void testConstructorAndGetters() {
        Task task = new Task("Titulo", "Descripcion", true);

        assertEquals("Titulo", task.getTitle());
        assertEquals("Descripcion", task.getDescription());
        assertTrue(task.isDone());
    }

    @Test
    void testConstructorAndGettersWithFalse() {
        Task task = new Task("Otro", "Probando", false);

        assertEquals("Otro", task.getTitle());
        assertEquals("Probando", task.getDescription());
        assertFalse(task.isDone());
    }
}
