package net.irisshaders.iris.pathways.scaling;

import net.irisshaders.iris.pathways.scaling.temporal.FsrNative;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class FsrRuntimeTest {
    @TempDir Path directory;
    @Test void missingOrModifiedRuntimeIsRejectedBeforeLoadingNativeCode() throws Exception {
        String previous=System.getProperty("nocturna.fsr.runtime");
        try {
            Path file=directory.resolve(FsrNative.RUNTIME_NAME);
            System.setProperty("nocturna.fsr.runtime",file.toString());
            assertThrows(IOException.class,FsrNative::runtime);
            Files.write(file,new byte[]{1,2,3,4});
            assertTrue(assertThrows(IOException.class,FsrNative::runtime).getMessage().contains("hash"));
        } finally {
            if(previous==null)System.clearProperty("nocturna.fsr.runtime");else System.setProperty("nocturna.fsr.runtime",previous);
        }
    }
}
