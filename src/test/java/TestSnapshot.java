import com.badlogic.gdx.utils.Json;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;

public class TestSnapshot {
    /**
     * Used to test matching deep UI trees.
     */
    public static void assertMatchSnapshot(Class<?> test, Object data) throws IOException {
        String path = "snapshot_" + test.getName();
        File file = new File(path);
        FileWriter fileWriter = new FileWriter(file);
        try {
            Gson gson = new Gson();
            String newValue = gson.toJson(data);
            if (file.exists()) {
                String currentValue = new String(Files.readAllBytes(Paths.get(file.getPath())), Charset.defaultCharset());
                Assertions.assertEquals(newValue, currentValue);
            } else {
                fileWriter.write(newValue);
            }
        } catch (Throwable e) {
            fileWriter.close();
            throw e;
        }
    }
}
