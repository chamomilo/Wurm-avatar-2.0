package org.avatar2.core;

import org.junit.Test;
import java.io.*;
import java.nio.file.*;
import static org.junit.Assert.*;

public class MorphBankTest {
    @Test public void packagedBanksRejectCorruptionAndUnknownBindings() throws Exception {
        for (String name : new String[]{"male-bodybuilder", "female-bodybuilder"}) {
            byte[] data = Files.readAllBytes(Paths.get(System.getProperty("avatar2ProjectDir"), "assets/runtime/morph/" + name + ".avm"));
            MorphBank bank = MorphBank.read(new ByteArrayInputStream(data));
            assertEquals(name, bank.body); assertNull(bank.matching("unknown"));
            byte[] broken = data.clone(); broken[0] = 0;
            reject(broken);
            reject(java.util.Arrays.copyOf(data, data.length - 4));
            reject(java.util.Arrays.copyOf(data, data.length + 1));
        }
    }
    private static void reject(byte[] data) throws Exception {
        try { MorphBank.read(new ByteArrayInputStream(data)); fail("Corrupt morph bank accepted"); }
        catch (IOException expected) { }
    }
}
