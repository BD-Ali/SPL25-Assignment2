package spl.lae;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class MainTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void main_validInput_writesResultJson() throws Exception {
        Path dir = Files.createTempDirectory("lae-main-test");
        Path in = dir.resolve("input.json");
        Path out = dir.resolve("output.json");

        // input: ( [[1,2],[3,4]] + [[10,20],[30,40]] )
        String json = """
                {
                  "operator": "+",
                  "operands": [
                    [[1,2],[3,4]],
                    [[10,20],[30,40]]
                  ]
                }
                """;
        Files.writeString(in, json);

        Main.main(new String[]{"2", in.toString(), out.toString()});

        assertTrue(Files.exists(out), "output file must be created");
        JsonNode root = MAPPER.readTree(out.toFile());

        assertTrue(root.has("result"), "output must contain 'result'");
        JsonNode result = root.get("result");
        assertEquals(2, result.size());
        assertEquals(11.0, result.get(0).get(0).asDouble(), 1e-9);
        assertEquals(22.0, result.get(0).get(1).asDouble(), 1e-9);
        assertEquals(33.0, result.get(1).get(0).asDouble(), 1e-9);
        assertEquals(44.0, result.get(1).get(1).asDouble(), 1e-9);
    }

    @Test
    void main_invalidThreads_writesErrorJson() throws Exception {
        Path dir = Files.createTempDirectory("lae-main-test2");
        Path in = dir.resolve("input.json");
        Path out = dir.resolve("output.json");

        Files.writeString(in, "[[1,2],[3,4]]");

        Main.main(new String[]{"abc", in.toString(), out.toString()});

        assertTrue(Files.exists(out));
        JsonNode root = MAPPER.readTree(out.toFile());
        assertTrue(root.has("error"), "output must contain 'error' on failure");
    }

    @Test
    void main_dimensionMismatch_writesErrorJson() throws Exception {
        Path dir = Files.createTempDirectory("lae-main-test3");
        Path in = dir.resolve("input.json");
        Path out = dir.resolve("output.json");

        // Multiply mismatch: left is 1x3, right is 2x2 => invalid
        String json = """
                {
                  "operator": "*",
                  "operands": [
                    [[1,2,3]],
                    [[1,2],[3,4]]
                  ]
                }
                """;
        Files.writeString(in, json);

        Main.main(new String[]{"2", in.toString(), out.toString()});

        assertTrue(Files.exists(out));
        JsonNode root = MAPPER.readTree(out.toFile());
        assertTrue(root.has("error"), "must write error JSON on invalid computation");
    }
}
