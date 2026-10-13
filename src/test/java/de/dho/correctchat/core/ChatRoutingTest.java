package de.dho.correctchat.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatRoutingTest {
    @Test
    void recognizesNamespacedAndAliasedModCommands() {
        assertTrue(ChatRouting.commandMatchesMod("coreprotect:co inspect", "coreprotect"));
        assertTrue(ChatRouting.commandMatchesMod("alerts", "vulcan"));
        assertTrue(ChatRouting.commandMatchesMod("//set stone", "worldedit"));
        assertTrue(ChatRouting.commandMatchesMod("fawe:brush sphere", "worldedit"));
    }

    @Test
    void rejectsCommandsFromUnrelatedMods() {
        assertFalse(ChatRouting.commandMatchesMod("home", "worldedit"));
        assertFalse(ChatRouting.commandMatchesMod("alerts", "coreprotect"));
        assertFalse(ChatRouting.commandMatchesMod("vulcan logs", "worldedit"));
        assertFalse(ChatRouting.commandMatchesMod("anything", "  "));
    }

    @Test
    void usesLongerResponseWindowsForKnownInteractiveMods() {
        assertEquals(120_000L, ChatRouting.responseWindowMillis("worldedit"));
        assertEquals(30_000L, ChatRouting.responseWindowMillis("coreprotect"));
        assertEquals(30_000L, ChatRouting.responseWindowMillis("vulcan"));
        assertEquals(5_000L, ChatRouting.responseWindowMillis("other"));
    }
}
